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
 *
 * <p>V3 对照基线，语义是混在一起的：同一组 0 到 1 的权重既被当成用户自己的特质，
 * 又被当成对伴侣的偏好，冷启动的 0.5 也不能表示「不知道」。V4 的关系画像把这三件事拆开。
 * {@link #rankAgainst} 仍是这条旧链路的排序函数，本轮不改它，匹配引擎继续用它。
 */
public final class TasteWeights {

    /** 固定维序：与合成仿真方案的 hidden_taste 10 维一一对应，换序会破坏指标可比性。 */
    public static final List<String> DIMS = List.of(
            "humor", "adventure", "expressiveness", "calm", "schedule",
            "ambition", "spending", "warmth", "height_sensitivity", "look_weight");

    /** 各维度在学习排序契合函数里的权重（与 DIMS 同序，与 TrueScore 同构）。 */
    static final double[] RANK_W = {1.2, 1.1, 1.2, 0.8, 0.7, 0.8, 0.7, 1.0, 0.5, 0.5};
    /** 互补型维度：「一个爱说、一个爱听」比「两人都爱说」更合拍。 */
    static final List<String> RANK_COMPLEMENTARY = List.of("expressiveness", "calm", "schedule");

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

    /**
     * 学习排序得分：把自身 10 维权重作为排序系数，对另一方画像权重（近似其特质强度）打分，0-1。
     * 契约：{@code 0.6×base + 0.4×interaction}，与 {@code TrueScore.compatibility} 同构——
     * 偏好×属性，不是「两份画像长得多像」。这是 Step 6 直接并入边权的学习信号，
     * 闭合「反馈→调权重→边权」链路，避免只经 LLM 人格画像间接影响。
     *
     * @param other 另一方的学习画像权重（近似其在各维度上的特质强度）
     * @return 0-1，越高越合拍；自身或对方为中性时退回基准
     */
    public double rankAgainst(TasteWeights other) {
        return 0.6 * baseAgainst(other) + 0.4 * interactionAgainst(other);
    }

    private double baseAgainst(TasteWeights other) {
        double sum = 0.0, wsum = 0.0;
        for (int i = 0; i < DIMS.size(); i++) {
            String d = DIMS.get(i);
            double x = get(d); // 自身对维度 d 的偏好程度（排序系数）
            double y = other.get(d); // 对方在 d 上的特质强度（以对方学习画像权重近似）
            double f = RANK_COMPLEMENTARY.contains(d)
                    ? 1.0 - Math.abs(x - (1.0 - y))
                    : x * y;
            sum += RANK_W[i] * f;
            wsum += RANK_W[i];
        }
        return wsum > 0 ? sum / wsum : 0.0;
    }

    private double interactionAgainst(TasteWeights other) {
        double s = get("humor") * other.get("expressiveness")
                + get("adventure") * other.get("adventure")
                + get("calm") * other.get("calm");
        return s / 3.0;
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
