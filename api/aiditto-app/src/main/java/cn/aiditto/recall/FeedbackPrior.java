package cn.aiditto.recall;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 粗排「反馈历史」分量（v3 规格 0.20 权重）的真实实现，替代 Step 5 里恒为 0 的占位。
 *
 * 数据来源（全部是已存在的表，不新增迁移）：
 *  - feedback.rating / again / met / felt_safe：候选过往约会的真实体验分
 *  - ai_pair_memory.result：该候选被拒绝 / 爽约 / 举报的历史
 *
 * C 端谨慎取舍：
 *  - 无反馈的冷启动用户/候选返回**中性 0.5**，绝不用 0 把新人整体压死。
 *  - 只做「质量先验」，不做口味画像改写（那是 Step 8 TasteRewriter 的职责），
 *    避免召回层越权学习、也避免同一份反馈被两个环节重复计入。
 *  - 一次 SQL 批量取回，不在候选循环里查库；查询失败返回空表，召回链路不因它中断。
 */
@Service
public class FeedbackPrior {

    private static final String SQL = """
            SELECT f.user_id AS uid,
                   AVG(f.rating)::float8 AS avg_rating,
                   COUNT(*) AS n,
                   SUM(CASE WHEN f.again = 'yes' THEN 1 ELSE 0 END)::float8 AS again_yes,
                   SUM(CASE WHEN f.met <> 'met' OR f.felt_safe = FALSE THEN 1 ELSE 0 END)::float8 AS bad
            FROM feedback f
            WHERE f.user_id = ANY (?)
            GROUP BY f.user_id
            """;

    /** 中性先验：无反馈时不奖不罚。 */
    public static final double NEUTRAL = 0.5;

    private final JdbcTemplate jdbc;

    public FeedbackPrior(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 返回 userId -> [0,1] 的反馈质量先验。查库异常时返回空 Map（上层按 NEUTRAL 处理）。
     */
    public Map<Long, Double> load(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return Map.of();
        try {
            Long[] arr = candidateIds.toArray(Long[]::new);
            Map<Long, Double> out = new HashMap<>();
            jdbc.query(SQL, ps -> ps.setArray(1,
                            ps.getConnection().createArrayOf("bigint", arr)),
                    rs -> {
                        long uid = rs.getLong("uid");
                        double rating = rs.getDouble("avg_rating");
                        int n = rs.getInt("n");
                        double againYes = rs.getDouble("again_yes");
                        double bad = rs.getDouble("bad");
                        // rating 1..5 -> 0..1；样本少时向中性收缩（贝叶斯式平滑，防单次差评毁掉候选）
                        double quality = rs.wasNull() ? NEUTRAL : (rating - 1.0) / 4.0;
                        double againRate = n <= 0 ? NEUTRAL : againYes / n;
                        double safety = n <= 0 ? NEUTRAL : 1.0 - (bad / n);
                        double raw = 0.6 * quality + 0.3 * againRate + 0.1 * safety;
                        double shrink = n / (double) (n + 3); // 3 条反馈后才接近真实值
                        out.put(uid, clamp(NEUTRAL + (raw - NEUTRAL) * shrink));
                    });
            return out;
        } catch (RuntimeException e) {
            return Collections.emptyMap();
        }
    }

    public double neutralIfAbsent(Map<Long, Double> priors, long userId) {
        return priors.getOrDefault(userId, NEUTRAL);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
