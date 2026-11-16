package cn.aiditto.recall;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 反馈先验的纯逻辑：中性兜底、范围约束、空集与异常安全（不连库）。 */
class FeedbackPriorTest {

    private final FeedbackPrior prior = new FeedbackPrior((JdbcTemplate) null);

    @Test
    void emptyCandidatesReturnEmptyMap() {
        assertThat(prior.load(null)).isEmpty();
        assertThat(prior.load(List.of())).isEmpty();
    }

    @Test
    void missingPriorIsNeutralNotZero() {
        // 冷启动绝不能被 0 分压死：新人/无反馈候选拿到 0.5 中性先验
        assertThat(prior.neutralIfAbsent(Map.of(), 42L)).isEqualTo(0.5);
        assertThat(prior.neutralIfAbsent(Map.of(7L, 0.9), 42L)).isEqualTo(0.5);
        assertThat(prior.neutralIfAbsent(Map.of(7L, 0.9), 7L)).isEqualTo(0.9);
    }

    @Test
    void jdbcFailureIsContainedNotPropagated() {
        // null JdbcTemplate 触发 NPE -> 被 catch 成空 Map，召回链路不因反馈表异常中断
        assertThat(prior.load(List.of(1L, 2L))).isEmpty();
    }

    @Test
    void neutralConstantIsHalf() {
        assertThat(FeedbackPrior.NEUTRAL).isEqualTo(0.5);
    }

    @Test
    void batchConfigNeverExceedsGatewayTextLimit() {
        // 回归防护：text-embedding-v3 单请求 ≤10 条文本；每用户 2 条，
        // 因此 usersPerGroup 必须 ≤ maxItems/2，否则批量 400 静默回退逐条（8 分钟 vs 1 分钟）
        for (int maxItems : new int[]{10, 16, 2, 1}) {
            for (int configuredUsers : new int[]{1, 5, 16, 32}) {
                int users = RecallProperties.usersPerBatch(configuredUsers, maxItems);
                assertThat(users * 2)
                        .as("maxItems=%d configuredUsers=%d -> users=%d", maxItems, configuredUsers, users)
                        .isLessThanOrEqualTo(Math.max(2, maxItems));
                assertThat(users).isGreaterThanOrEqualTo(1);
            }
        }
        assertThat(RecallProperties.usersPerBatch(16, 10)).isEqualTo(5);
        assertThat(RecallProperties.usersPerBatch(1, 10)).isEqualTo(1);
    }
}
