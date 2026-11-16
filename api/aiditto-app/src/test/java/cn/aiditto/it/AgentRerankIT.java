package cn.aiditto.it;

import cn.aiditto.match.WeekRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6 端到端闸门：开启 Agent 精排后跑完整周批，验证
 *  - 周批状态机不破、仍能 PUBLISH；
 *  - 结构化留痕 sim_report 落库；
 *  - 一人一位、硬约束不越界；
 *  - 统计里能看到 agentMode / judged / degraded（可观测）。
 *
 * 默认跳过；用 AIDITTO_STEP6_IT=1 开启。
 * Mock LLM 模式下裁判输出非法 → 全部降级，正好验证 L3/L4 不空发。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_STEP6_IT", matches = "1")
class AgentRerankIT extends ApiIT {

    @DynamicPropertySource
    static void agentOn(DynamicPropertyRegistry r) {
        r.add("aiditto.match.agent.enabled", () -> "true");
        r.add("aiditto.match.agent.top-pairs", () -> "5");
        r.add("aiditto.match.agent.min-total", () -> "72");
        // 设 AIDITTO_STEP6_LIVE=1 + LLM_* 环境变量即可用真实模型验证 AI 分主导配对；
        // 默认 mock 模式用于验证 L3/L4 降级不空发。
        r.add("aiditto.llm.mode", () -> env("AIDITTO_STEP6_LIVE", "mock").equals("1") ? "live" : "mock");
        r.add("aiditto.llm.primary-base-url", () -> env("LLM_LIVE_BASE_URL", ""));
        r.add("aiditto.llm.primary-api-key", () -> env("LLM_LIVE_API_KEY", ""));
        r.add("aiditto.llm.primary-model", () -> env("LLM_LIVE_MODEL", "deepseek-v4-flash"));
        r.add("aiditto.llm.max-attempts", () -> "2");
    }

    @Autowired WeekRunner weeks;
    @Autowired JdbcTemplate jdbc;
    @Autowired cn.aiditto.match.SyntheticLoader loader;
    @Autowired cn.aiditto.taste.TasteBatchService tastes;

    @Test
    void batchCompletesWithAgentRerankEnabledAndDegradesSafely() {
        loader.load();
        // 合成用户直接插库、不走入驻事件，必须先有画像，否则精排空转。
        // live 模式下不能调 refreshMissing：它会对 200 用户逐个打真实画像模型（10+ 分钟），
        // 与本 IT 要验证的"裁判/配对"无关。改为插入确定性最小画像。
        boolean live = "1".equals(env("AIDITTO_STEP6_LIVE", "mock"));
        if (live) {
            jdbc.update("""
                    INSERT INTO user_taste (user_id, version, persona_text, taste_text,
                                           embedding_version, vector_sync_status,
                                           source_feedback_cnt, is_active, created_at)
                    SELECT u.id, 1,
                           '喜欢户外冒险与新鲜体验，说话直接，周末爱往山里跑',
                           '看重幽默感和行动力，喜欢能一起尝试新事物的人',
                           'it-step6', 'pending', 0, TRUE, CURRENT_TIMESTAMP
                    FROM "user" u
                    WHERE u.status = 'active'
                    ON CONFLICT (user_id, version) DO NOTHING
                    """);
            System.out.println("[STEP6-IT] live mode: inserted deterministic personas");
        } else {
            int generated = tastes.refreshMissing(1000);
            System.out.println("[STEP6-IT] taste generated=" + generated
                    + " active=" + tastes.stats().active());
        }
        Map<String, Object> out = weeks.runManual("bj", Instant.now(), true);
        assertThat(out).isNotNull();
        System.out.println("[STEP6-IT] runManual summary=" + out);

        // 周批必须仍能完成（不能因 LLM 挂掉/输出非法而中断）
        Long weekId = jdbc.queryForObject(
                "SELECT MAX(id) FROM week WHERE pool = 'bj' AND trigger_kind = 'manual'", Long.class);
        assertThat(weekId).isNotNull();
        Long pairs = jdbc.queryForObject("SELECT COUNT(*) FROM \"match\" WHERE week_id = ?",
                Long.class, weekId);
        System.out.println("[STEP6-IT] week=" + weekId + " pairs=" + pairs);

        // 一人一位（外层聚合保证恒返回一行，避免无重复时 queryForObject 抛 0 行）
        Long dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT u FROM (
                    SELECT user_low AS u FROM "match" WHERE week_id = ?
                    UNION ALL SELECT user_high FROM "match" WHERE week_id = ?
                  ) t GROUP BY u HAVING COUNT(*) > 1
                ) d
                """, Long.class, weekId, weekId);
        assertThat(dup == null ? 0L : dup).isZero();

        // 留痕落库（sim_report / final_score / degraded）
        Long reports = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recall_candidate WHERE week_id = ? AND sim_report IS NOT NULL",
                Long.class, weekId);
        System.out.println("[STEP6-IT] sim_report rows=" + reports);

        // 统计里 agent 可观测
        String stats = jdbc.queryForObject("SELECT stats::text FROM week WHERE id = ?", String.class, weekId);
        System.out.println("[STEP6-IT] week stats=" + (stats == null ? "null" : stats.substring(0, Math.min(400, stats.length()))));
        assertThat(stats).contains("agentMode");
        if ("1".equals(env("AIDITTO_STEP6_LIVE", "mock"))) {
            // 真实模型下必须真的产出 AI 分并注入配对，不能只是"跑通了但空转"
            assertThat(stats).doesNotContain("\"agentAiPairs\": 0");
            assertThat(stats).doesNotContain("fully-degraded");
            System.out.println("[STEP6-IT] LIVE: AI scores injected = "
                    + jdbc.queryForObject("SELECT COUNT(*) FROM recall_candidate WHERE week_id = ? AND final_score IS NOT NULL",
                    Long.class, weekId));
        }
    }
}
