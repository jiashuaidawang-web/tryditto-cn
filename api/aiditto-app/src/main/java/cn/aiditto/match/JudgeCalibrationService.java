package cn.aiditto.match;

import cn.aiditto.infra.web.Times;
import cn.aiditto.taste.FeedbackLearningProperties;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * 裁判校准（v3 Step 8）：把「AI 预测分」与「真人反馈观测分」对齐。
 *
 * 防过拟合：样本 <30 条一律 bias=0（保持原行为）；即便达标也只补 20% 差距、
 * 硬夹在 ±10，绝不让校准反客为主盖过裁判 rubric。校准结果落 judge_calibration 可审计。
 */
@Service
public class JudgeCalibrationService {

    public record Calibration(int samples, double predictedAvg, double observedAvg,
                              double bias, boolean active) {}

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;
    private final Clock clock;
    private final FeedbackLearningProperties props;
    private volatile double bias = 0.0;

    public JudgeCalibrationService(JdbcTemplate jdbc, IdentifierGenerator ids, Clock clock,
                                   FeedbackLearningProperties props) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.clock = clock;
        this.props = props;
    }

    public double currentBias() {
        return bias;
    }

    /** 应用重启后从库里恢复最近一次生效的校准，避免重启悄悄丢掉闭环成果。 */
    public void refresh() {
        try {
            Double b = jdbc.queryForObject("""
                    SELECT bias FROM judge_calibration WHERE active ORDER BY created_at DESC LIMIT 1
                    """, Double.class);
            this.bias = b == null ? 0.0 : b;
        } catch (RuntimeException e) {
            this.bias = 0.0;
        }
    }

    /** 把校准偏置作用到 AI 分上，仍夹在 0-100。 */
    public int apply(int total) {
        return Math.max(0, Math.min(100, (int) Math.round(total + bias)));
    }

    public Calibration recompute() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rc.final_score AS predicted, f.rating, f.again
                FROM recall_candidate rc
                JOIN "match" m ON m.week_id = rc.week_id
                  AND ((m.user_low = rc.user_id AND m.user_high = rc.cand_id)
                    OR (m.user_high = rc.user_id AND m.user_low = rc.cand_id))
                JOIN feedback f ON f.match_id = m.id AND f.user_id = rc.user_id
                WHERE rc.final_score IS NOT NULL AND f.rating IS NOT NULL
                """);
        int n = rows.size();
        double predicted = 0, observed = 0;
        for (Map<String, Object> r : rows) {
            predicted += num(r.get("predicted"));
            int rating = (int) num(r.get("rating"));
            String again = r.get("again") == null ? "" : r.get("again").toString();
            double obs = rating * 20.0 + ("yes".equals(again) ? 5.0 : "no".equals(again) ? -5.0 : 0.0);
            observed += Math.max(0, Math.min(100, obs));
        }
        double pAvg = n == 0 ? 0 : predicted / n;
        double oAvg = n == 0 ? 0 : observed / n;
        boolean active = n >= props.minCalibrationSamples();
        double cap = props.calibrationCap() == null ? 10.0 : props.calibrationCap();
        double b = active ? Math.max(-cap, Math.min(cap, (oAvg - pAvg) * 0.2)) : 0.0;
        this.bias = b;
        jdbc.update("UPDATE judge_calibration SET active = FALSE WHERE active");
        jdbc.update("""
                INSERT INTO judge_calibration
                  (id, sample_cnt, predicted_avg, observed_avg, bias, active, created_at)
                VALUES (?,?,?,?,?,?,?)
                """, ((Number) ids.nextId(new Object())).longValue(), n, pAvg, oAvg, b, active,
                Timestamp.from(Times.millis(clock.instant())));
        return new Calibration(n, pAvg, oAvg, b, active);
    }

    private static double num(Object o) {
        return o instanceof Number x ? x.doubleValue() : 0.0;
    }
}
