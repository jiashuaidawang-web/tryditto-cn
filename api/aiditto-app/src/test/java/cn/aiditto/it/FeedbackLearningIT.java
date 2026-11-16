package cn.aiditto.it;

import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.MockLlmModel;
import cn.aiditto.match.JudgeCalibrationService;
import cn.aiditto.match.MatchDb;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.match.WeekRunner;
import cn.aiditto.taste.FeedbackLearningService;
import cn.aiditto.taste.TasteBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 8 真 PG 闸门：反馈 → 画像新版本（单次 1 维 ≤0.15）→ 配对永久记忆 → 裁判校准。
 * 同时验证可重入、每周≤1 次、模型全挂时闭环不空转。
 * 默认跳过；AIDITTO_STEP8_IT=1 开启。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_STEP8_IT", matches = "1")
class FeedbackLearningIT extends ApiIT {

    @DynamicPropertySource
    static void learningOn(DynamicPropertyRegistry r) {
        r.add("aiditto.learning.enabled", () -> "true");
        r.add("aiditto.learning.min-calibration-samples", () -> "30");
        r.add("aiditto.llm.mode", () -> "mock");
        r.add("aiditto.match.agent.enabled", () -> "false");
        r.add("aiditto.match.copy.enabled", () -> "false");
    }

    @Autowired WeekRunner weeks;
    @Autowired JdbcTemplate jdbc;
    @Autowired SyntheticLoader loader;
    @Autowired TasteBatchService tastes;
    @Autowired FeedbackLearningService learning;
    @Autowired JudgeCalibrationService calibration;
    @Autowired MatchDb db;
    @Autowired @Qualifier("primaryLlmModel") LlmModel primary;

