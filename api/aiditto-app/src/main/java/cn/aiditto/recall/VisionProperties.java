package cn.aiditto.recall;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 视觉（审美）模型配置。独立于文本 embedding，避免动 RecallProperties 的构造签名。
 * 密钥只从环境变量注入（VISION_API_KEY），不写默认值、不落盘。
 */
@ConfigurationProperties("aiditto.vision")
public record VisionProperties(
        Boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        String version,
        Long timeoutMs) {

    public VisionProperties {
        enabled = enabled == null ? Boolean.FALSE : enabled;
        baseUrl = baseUrl == null ? "" : baseUrl.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "doubao-embedding-vision-251215" : model.strip();
        version = version == null || version.isBlank()
                ? VisionEmbeddingService.DEFAULT_VERSION : version.strip();
        timeoutMs = timeoutMs == null || timeoutMs <= 0 ? 30_000L : timeoutMs;
    }

    public boolean live() {
        return Boolean.TRUE.equals(enabled) && !baseUrl.isBlank() && !apiKey.isBlank();
    }
}
