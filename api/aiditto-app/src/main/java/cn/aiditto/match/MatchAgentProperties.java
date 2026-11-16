package cn.aiditto.match;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step 6 Agent 精排配置。默认关闭：不带 AI 分时求解器逐位等同 M2，
 * 保证 471 项既有测试与线上 MySQL 版零回归；灰度/仿真时显式开启。
 */
@ConfigurationProperties("aiditto.match.agent")
public record MatchAgentProperties(
        Boolean enabled,
        Integer topPairs,
        Integer minTotal,
        Boolean simulateEnabled) {

    public MatchAgentProperties {
        enabled = enabled == null ? Boolean.FALSE : enabled;
        topPairs = topPairs == null || topPairs <= 0 ? 15 : Math.min(topPairs, 100);
        minTotal = minTotal == null || minTotal < 0 ? 72 : Math.min(minTotal, 100);
        simulateEnabled = simulateEnabled == null ? Boolean.TRUE : simulateEnabled;
    }
}
