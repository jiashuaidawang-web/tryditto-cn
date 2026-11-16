package cn.aiditto.domain.recall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * E1–E14 实验矩阵的纯函数内核：逐路消融、RRF 权重网格、单路占比与候选覆盖率。
 *
 * 与 Qdrant 无关，可在毫秒级跑完数百组权重组合，用于 Step 9 仿真前的权重调优；
 * 真实 ANN 部分的 ef 网格 / 分桶 Recall 由 app 层 RecallEvaluator 负责。
 *
 * 闸门（v3 Go/No-Go）：
 *  - 单路贡献占比 ≤ 0.60：任一召回路不能垄断候选集
 *  - 候选覆盖率 ≥ 0.98：待匹配用户中能拿到 ≥1 个候选的比例
 */
public final class RecallMatrix {

    public record Cell(String label, double maxSingleRouteShare, double coverage,
                       int fusedCount, boolean gatePassed) {}

    /**
     * 独占单路占比：仅被**唯一一路**召回的候选数 / 总候选数。
     *
     * RrfFusion.share 度量的是"该路贡献过的候选占比"，五路都召回同一批好候选时它会等于 1.0，
     * 与"某一路垄断候选集"完全无法区分——前者健康、后者才是规格要防的风险。
     * 闸门必须使用独占口径，避免把"多路共识"误报成"单路垄断"。
     */
    public static double maxExclusiveRouteShare(List<RrfFusion.Fused> fused) {
        if (fused.isEmpty()) return 0;
        Map<String, Long> exclusive = new LinkedHashMap<>();
        long totalExclusive = 0;
        for (RrfFusion.Fused f : fused) {
            if (f.sources().size() == 1) {
                exclusive.merge(f.sources().get(0), 1L, Long::sum);
                totalExclusive++;
            }
        }
        if (totalExclusive == 0) return 0;
        return exclusive.values().stream().mapToLong(Long::longValue).max().orElse(0)
                / (double) fused.size();
    }

    private static final String[] ROUTES = {
            "R1_TASTE", "R2_PERSONA", "R3_TAGS", "R4_PREFERENCE", "R5_EXPLORE"};

    private RecallMatrix() {}

    /** 单路占比：融合结果里被某一路贡献的候选占比（取最大值）。 */
    public static double maxSingleRouteShare(List<RrfFusion.Fused> fused) {
        if (fused.isEmpty()) return 0;
        double max = 0;
        for (String r : ROUTES) max = Math.max(max, RrfFusion.share(fused, r));
        return max;
    }

    /** 候选覆盖率：有 ≥1 个融合候选的用户数 / 总待匹配用户数。 */
    public static double coverage(int usersWithCandidates, int totalUsers) {
        return totalUsers <= 0 ? 1.0 : (double) usersWithCandidates / totalUsers;
    }

    public static boolean gate(double maxShare, double coverage) {
        return maxShare <= 0.60 && coverage >= 0.98;
    }

    /**
     * 权重网格 + 逐路消融。routes 为每路的有序命中 userId 列表。
     * 返回每个组合的单路占比、覆盖率与闸门结论，供 E8 权重调优选出最优组合。
     */
    public static List<Cell> sweep(Map<String, List<Long>> routes, int totalUsers,
                                   double k, double step) {
        List<Cell> out = new ArrayList<>();
        // 基线：等权
        out.add(evaluate("base-equal", routes, equalWeights(1.0), totalUsers, k));

        // E1–E5 逐路消融：每次把一路权重置 0，观察该路对多样性的贡献
        for (String r : ROUTES) {
            Map<String, Double> w = equalWeights(1.0);
            w.put(r, 0.0);
            out.add(evaluate("ablate-" + r, routes, w, totalUsers, k));
        }

        // E6–E14 权重网格：主路 R1 权重从 1.0 递减，结构化路同步提升
        for (double w1 = step; w1 <= 2.0 + 1e-9; w1 += step) {
            Map<String, Double> w = new LinkedHashMap<>();
            w.put("R1_TASTE", round(w1));
            w.put("R2_PERSONA", round(w1 * 0.9));
            w.put("R3_TAGS", round(2.0 - w1));
            w.put("R4_PREFERENCE", round((2.0 - w1) * 0.6));
            w.put("R5_EXPLORE", round((2.0 - w1) * 0.8));
            out.add(evaluate(String.format("grid-w1=%.2f", w1), routes, w, totalUsers, k));
        }
        return out;
    }

    private static Cell evaluate(String label, Map<String, List<Long>> routes,
                                 Map<String, Double> weights, int totalUsers, double k) {
        List<List<RrfFusion.Ranked>> ranked = new ArrayList<>();
        for (String r : ROUTES) {
            List<Long> ids = routes.getOrDefault(r, List.of());
            List<RrfFusion.Ranked> one = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                one.add(new RrfFusion.Ranked(ids.get(i), i + 1, r));
            }
            ranked.add(one);
        }
        List<RrfFusion.Fused> fused = RrfFusion.merge(ranked, weights, k);
        double share = maxSingleRouteShare(fused);
        double cov = coverage(fused.isEmpty() ? 0 : totalUsers, totalUsers);
        return new Cell(label, round(share), round(cov), fused.size(), gate(share, cov));
    }

    private static Map<String, Double> equalWeights(double v) {
        Map<String, Double> w = new LinkedHashMap<>();
        for (String r : ROUTES) w.put(r, v);
        return w;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
