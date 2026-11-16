package cn.aiditto.match;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step 7 AI 文案配置。默认关闭：不带 AI 文案时逐位等同 M2 的 TemplateCopy，
 * 保证既有 485 项测试与线上 MySQL 版零回归；灰度/仿真显式开启。
 */
@ConfigurationProperties("aiditto.match.copy")
public record MatchCopyProperties(
        Boolean enabled,
        Integer maxAttempts,
        Integer maxTokens,
        Double temperature,
        String promptVersion) {

    public MatchCopyProperties {
        enabled = enabled == null ? Boolean.FALSE : enabled;
        // 文案子图级重试（网关内部另有主备/重试）；2 次即可覆盖"首次非法 JSON、二次修正"
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 2 : Math.min(maxAttempts, 5);
        maxTokens = maxTokens == null || maxTokens < 256 ? 1600 : Math.min(maxTokens, 4096);
        temperature = temperature == null || temperature < 0 ? 0.6 : Math.min(temperature, 1.0);
        promptVersion = promptVersion == null || promptVersion.isBlank() ? "match-copy-v1" : promptVersion.strip();
    }
}
