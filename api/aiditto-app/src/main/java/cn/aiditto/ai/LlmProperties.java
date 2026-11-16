package cn.aiditto.ai;

import cn.aiditto.infra.json.Json;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Map;

/**
 * 大模型配置。密钥只来自环境变量（服务器 /opt/aiditto/.env 注入），不写默认值、不落盘、不打印。
 * mode=mock 时主备都是确定性 MockLlmModel，本机无密钥也能跑全链路。
 *
 * 备用出厂模型：DeepSeek-V4.1-Flash-1017（模型名固定 deepseek-flash，OpenAI 兼容端点）。
 */
@ConfigurationProperties("aiditto.llm")
public record LlmProperties(
        String mode,
        String primaryBaseUrl,
        String primaryApiKey,
        String primaryModel,
        String fallbackBaseUrl,
        String fallbackApiKey,
        String fallbackModel,
        int maxAttempts,
        long timeoutMs,
        long retryDelayMs,
        int circuitThreshold,
        String primaryExtraBody,
        String fallbackExtraBody) {

    /** 关掉思考：DeepSeek / LongCat 通用。未显式配置 extra-body 时对 deepseek-* 自动注入。 */
    private static final String DISABLE_THINKING = "{\"thinking\":{\"type\":\"disabled\"}}";

    public LlmProperties {
        mode = (mode == null || mode.isBlank()) ? "mock" : mode.strip();
        primaryBaseUrl = nullToEmpty(primaryBaseUrl);
        primaryApiKey = nullToEmpty(primaryApiKey);
        primaryModel = blankTo(primaryModel, "LongCat-2.5");
        fallbackBaseUrl = nullToEmpty(fallbackBaseUrl);
        fallbackApiKey = nullToEmpty(fallbackApiKey);
        fallbackModel = blankTo(fallbackModel, "deepseek-flash");
        maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        timeoutMs = timeoutMs <= 0 ? 30_000L : timeoutMs;
        retryDelayMs = retryDelayMs < 0 ? 2_000L : retryDelayMs;
        circuitThreshold = circuitThreshold <= 0 ? 5 : circuitThreshold;
        primaryExtraBody = nullToEmpty(primaryExtraBody);
        fallbackExtraBody = nullToEmpty(fallbackExtraBody);
        // DeepSeek 默认开思考（返回 reasoning_content）。显式配置优先级更高；
        // 换成非 DeepSeek 模型时不会误注入这个参数。
        if (fallbackExtraBody.isBlank() && isDeepSeek(fallbackModel)) {
            fallbackExtraBody = DISABLE_THINKING;
        }
    }

    public boolean live() {
        return "live".equalsIgnoreCase(mode);
    }

    /** 备用整条关闭：密钥为空时不走「切备用」这条边（技术方案 §8.3）。 */
    public boolean fallbackAvailable() {
        return !fallbackBaseUrl.isBlank() && !fallbackApiKey.isBlank();
    }

    /** 模型级私有请求体参数；畸形配置直接 fail-fast，避免悄悄把错参数发给模型。 */
    public Map<String, Object> primaryExtraBodyMap() {
        return parseExtraBody(primaryExtraBody, "primary");
    }

    public Map<String, Object> fallbackExtraBodyMap() {
        return parseExtraBody(fallbackExtraBody, "fallback");
    }

    private static Map<String, Object> parseExtraBody(String json, String which) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = Json.readMap(json);
            return parsed == null ? Map.of() : Map.copyOf(parsed);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "aiditto.llm." + which + "-extra-body 不是合法 JSON 对象: " + e.getMessage());
        }
    }

    private static boolean isDeepSeek(String model) {
        return model != null && model.toLowerCase(Locale.ROOT).startsWith("deepseek");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.strip();
    }
}
