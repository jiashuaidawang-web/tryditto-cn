package cn.aiditto.recall;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任意 OpenAI 兼容 /embeddings 端点（vveai / DashScope text-embedding-v3 / 智谱 embedding-3 等）。
 * 配置齐备即为正式实现，不需要改业务代码；失败直接抛，由 VectorSyncService 记 failed 并留待补偿。
 *
 * 真实中转站兼容要点（Step 5 收尾补全，避免"配了 key 却跑不通"）：
 *  1. baseUrl 归一化：支持 https://host 、https://host/v1 、https://host/v1/embeddings 三种写法，
 *     统一收敛到 /v1/embeddings；绝大多数 OpenAI 兼容网关都要求带 /v1。
 *  2. dimensions 参数可选：不少模型（bge / 部分聚合网关）不支持该参数，强传会 400；
 *     sendDimensions=false 时不发送该字段，由模型返回原生维度。
 *  3. 维度校验给出实际值与期望值，便于配置纠错；dimension<=0 时接受模型返回的任意维度（探测模式）。
 *  4. 错误带 HTTP 状态与响应体片段，token 过期/欠费/模型不存在能一眼定位，不吞异常。
 */
public final class OpenAiCompatEmbeddingService implements EmbeddingService {

    private final String version;
    private final String url;
    private final String apiKey;
    private final String model;
    private final int expectedDimension;
    private final boolean sendDimensions;
    private final HttpClient http;
    private final long timeoutMs;

    /** 兼容旧构造（旧测试与调用方零改动）：默认发送 dimensions。 */
    public OpenAiCompatEmbeddingService(String version, String baseUrl, String apiKey, String model,
                                        int dimension, long timeoutMs) {
        this(version, baseUrl, apiKey, model, dimension, true, timeoutMs);
    }

    public OpenAiCompatEmbeddingService(String version, String baseUrl, String apiKey, String model,
                                        int dimension, boolean sendDimensions, long timeoutMs) {
        this.version = version;
        this.url = normalizeUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.expectedDimension = dimension;
        this.sendDimensions = sendDimensions;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .build();
    }

    /**
     * 把用户给的 baseUrl 收敛成完整的 /v1/embeddings 端点。
     *   https://api.vveai.com              -> https://api.vveai.com/v1/embeddings
     *   https://api.vveai.com/v1           -> https://api.vveai.com/v1/embeddings
     *   https://api.vveai.com/v1/embeddings -> 原样
     * 已含其他自定义路径（如 /openai/v1）时只在尾部补 /embeddings，不破坏网关前缀。
     */
    static String normalizeUrl(String baseUrl) {
        String s = baseUrl == null ? "" : baseUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "/v1/embeddings";
        if (s.endsWith("/embeddings")) return s;
        if (s.endsWith("/v1")) return s + "/embeddings";
        if (s.contains("/v1/")) return s + "/embeddings";
        return s + "/v1/embeddings";
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public int dimension() {
        return expectedDimension;
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text == null ? "" : text);
        if (sendDimensions && expectedDimension > 0) body.put("dimensions", expectedDimension);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("embedding http " + response.statusCode()
                        + " body=" + clip(response.body())
                        + " (model=" + model + ", url=" + url + ")");
            }
            JsonNode root = Json.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || data.size() == 0) {
                throw new IllegalStateException("embedding empty data body=" + clip(response.body()));
            }
            JsonNode vec = data.get(0).get("embedding");
            if (vec == null || vec.size() == 0) {
                throw new IllegalStateException("embedding missing vector body=" + clip(response.body()));
            }
            // 有些网关返回 base64 字符串而非浮点数组
            if (vec.isTextual()) {
                throw new IllegalStateException("embedding returned base64/string vector; "
                        + "set encoding_format=float via extra-body (model=" + model + ")");
            }
            int actual = vec.size();
            if (expectedDimension > 0 && actual != expectedDimension) {
                throw new IllegalStateException("embedding dimension mismatch: expected "
                        + expectedDimension + " but model " + model + " returned " + actual
                        + "; set EMBEDDING_DIM=" + actual + " or EMBEDDING_SEND_DIMENSIONS=false");
            }
            float[] out = new float[actual];
            for (int i = 0; i < actual; i++) out[i] = (float) vec.get(i).asDouble();
            return normalize(out);
        } catch (IOException e) {
            throw new IllegalStateException("embedding io: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /** 一次请求批量取向量，减少真实 key 下的调用次数（VectorSyncService 批量同步使用）。 */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        if (sendDimensions && expectedDimension > 0) body.put("dimensions", expectedDimension);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("embedding batch http " + response.statusCode()
                        + " body=" + clip(response.body()));
            }
            JsonNode data = Json.readTree(response.body()).get("data");
            if (data == null || data.size() != texts.size()) {
                throw new IllegalStateException("embedding batch size mismatch");
            }
            java.util.List<float[]> out = new java.util.ArrayList<>(texts.size());
            // data 可能乱序，按 index 归位
            float[][] sorted = new float[texts.size()][];
            for (JsonNode item : data) {
                int idx = item.path("index").asInt(-1);
                JsonNode vec = item.get("embedding");
                if (idx < 0 || idx >= texts.size() || vec == null) {
                    throw new IllegalStateException("embedding batch malformed item");
                }
                float[] v = new float[vec.size()];
                for (int i = 0; i < v.length; i++) v[i] = (float) vec.get(i).asDouble();
                if (expectedDimension > 0 && v.length != expectedDimension) {
                    throw new IllegalStateException("embedding dimension mismatch: expected "
                            + expectedDimension + " got " + v.length);
                }
                sorted[idx] = normalize(v);
            }
            for (float[] v : sorted) {
                if (v == null) throw new IllegalStateException("embedding batch missing index");
                out.add(v);
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("embedding batch io: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding interrupted", e);
        }
    }

    private static float[] normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm <= 0) return v;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    private static String clip(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 220 ? s.substring(0, 220) : s;
    }
}
