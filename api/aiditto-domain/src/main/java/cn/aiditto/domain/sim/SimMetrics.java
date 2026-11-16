package cn.aiditto.domain.sim;

import cn.aiditto.domain.taste.TasteWeights;

import java.util.ArrayList;
import java.util.List;

/**
 * Go/No-Go 闸门指标（仿真方案 §6）。全部是纯函数、可复算，不依赖报告里的人工描述。
 */
public final class SimMetrics {

    private SimMetrics() {}

    /** regret = (最优可行 true_score − 选中 true_score) / 最优；最优为 0 时记 0。 */
    public static double regret(double chosen, double best) {
        if (best <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (best - chosen) / best));
    }

    /**
     * top1_hit@decile：被选中的 true_score 是否落在该用户当周全部可行候选的前 10%。
     * 至少 10 个候选才按分位数算；不足时按「是否就是最优」判定，避免小样本虚高。
     */
    public static boolean hitTopDecile(double chosen, List<Double> feasible) {
        if (feasible == null || feasible.isEmpty()) return false;
        if (feasible.size() < 10) return chosen >= max(feasible) - 1e-9;
        List<Double> sorted = new ArrayList<>(feasible);
        sorted.sort((a, b) -> Double.compare(b, a));
        int k = Math.max(1, (int) Math.ceil(sorted.size() * 0.10));
        double threshold = sorted.get(k - 1);
        return chosen >= threshold - 1e-9;
    }

    public static double hitRate(List<Double> chosen, List<List<Double>> allFeasible) {
        if (chosen == null || chosen.isEmpty()) return 0.0;
        int hit = 0;
        for (int i = 0; i < chosen.size(); i++) {
            if (hitTopDecile(chosen.get(i), allFeasible.get(i))) hit++;
        }
        return (double) hit / chosen.size();
    }

    /** 线性回归斜率（regret 对周期号）：<0 说明在变好。 */
    public static double slope(List<Double> series) {
        int n = series == null ? 0 : series.size();
        if (n < 2) return 0.0;
        double mx = (n - 1) / 2.0, my = avg(series);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - mx) * (series.get(i) - my);
            den += (i - mx) * (i - mx);
        }
        return den < 1e-12 ? 0.0 : num / den;
    }

    /** 相邻周期不上升的次数（闸门：12 周期至少 8 个周期单调下降）。 */
    public static int monotoneDecreaseCount(List<Double> series) {
        if (series == null || series.size() < 2) return 0;
        int n = 0;
        for (int i = 1; i < series.size(); i++) {
            if (series.get(i) <= series.get(i - 1) + 1e-9) n++;
        }
        return n;
    }

    /** taste_recovery：学到的画像与隐藏真值的 Spearman ρ 均值（≥0.70 为达标）。 */
    public static double tasteRecovery(List<TasteWeights> learned, List<TasteWeights> truth) {
        int n = Math.min(learned.size(), truth.size());
        if (n == 0) return 0.0;
        double s = 0;
        for (int i = 0; i < n; i++) s += TasteWeights.spearman(learned.get(i), truth.get(i));
        return s / n;
    }

    /** 口是心非组收敛比例：隐性维度权重超过显性维度。 */
    public static double twoFacedConvergence(List<TasteWeights> learned) {
        if (learned == null || learned.isEmpty()) return 0.0;
        int ok = 0;
        for (TasteWeights w : learned) if (w.twoFacedConverged()) ok++;
        return (double) ok / learned.size();
    }

    /** 公平性：长尾用户曝光份额 / 人数占比，≥0.80 达标。 */
    public static double longTailFairness(double exposureShare, double populationShare) {
        return populationShare <= 0 ? 1.0 : exposureShare / populationShare;
    }

    /**
     * 预测分与真值的 Spearman 秩相关（在当周被召回/精排的候选边上计算）。
     *
     * 这是诊断「学习有没有真的让排序变准」的关键指标：配对质量最终取决于排序，
     * 而不是分数绝对值。若 taste_recovery 上升但本指标不动，说明学到的画像
     * 没有被正确用在预测函数上；若本指标上升而 top1_hit 不升，才是匹配结构问题。
     */
    public static double rankCorrelation(List<Double> x, List<Double> y) {
        if (x == null || y == null || x.size() != y.size() || x.size() < 2) return 0.0;
        double[] rx = ranks(x), ry = ranks(y);
        double mx = avg(rx), my = avg(ry);
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < rx.length; i++) {
            double a = rx[i] - mx, b = ry[i] - my;
            num += a * b;
            dx += a * a;
            dy += b * b;
        }
        return dx < 1e-12 || dy < 1e-12 ? 0.0 : num / Math.sqrt(dx * dy);
    }

    /** 平均秩（并列取平均），用于 Spearman。 */
    private static double[] ranks(List<Double> xs) {
        int n = xs.size();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        idx.sort((i, j) -> Double.compare(xs.get(i), xs.get(j)));
        double[] r = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && Math.abs(xs.get(idx.get(j + 1)) - xs.get(idx.get(i))) < 1e-12) j++;
            double avgRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) r[idx.get(k)] = avgRank;
            i = j + 1;
        }
        return r;
    }

    private static double avg(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.length;
    }

    private static double max(List<Double> xs) {
        double m = Double.NEGATIVE_INFINITY;
        for (double x : xs) m = Math.max(m, x);
        return m;
    }

    private static double avg(List<Double> xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.size();
    }
}
