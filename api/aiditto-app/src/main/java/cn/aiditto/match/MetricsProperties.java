package cn.aiditto.match;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实效果埋点配置（创始人需求：每次策略/学习方案改动都能归因到指标涨跌）。
 * configVersion 是策略版本标记：每次改召回/精排/学习方案，改这个版本号（如 v4-学习方案甲），
 * week_metric 快照会带上它，看板按版本切分即可回答「接受率从 X% 提升到 Y%」。
 */
@ConfigurationProperties("aiditto.metrics")
public record MetricsProperties(
        Boolean enabled,
        String configVersion) {

    public MetricsProperties {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        configVersion = configVersion == null || configVersion.isBlank()
                ? "v3-baseline" : configVersion.strip();
    }
}
