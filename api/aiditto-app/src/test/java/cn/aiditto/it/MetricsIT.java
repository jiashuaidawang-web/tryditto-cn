package cn.aiditto.it;

import cn.aiditto.match.MetricsProperties;
import cn.aiditto.match.WeekMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实效果埋点闸门（真 PG16）：验证结果指标从事实表聚合正确、带策略版本、幂等可重算。
 * 默认跳过；AIDITTO_METRICS_IT=1 开启。
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AIDITTO_METRICS_IT", matches = "1")
class MetricsIT extends ApiIT {

    @DynamicPropertySource
    static void metricsProps(DynamicPropertyRegistry r) {
        r.add("aiditto.metrics.enabled", () -> "true");
        r.add("aiditto.metrics.config-version", () -> "v4-test-scheme");
    }

    @Autowired WeekMetricsService metrics;
    @Autowired MetricsProperties props;
    @Autowired JdbcTemplate jdbc;

    @Test
    void capturesResultMetricsWithVersionAndIsIdempotent() {
        assertThat(props.enabled()).isTrue();
        assertThat(props.configVersion()).isEqualTo("v4-test-scheme");

        // 造一个已关闭的周 + 数据：2 对配对，1 对双方接受(mutual)，1 对一方拒绝；1 人见面，1 人满意
        long weekId = 990_000_000_000_000_000L;
        jdbc.update("DELETE FROM week_metric WHERE week_id=?", weekId);
        jdbc.update("DELETE FROM week_participant WHERE week_id=?", weekId);
        jdbc.update("DELETE FROM feedback WHERE match_id IN (SELECT id FROM \"match\" WHERE week_id=?)", weekId);
        jdbc.update("DELETE FROM match_side WHERE week_id=?", weekId);
        jdbc.update("DELETE FROM \"match\" WHERE week_id=?", weekId);
        jdbc.update("DELETE FROM week WHERE id=?", weekId);
        jdbc.update("""
                INSERT INTO week (id,pool,week_start,trigger_kind,run_no,state,created_at,updated_at)
                VALUES (?,?,DATE '2026-11-11','manual',1,'closed',now(),now())
                """, weekId, "bj");
        long m1 = weekId + 1, m2 = weekId + 2;
        long u1 = weekId + 11, u2 = weekId + 12, u3 = weekId + 13, u4 = weekId + 14;
        insertMatch(jdbc, m1, weekId, u1, u2, "mutual");
        insertMatch(jdbc, m2, weekId, u3, u4, "declined");
        insertSide(jdbc, m1, u1, weekId, "accept"); insertSide(jdbc, m1, u2, weekId, "accept");
        insertSide(jdbc, m2, u3, weekId, "accept"); insertSide(jdbc, m2, u4, weekId, "decline");
        jdbc.update("INSERT INTO week_participant (week_id,user_id,profile_version,status) VALUES (?,?,1,'in')", weekId, u1);
        jdbc.update("INSERT INTO week_participant (week_id,user_id,profile_version,status) VALUES (?,?,1,'in')", weekId, u2);
        jdbc.update("INSERT INTO week_participant (week_id,user_id,profile_version,status) VALUES (?,?,1,'in')", weekId, u3);
        jdbc.update("INSERT INTO week_participant (week_id,user_id,profile_version,status) VALUES (?,?,1,'in')", weekId, u4);
        // 一人真见面且满意
        jdbc.update("INSERT INTO feedback (match_id,user_id,met,again,created_at) VALUES (?,?,'met','yes',now())", m1, u1);
        jdbc.update("INSERT INTO feedback (match_id,user_id,met,again,created_at) VALUES (?,?,'noShow','no',now())", m2, u3);

        Map<String, Object> snap = metrics.capture(weekId);
        assertThat(snap.get("captured")).isEqualTo(true);
        assertThat(snap.get("configVersion")).isEqualTo("v4-test-scheme");
        assertThat(snap.get("pairs")).isEqualTo(2);
        assertThat(snap.get("mutualOk")).isEqualTo(1);
        assertThat(snap.get("met")).isEqualTo(1);

        // 幂等：再捕一次仍只有一行（同 week+version 覆盖）
        metrics.capture(weekId);
        Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM week_metric WHERE week_id=? AND config_version='v4-test-scheme'",
                Long.class, weekId);
        assertThat(cnt).isEqualTo(1L);

        // 看板：比率在查询层按事实算（2 对里 1 对成功 → successRate 0.5）
        Map<String, Object> row = metrics.metrics(10).stream()
                .filter(r -> Long.parseLong(r.get("weekId").toString()) == weekId)
                .findFirst().orElseThrow();
        assertThat(((Number) row.get("successRate")).doubleValue()).isEqualTo(0.5);
        assertThat(((Number) row.get("meetRate")).doubleValue()).isEqualTo(1.0);
        assertThat(row.get("configVersion")).isEqualTo("v4-test-scheme");
        System.out.println("[METRICS-IT] captured=" + snap + " successRate=" + row.get("successRate"));
    }

    private static void insertMatch(JdbcTemplate jdbc, long id, long weekId, long low, long high, String outcome) {
        jdbc.update("""
                INSERT INTO "match" (id,week_id,user_low,user_high,score,state,outcome,version,created_at,updated_at)
                VALUES (?,?,?,?,80,'published',?,0,now(),now())
                """, id, weekId, low, high, outcome);
    }

    private static void insertSide(JdbcTemplate jdbc, long matchId, long userId, long weekId, String response) {
        jdbc.update("INSERT INTO match_side (match_id,user_id,week_id,response) VALUES (?,?,?,?)",
                matchId, userId, weekId, response);
    }
}
