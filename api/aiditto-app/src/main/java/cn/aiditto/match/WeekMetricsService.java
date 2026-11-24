package cn.aiditto.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实效果埋点（创始人需求）。每周批结束后拍一张「结果指标快照」并打上策略版本标记，
 * 让「改了学习方案后接受率从 X% 到 Y%」这类归因可用真实数据回答。
 *
 * 设计原则：
 *  - 指标从现有事实表聚合算出（match / match_side / feedback），不重复埋点、不冗余原始事件；
 *  - 幂等：同一 week + config_version 覆盖写，可随时重算（反馈是逐步产生的）；
 *  - 只读聚合，不改变配对流程，开关关闭时零开销。
 */
@Service
public class WeekMetricsService {
    private static final Logger log = LoggerFactory.getLogger(WeekMetricsService.class);

    private final JdbcTemplate jdbc;
    private final MetricsProperties props;
    private final Clock clock;

    public WeekMetricsService(JdbcTemplate jdbc, MetricsProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    /** 周批关闭时调用：按当前事实算快照并落库。幂等，可重复调用刷新。 */
    public Map<String, Object> capture(long weekId) {
        if (!Boolean.TRUE.equals(props.enabled())) return Map.of("enabled", false);
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM week WHERE id=?", Long.class, weekId);
        if (exists == null || exists == 0) return Map.of("captured", false, "reason", "no-week");

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT w.pool, w.week_start,
                  (SELECT COUNT(*) FROM "match" m WHERE m.week_id=w.id) AS pairs,
                  (SELECT COUNT(*) FROM week_participant wp WHERE wp.week_id=w.id) AS participants,
                  (SELECT COUNT(*) FROM match_side s WHERE s.week_id=w.id AND s.response<>'pending') AS responded,
                  (SELECT COUNT(*) FROM match_side s WHERE s.week_id=w.id AND s.response='accept') AS accepted,
                  (SELECT COUNT(*) FROM "match" m WHERE m.week_id=w.id AND m.outcome='mutual') AS mutual_ok,
                  (SELECT COUNT(*) FROM feedback f JOIN "match" m ON m.id=f.match_id
                     WHERE m.week_id=w.id AND f.met='met') AS met,
                  (SELECT COUNT(*) FROM feedback f JOIN "match" m ON m.id=f.match_id
                     WHERE m.week_id=w.id AND f.again='yes') AS again_yes,
                  (SELECT COUNT(*) FROM feedback f JOIN "match" m ON m.id=f.match_id
                     WHERE m.week_id=w.id) AS feedback_cnt
                FROM week w WHERE w.id=?
                """, weekId);

        String version = props.configVersion();
        jdbc.update("""
                INSERT INTO week_metric
                  (week_id, pool, week_start, config_version, pairs, participants, responded,
                   accepted, mutual_ok, met, again_yes, feedback_cnt, captured_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (week_id, config_version) DO UPDATE SET
                  responded=EXCLUDED.responded, accepted=EXCLUDED.accepted, mutual_ok=EXCLUDED.mutual_ok,
                  met=EXCLUDED.met, again_yes=EXCLUDED.again_yes, feedback_cnt=EXCLUDED.feedback_cnt,
                  captured_at=EXCLUDED.captured_at
                """,
                weekId, str(row.get("pool")), row.get("week_start"), version,
                num(row.get("pairs")), num(row.get("participants")), num(row.get("responded")),
                num(row.get("accepted")), num(row.get("mutual_ok")), num(row.get("met")),
                num(row.get("again_yes")), num(row.get("feedback_cnt")),
                Timestamp.from(clock.instant()));

        log.info("week metric captured week={} version={} pairs={} accepted={} mutual={} met={}",
                weekId, version, num(row.get("pairs")), num(row.get("accepted")),
                num(row.get("mutual_ok")), num(row.get("met")));
        return snapshot(row, version);
    }

    /** 看板查询：按版本 + 周列出结果指标，比率在查询层算。 */
    public List<Map<String, Object>> metrics(int limit) {
        return jdbc.query("""
                SELECT week_id, pool, week_start, config_version, pairs, participants,
                       responded, accepted, mutual_ok, met, again_yes, feedback_cnt, captured_at,
                       CASE WHEN pairs=0 THEN 0 ELSE round(responded::numeric/pairs, 4) END AS respond_rate,
                       CASE WHEN pairs=0 THEN 0 ELSE round(accepted::numeric/(pairs*2), 4) END AS accept_rate,
                       CASE WHEN pairs=0 THEN 0 ELSE round(mutual_ok::numeric/pairs, 4) END AS success_rate,
                       CASE WHEN mutual_ok=0 THEN 0 ELSE round(met::numeric/mutual_ok, 4) END AS meet_rate,
                       CASE WHEN feedback_cnt=0 THEN 0 ELSE round(again_yes::numeric/feedback_cnt, 4) END AS satisfaction_rate
                FROM week_metric ORDER BY week_start DESC, config_version, week_id DESC LIMIT ?
                """, (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("weekId", Long.toString(rs.getLong("week_id")));
                    m.put("pool", rs.getString("pool"));
                    m.put("weekStart", rs.getDate("week_start").toLocalDate().toString());
                    m.put("configVersion", rs.getString("config_version"));
                    m.put("pairs", rs.getInt("pairs"));
                    m.put("participants", rs.getInt("participants"));
                    m.put("respondRate", rs.getDouble("respond_rate"));
                    m.put("acceptRate", rs.getDouble("accept_rate"));
                    m.put("successRate", rs.getDouble("success_rate"));
                    m.put("meetRate", rs.getDouble("meet_rate"));
                    m.put("satisfactionRate", rs.getDouble("satisfaction_rate"));
                    m.put("feedbackCnt", rs.getInt("feedback_cnt"));
                    m.put("capturedAt", rs.getTimestamp("captured_at").toInstant().toString());
                    return m;
                }, limit);
    }

    /** 按版本聚合，用于回答「换方案后从多少提升到多少」。 */
    public List<Map<String, Object>> byVersion() {
        return jdbc.query("""
                SELECT config_version, COUNT(*) AS weeks, SUM(pairs) AS pairs,
                       SUM(accepted) AS accepted, SUM(mutual_ok) AS mutual_ok,
                       SUM(met) AS met, SUM(feedback_cnt) AS feedback_cnt, SUM(again_yes) AS again_yes
                FROM week_metric GROUP BY config_version ORDER BY config_version
                """, (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("configVersion", rs.getString("config_version"));
                    m.put("weeks", rs.getInt("weeks"));
                    m.put("pairs", rs.getInt("pairs"));
                    int pairs = rs.getInt("pairs");
                    m.put("acceptRate", pairs == 0 ? 0.0 : round(rs.getInt("accepted") / (double) (pairs * 2)));
                    m.put("successRate", pairs == 0 ? 0.0 : round(rs.getInt("mutual_ok") / (double) pairs));
                    int fb = rs.getInt("feedback_cnt");
                    m.put("satisfactionRate", fb == 0 ? 0.0 : round(rs.getInt("again_yes") / (double) fb));
                    return m;
                });
    }

    private Map<String, Object> snapshot(Map<String, Object> row, String version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("captured", true);
        m.put("configVersion", version);
        m.put("pairs", num(row.get("pairs")));
        m.put("accepted", num(row.get("accepted")));
        m.put("mutualOk", num(row.get("mutual_ok")));
        m.put("met", num(row.get("met")));
        return m;
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
