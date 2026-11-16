package cn.aiditto.taste;

import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.match.JudgeCalibrationService;
import cn.aiditto.match.PairMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Step 8 每周期批量消费反馈（不侵入反馈提交链路、不阻塞用户请求）。
 *
 * 设计要点：
 * - 只扫「尚未被 taste_log.trigger_feedback 消费」的反馈，可重入、崩溃续跑；
 * - 每周每用户最多 1 次由 {@link TasteRewriter} 的 taste_log 计数硬闸门保证；
 * - 同时把负向结果沉淀进 {@link PairMemoryService}（双向拒绝 → 永久 forbidden，A04）；
 * - 末尾重算裁判校准（≥30 条才启用）；
 * - 任何一条失败只记日志，不阻断其余反馈与下一周期。
 *
 * 仿真回放每周期末调用 {@link #scanAndRewrite(int, Map)}，跑的就是这段生产代码。
 */
@Service
public class FeedbackLearningService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLearningService.class);

    private static final String PENDING = """
            SELECT f.match_id, f.user_id, f.met, f.rating, f.again, f.felt_safe, f.comment_enc,
                   m.user_low, m.user_high, m.week_id,
                   rc.final_score, rc.sim_report->>'reason_text' AS judge_reason
            FROM feedback f
            JOIN "match" m ON m.id = f.match_id
            LEFT JOIN recall_candidate rc ON rc.week_id = m.week_id
              AND rc.user_id = f.user_id
              AND rc.cand_id = CASE WHEN m.user_low = f.user_id THEN m.user_high ELSE m.user_low END
            WHERE NOT EXISTS (
              SELECT 1 FROM taste_log t
              WHERE t.user_id = f.user_id AND t.trigger_feedback = f.match_id)
            ORDER BY f.created_at
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final Crypto crypto;
    private final TasteRewriter rewriter;
    private final PairMemoryService pairs;
    private final JudgeCalibrationService calibration;
    private final FeedbackLearningProperties props;

    public FeedbackLearningService(JdbcTemplate jdbc, Crypto crypto, TasteRewriter rewriter,
                                   PairMemoryService pairs, JudgeCalibrationService calibration,
                                   FeedbackLearningProperties props) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.rewriter = rewriter;
        this.pairs = pairs;
        this.calibration = calibration;
        this.props = props;
    }

    public Stats scanAndRewrite(int limit) {
        return scanAndRewrite(limit, Map.of());
    }

    /**
     * @param reasonTags 可选的 {@code userId:matchId → 结构化原因标签}，
     *                   供合成仿真注入 Oracle 标签；生产留空时由反馈文本推导维度。
     */
    public Stats scanAndRewrite(int limit, Map<String, String> reasonTags) {
        if (!Boolean.TRUE.equals(props.enabled())) return new Stats(0, 0, 0, 0, 0, 0, 0.0);
        int cap = Math.max(1, Math.min(limit <= 0 ? props.maxPerScan() : limit, props.maxPerScan()));
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(PENDING, cap);
        } catch (RuntimeException e) {
            log.warn("feedback scan failed: {}", e.getClass().getSimpleName());
            return new Stats(0, 0, 0, 0, 0, 0, 0.0);
        }
        int rewritten = 0, weekly = 0, noSignal = 0, failed = 0, forbidden = 0;
        for (Map<String, Object> r : rows) {
            try {
                // 雪花 ID 是 19 位，绝不能经 double 中转（53 位尾数会丢精度，
                // 导致 reasonTags 按 userId:matchId 匹配不上、pair_memory 记错人）。
                long userId = ((Number) r.get("user_id")).longValue();
                long matchId = ((Number) r.get("match_id")).longValue();
                long low = ((Number) r.get("user_low")).longValue();
                long high = ((Number) r.get("user_high")).longValue();
                long partner = userId == low ? high : low;
                long weekId = ((Number) r.get("week_id")).longValue();
                Integer rating = r.get("rating") == null ? null : ((Number) r.get("rating")).intValue();
                String again = str(r.get("again"));
                String met = str(r.get("met"));
                Boolean feltSafe = r.get("felt_safe") == null ? null : (Boolean) r.get("felt_safe");
                Double judge = r.get("final_score") == null ? null
                        : ((Number) r.get("final_score")).doubleValue();
                // 只取裁判理由文本，不把整份 sim_report（含模拟对话）塞进提示词
                String judgeReason = str(r.get("judge_reason"));
                String comment = decrypt(r.get("comment_enc"));
                String reasonTag = reasonTags.getOrDefault(userId + ":" + matchId, null);

                TasteRewriter.Input in = new TasteRewriter.Input(userId, matchId, partner, rating, again,
                        met, feltSafe, comment, reasonTag, judge, judgeReason);
                TasteRewriter.Result res = rewriter.rewrite(in);
                switch (res.outcome()) {
                    case REWRITTEN -> rewritten++;
                    case WEEKLY_LIMIT -> weekly++;
                    case NO_SIGNAL, NO_ACTIVE -> noSignal++;
                    default -> failed++;
                }

                String result = "noShow".equals(met) ? "no_show"
                        : "no".equals(again) ? "refused" : "yes".equals(again) ? "again" : "dated";
                boolean bothRefused = "no".equals(again) && bothRefused(matchId);
                // 只存结构化标签/枚举，绝不把反馈原文（可能含 PII）写进 reject_reason
                pairs.record(userId, partner, weekId, result,
                        bothRefused ? "both_refused" : (reasonTag == null ? "feedback" : reasonTag),
                        judge, bothRefused);
                if (bothRefused) forbidden++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("feedback learning row failed: {}", e.getClass().getSimpleName());
            }
        }
        JudgeCalibrationService.Calibration cal;
        try {
            cal = calibration.recompute();
        } catch (RuntimeException e) {
            cal = new JudgeCalibrationService.Calibration(0, 0, 0, 0, false);
            log.warn("judge calibration failed: {}", e.getClass().getSimpleName());
        }
        log.info("feedback learning scanned={} rewritten={} weeklyLimit={} noSignal={} failed={} "
                        + "forbiddenPairs={} calibration={} bias={}",
                rows.size(), rewritten, weekly, noSignal, failed, forbidden, cal.active(), cal.bias());
        return new Stats(rows.size(), rewritten, weekly, noSignal, failed, forbidden, cal.bias());
    }

    private boolean bothRefused(long matchId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM feedback
                WHERE match_id = ? AND again = 'no'
                """, Integer.class, matchId);
        return n != null && n >= 2;
    }

    private String decrypt(Object blob) {
        if (!(blob instanceof byte[] bytes) || bytes.length == 0) return "";
        try {
            String s = crypto.decrypt(bytes);
            return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) : s);
        } catch (RuntimeException e) {
            return ""; // 解密失败不影响结构化反馈学习
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    public record Stats(int scanned, int rewritten, int weeklyLimit, int noSignal,
                        int failed, int forbiddenPairs, double calibrationBias) {}
}
