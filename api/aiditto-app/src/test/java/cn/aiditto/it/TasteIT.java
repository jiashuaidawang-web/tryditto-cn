package cn.aiditto.it;

import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.onboarding.AnswerStore;
import cn.aiditto.taste.ProfileWrittenEvent;
import cn.aiditto.taste.TasteBatchService;
import cn.aiditto.taste.TasteExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4 闸门：200 合成用户全量生成、确定性幂等、版本化、零 PII 泄漏、异步事件链路。
 * 走真实 PG16（TEST_DB_URL），不是内存桩。
 */
class TasteIT extends ApiIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired SyntheticLoader synthetic;
    @Autowired AnswerStore answers;
    @Autowired TasteBatchService batch;
    @Autowired TasteExtractor extractor;
    @Autowired ApplicationEventPublisher events;

    @Test
    void allSyntheticUsersGetDeterministicDesensitizedVersionedTaste() {
        jdbc.update("DELETE FROM taste_log");
        jdbc.update("DELETE FROM user_taste");

        Map<String, Integer> load = synthetic.load();
        assertThat(load.get("loaded") + load.get("skipped")).isEqualTo(200);

        long syntheticUsers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"user\" WHERE cohort IN ('synthetic','demo')", Long.class);
        assertThat(syntheticUsers).isGreaterThanOrEqualTo(200);

        // 批量补齐：所有合成用户都应有 active 画像
        batch.refreshMissing(1000);
        long missing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM "user" u JOIN profile p ON p.user_id = u.id
                WHERE u.cohort IN ('synthetic','demo')
                  AND NOT EXISTS (SELECT 1 FROM user_taste t WHERE t.user_id = u.id AND t.is_active)
                """, Long.class);
        assertThat(missing).isZero();

        TasteBatchService.Stats stats = batch.stats();
        long withAnswers = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT a.user_id) FROM onboarding_answer a "
                        + "JOIN profile p ON p.user_id = a.user_id", Long.class);
        assertThat(stats.active()).isEqualTo(withAnswers);
        assertThat(stats.versions()).isEqualTo(stats.active());
        assertThat(stats.pendingSync()).isEqualTo(stats.active());

        // 幂等：再跑一轮不产生任何新版本
        batch.refreshMissing(1000);
        assertThat(batch.stats().versions()).isEqualTo(stats.versions());

        // 内容、指纹、脱敏
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_taste WHERE is_active
                  AND length(persona_text) > 0 AND length(taste_text) > 0
                """, Long.class)).isEqualTo(stats.active());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_taste WHERE is_active AND input_hash ~ '^[0-9a-f]{64}$'",
                Long.class)).isEqualTo(stats.active());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_taste WHERE is_active AND (
                    persona_text ~ '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}'
                 OR taste_text   ~ '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}'
                 OR persona_text ~ '1[3-9][0-9]{9}'
                 OR taste_text   ~ '1[3-9][0-9]{9}'
                 OR persona_text LIKE '%sk-%'
                 OR taste_text   LIKE '%sk-%')
                """, Long.class)).isZero();

        // 版本化：答案变化 → 追加 v2、旧版本失效、taste_log 留痕
        Long uid = jdbc.queryForObject(
                "SELECT MIN(id) FROM \"user\" WHERE cohort IN ('synthetic','demo')", Long.class);
        assertThat(uid).isNotNull();
        answers.put(uid, "firstMessage", "全新的开场白，用于验证画像版本化");
        TasteExtractor.Result r = extractor.ensure(uid, "answer_edit");
        assertThat(r.outcome()).isEqualTo(TasteExtractor.Outcome.CREATED);
        assertThat(r.version()).isEqualTo(2);
        assertThat(activeCount(uid)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taste_log WHERE user_id = ?", Long.class, uid))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_taste WHERE user_id = ? AND version = 1 AND NOT is_active",
                Long.class, uid)).isEqualTo(1L);

        // 异步事件链路（IT 里执行器退化为同步，AFTER_COMMIT / fallbackExecution 可验证）
        jdbc.update("DELETE FROM user_taste WHERE user_id = ?", uid);
        assertThat(activeCount(uid)).isZero();
        events.publishEvent(new ProfileWrittenEvent(uid, "it_event"));
        assertThat(activeCount(uid)).isEqualTo(1L);
    }

    private long activeCount(long userId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_taste WHERE user_id = ? AND is_active", Long.class, userId);
        return n == null ? 0L : n;
    }
}
