package cn.aiditto.ai;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容端点（LongCat-2.5 / DeepSeek-V4.1-Flash / Qwen DashScope）的 JDK HttpClient 实现。
 * 对应技术方案 §8.4 写明的退路：自己实现 ChatModel，不引入 LangChain4j 的 Spring starter，
 * 换模型只改配置。密钥只在请求头内存中使用，不写日志。
 *
 * 各家私有参数（thinking / enable_thinking / reasoning_effort / response_format）差异通过
 * extraBody 按模型实例注入，而不是散落在每个调用点；显式配置的优先级高于请求级 disableThinking。
 */
public final class OpenAiCompatModel implements LlmModel {

    private final HttpClient http;
    private final String url;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;
    private final Map<String, Object> extraBody;

    public OpenAiCompatModel(String baseUrl, String apiKey, String model, long timeoutMs) {
        this(baseUrl, apiKey, model, timeoutMs, Map.of());
    }

    public OpenAiCompatModel(String baseUrl, String apiKey, String model, long timeoutMs,
                             Map<String, Object> extraBody) {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .build();
        this.url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.extraBody = extraBody == null ? Map.of() : Map.copyOf(extraBody);
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public Raw call(LlmRequest request) throws LlmException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.system()),
                Map.of("role", "user", "content", request.user())));
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        if (request.jsonMode()) body.put("response_format", Map.of("type", "json_object"));
        // LongCat-2.5 推理模型：文案类调用关掉思考（技术方案 §8.3 实测约 5s）
        if (request.disableThinking()) body.put("thinking", Map.of("type", "disabled"));
        // 模型级私有参数放最后：DeepSeek 默认开思考会返回 reasoning_content，
        // 极端情况下 max_tokens 被思考耗尽导致 content 为空，必须由模型层强制兜底。
        if (!extraBody.isEmpty()) body.putAll(extraBody);

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new LlmException(LlmException.Kind.TIMEOUT, "timeout after " + timeoutMs + "ms");
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.SERVER_ERROR, "io: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.TIMEOUT, "interrupted");
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new LlmException(LlmException.Kind.RATE_LIMIT, "http 429",
                    response.headers().firstValue("Retry-After").orElse(null));
        }
        if (status == 400 || status == 401 || status == 403) {
            throw new LlmException(LlmException.Kind.AUTH, "http " + status);
        }
        if (status >= 500) throw new LlmException(LlmException.Kind.SERVER_ERROR, "http " + status);
        if (status >= 400) throw new LlmException(LlmException.Kind.BAD_RESPONSE, "http " + status);

        JsonNode root;
        try {
            root = Json.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || choices.size() == 0) throw new IllegalStateException("no choices");
            JsonNode message = choices.get(0).get("message");
            if (message == null) throw new IllegalStateException("no message");
            JsonNode contentNode = message.get("content");
            String content = contentNode == null || contentNode.isNull() ? "" : contentNode.asString();
            JsonNode finish = choices.get(0).get("finish_reason");
            String finishReason = finish == null || finish.isNull() ? "stop" : finish.asString();
            if (content.isBlank() && "length".equals(finishReason)) {
                throw new LlmException(LlmException.Kind.EMPTY_CONTENT, "finish_reason=length");
            }
            JsonNode usage = root.get("usage");
            if (content.isBlank()) return new Raw(null, finishReason, tokens(usage, "prompt_tokens"), 0);
            return new Raw(content, finishReason,
                    tokens(usage, "prompt_tokens"), tokens(usage, "completion_tokens"));
        } catch (LlmException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "bad response: " + e.getMessage());
        }
    }

    private static Integer tokens(JsonNode node, String key) {
        if (node == null) return null;
        JsonNode v = node.get(key);
        return v != null && v.isNumber() ? v.intValue() : null;
    }
}