    @Test
    void feedbackRewritesTasteWithOneBoundedWeightAndPersistsPairMemory() {
        // 模型输出不可用的确定性降级：MockLlmModel 默认返回 {"mock":true}，
        // TasteRewriter 必须走规则改写而不是空转。
        ((MockLlmModel) primary).clearScripts();
        loader.load();
        int generated = tastes.refreshMissing(1000);
        assertThat(generated).isGreaterThan(0);
        weeks.runManual("bj", Instant.now(), true);

        // 演示/合成账号可能没有画像（直接插库、不走入驻事件），先补齐再选对，
        // 保证本 IT 验的是「反馈学习」而不是「画像缺失」。
        jdbc.update("""
                INSERT INTO user_taste (user_id, version, persona_text, taste_text, embedding_version,
                                        vector_sync_status, source_feedback_cnt, is_active, created_at,
                                        input_hash, weights, learned_feedback_cnt)
                SELECT x.u, 1, '喜欢户外与新鲜体验', '看重幽默感和行动力',
                       'it-step8', 'pending', 0, TRUE, CURRENT_TIMESTAMP,
                       repeat('0', 64), '{}'::JSONB, 0
                FROM (SELECT user_low AS u FROM "match" UNION SELECT user_high FROM "match") x
                WHERE NOT EXISTS (SELECT 1 FROM user_taste t WHERE t.user_id = x.u AND t.is_active)
                ON CONFLICT DO NOTHING
                """);
        Map<String, Object> pair = jdbc.queryForMap("""
                SELECT m.id AS match_id, m.week_id, m.user_low, m.user_high
                FROM "match" m
                JOIN user_taste tl ON tl.user_id = m.user_low AND tl.is_active
                JOIN user_taste th ON th.user_id = m.user_high AND th.is_active
                ORDER BY m.id LIMIT 1
                """);
        long matchId = ((Number) pair.get("match_id")).longValue();
        long weekId = ((Number) pair.get("week_id")).longValue();
        long low = ((Number) pair.get("user_low")).longValue();
        long high = ((Number) pair.get("user_high")).longValue();

        // 双方都拒绝 → 双向 forbidden（A04），同时各自产生一条反馈
        insertFeedback(matchId, low, 2, "no");
        insertFeedback(matchId, high, 2, "no");
        String beforeLow = weightsOf(low);
        int versionBefore = activeVersion(low);

        Map<String, String> tags = Map.of(low + ":" + matchId, "bad:height_sensitivity",
                high + ":" + matchId, "good:humor");
        FeedbackLearningService.Stats stats = learning.scanAndRewrite(10, tags);
        System.out.println("[STEP8-IT] stats=" + stats);
        assertThat(stats.rewritten()).isGreaterThanOrEqualTo(2);
        assertThat(stats.forbiddenPairs()).isGreaterThanOrEqualTo(1);

        // 新版本只追加、旧版失效、反馈计数递增
        assertThat(activeVersion(low)).isEqualTo(versionBefore + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_taste WHERE user_id=? AND is_active",
                Integer.class, low)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT learned_feedback_cnt FROM user_taste WHERE user_id=? AND is_active",
                Integer.class, low)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT source_feedback_cnt FROM user_taste WHERE user_id=? AND is_active",
                Integer.class, low)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT vector_sync_status FROM user_taste WHERE user_id=? AND is_active",
                String.class, low)).isEqualTo("pending");

        // 单次只改 1 维、幅度 ≤0.15
        cn.aiditto.domain.taste.TasteWeights before = cn.aiditto.domain.taste.TasteWeights.of(jsonMap(beforeLow));
        cn.aiditto.domain.taste.TasteWeights after = cn.aiditto.domain.taste.TasteWeights.of(jsonMap(weightsOf(low)));
        assertThat(after.maxDeltaFrom(before)).isLessThanOrEqualTo(0.15 + 1e-6);
        int changed = 0;
        for (String d : cn.aiditto.domain.taste.TasteWeights.DIMS) {
            if (Math.abs(after.get(d) - before.get(d)) > 1e-6) changed++;
        }
        assertThat(changed).isEqualTo(1);
        assertThat(after.get("height_sensitivity")).isLessThan(before.get("height_sensitivity"));

        // 留痕可审计、可回滚
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM taste_log WHERE user_id=? AND changed_dim='taste_rewrite'
                  AND trigger_feedback=? AND diff_text LIKE 'dim=%'
                """, Integer.class, low, matchId)).isEqualTo(1);

        // 配对永久记忆：双向 forbidden，且硬过滤能查到
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_pair_memory WHERE forbidden",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(db.forbiddenPairs()).anyMatch(p -> p[0] == Math.min(low, high) && p[1] == Math.max(low, high));

        // 可重入：同一条反馈不会被重复学习
        FeedbackLearningService.Stats again = learning.scanAndRewrite(10, tags);
        assertThat(again.scanned()).isZero();
        assertThat(activeVersion(low)).isEqualTo(versionBefore + 1);

        // 每周≤1 次：同一用户再造一条反馈，必须被周闸门拦下
        long match2 = ((Number) jdbc.queryForObject("""
                SELECT m.id FROM "match" m WHERE m.id <> ? ORDER BY m.id LIMIT 1
                """, Long.class, matchId)).longValue();
        insertFeedback(match2, low, 5, "yes");
        FeedbackLearningService.Stats weekly = learning.scanAndRewrite(10,
                Map.of(low + ":" + match2, "good:humor"));
        assertThat(weekly.weeklyLimit()).isGreaterThanOrEqualTo(1);
        assertThat(activeVersion(low)).isEqualTo(versionBefore + 1);

        // 裁判校准：<30 条样本不启用，硬夹在 ±10
        JudgeCalibrationService.Calibration cal = calibration.recompute();
        assertThat(cal.active()).isFalse();
        assertThat(cal.bias()).isZero();
        assertThat(calibration.apply(74)).isEqualTo(74);
        System.out.println("[STEP8-IT] calibration=" + cal + " forbidden=" + db.forbiddenPairs().size());
    }

    private void insertFeedback(long matchId, long userId, int rating, String again) {
        jdbc.update("""
                INSERT INTO feedback (match_id, user_id, met, rating, again, felt_safe, created_at)
                VALUES (?,?,'met',?,?,TRUE,?)
                ON CONFLICT (match_id, user_id) DO UPDATE SET rating = EXCLUDED.rating, again = EXCLUDED.again
                """, matchId, userId, rating, again, Timestamp.from(Instant.now()));
    }

    private int activeVersion(long userId) {
        return jdbc.queryForObject("SELECT version FROM user_taste WHERE user_id=? AND is_active",
                Integer.class, userId);
    }

    private String weightsOf(long userId) {
        return jdbc.queryForObject("SELECT weights::text FROM user_taste WHERE user_id=? AND is_active",
                String.class, userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String json) {
        return cn.aiditto.infra.json.Json.readMap(json);
    }
}
