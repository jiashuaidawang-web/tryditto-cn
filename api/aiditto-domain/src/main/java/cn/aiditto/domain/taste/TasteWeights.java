package cn.aiditto.domain.taste;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化口味权重（v3 反馈学习闭环的可计算载体）。
 *
 * 与 {@code user_taste.taste_text} 双轨并存：自然语言给分身/裁判/文案用，
 * 这 10 维权重给「单次反馈只调 1 个权重、幅度 ≤0.15」「taste_recovery 与隐藏真值求
 * Spearman ρ」「口是心非组收敛」「恶意差评稳定性」这些**能算出来的闸门**用。
 *
 * 隐藏真值（sim.sim_hidden_truth）永远不进这里；这里只是系统从反馈中学到的估计值。
 */
public final class TasteWeights {

    /** 固定维序：与合成仿真方案的 hidden_taste 10 维一一对应，换序会破坏指标可比性。 */
    public static final List<String> DIMS = List.of(
            "humor", "adventure", "expressiveness", "calm", "schedule",
            "ambition", "spending", "warmth", "height_sensitivity", "look_weight");

    /** 单次反馈允许的最大权重变化幅度（A01 / v3 §6.7 防过拟合）。 */
    public static final double MAX_DELTA = 0.15;
    /** 隐性体验维度（口是心非组真正看重的）与显性条件维度。 */
    public static final List<String> LATENT_DIMS = List.of("humor", "expressiveness", "adventure");
    public static final List<String> SURFACE_DIMS = List.of("height_sensitivity", "look_weight");

    private final Map<String, Double> w;

    private TasteWeights(Map<String, Double> w) {
        this.w = w;
    }

    /** 冷启动：所有维度中性 0.5，不预设任何审美。 */
    public static TasteWeights neutral() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String d : DIMS) m.put(d, 0.5);
        return new TasteWeights(Map.copyOf(m));
    }

    public static TasteWeights of(Map<String, ?> raw) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String d : DIMS) {
            Object v = raw == null ? null : raw.get(d);
            double x = 0.5;
            if (v instanceof Number n && Double.isFinite(n.doubleValue())) x = n.doubleValue();
            m.put(d, clamp(x));
        }
        return new TasteWeights(Map.copyOf(m));
    }

    public double get(String dim) {
        return w.getOrDefault(dim, 0.5);
    }

    public Map<String, Double> asMap() {
        return Map.copyOf(w);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < DIMS.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(DIMS.get(i)).append("\":")
                    .append(String.format(java.util.Locale.ROOT, "%.4f", w.get(DIMS.get(i))));
        }
        return sb.append('}').toString();
    }

    /**
     * 单次只允许改 1 个维度，且幅度被服务端强制 clamp 到 ±MAX_DELTA。
     * 这是 C 端红线：模型给出的建议再激进，也不能一次差评洗掉整份画像。
     */
    public TasteWeights apply(String dim, double delta) {
        if (!DIMS.contains(dim)) return this;
        double d = Double.isFinite(delta) ? Math.max(-MAX_DELTA, Math.min(MAX_DELTA, delta)) : 0.0;
        Map<String, Double> m = new LinkedHashMap<>(w);
        m.put(dim, clamp(w.get(dim) + d));
        return new TasteWeights(Map.copyOf(m));
    }

    /** 与另一份画像的最大单维差异，用于 stability 闸门（≤0.15）。 */
    public double maxDeltaFrom(TasteWeights other) {
        double max = 0.0;
        for (String d : DIMS) max = Math.max(max, Math.abs(get(d) - other.get(d)));
        return max;
    }

    /** 口是心非组收敛：隐性体验维度均值必须高于显性条件维度均值。 */
    public boolean twoFacedConverged() {
        return mean(LATENT_DIMS) > mean(SURFACE_DIMS) + 0.05;
    }

    public double latentMean() {
        return mean(LATENT_DIMS);
    }

    public double surfaceMean() {
        return mean(SURFACE_DIMS);
    }

    private double mean(List<String> dims) {
        double s = 0;
        for (String d : dims) s += get(d);
        return s / dims.size();
    }

    /**
     * Spearman ρ（按 DIMS 固定顺序求秩，处理并列用平均秩）。
     * 全常量向量没有可分秩，返回 0 而不是 NaN，避免指标虚高或炸掉报告。
     */
    public static double spearman(TasteWeights a, TasteWeights b) {
        double[] ra = ranks(a), rb = ranks(b);
        double ma = avg(ra), mb = avg(rb);
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < ra.length; i++) {
            double x = ra[i] - ma, y = rb[i] - mb;
            num += x * y;
            da += x * x;
            db += y * y;
        }
        if (da < 1e-12 || db < 1e-12) return 0.0;
        return num / Math.sqrt(da * db);
    }

    private static double[] ranks(TasteWeights t) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < DIMS.size(); i++) idx.add(i);
        idx.sort(Comparator.comparingDouble(i -> t.get(DIMS.get(i))));
        double[] r = new double[DIMS.size()];
        int i = 0;
        while (i < idx.size()) {
            int j = i;
            while (j + 1 < idx.size()
                    && Math.abs(t.get(DIMS.get(idx.get(j + 1))) - t.get(DIMS.get(idx.get(i)))) < 1e-9) j++;
            double avgRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) r[idx.get(k)] = avgRank;
            i = j + 1;
        }
        return r;
    }

    private static double avg(double[] a) {
        double s = 0;
        for (double x : a) s += x;
        return s / a.length;
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
