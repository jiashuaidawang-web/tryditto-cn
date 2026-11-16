package cn.aiditto.domain.sim;

import cn.aiditto.domain.taste.TasteWeights;

import java.util.List;
import java.util.Random;

/**
 * 兼容度函数 true_score(a,b)：FeedbackOracle 的核心（仿真方案 §3.3）。
 *
 * 刻意**不是简单线性内积**：含跨表面共鸣的交互项（humor_a × expressiveness_b 等）
 * 与硬否决 gate，只用标签重合度的系统在这批样本上必然学不动，指标会立刻暴露。
 *
 * 纯确定性 + 可复现噪声：给定 (a,b,seed) 永远同一结果，不依赖任何 LLM，
 * 从根上避免「系统学会 Oracle 的措辞」这一自证循环。
 */
public final class TrueScore {

    /** 各维度在 base 里的权重（与 DIMS 同序）；互补型维度单独处理。 */
    private static final double[] W = {1.2, 1.1, 1.2, 0.8, 0.7, 0.8, 0.7, 1.0, 0.5, 0.5};
    /** 这些维度「互补」比「相似」更合拍：一个爱说、一个爱听。 */
    private static final List<String> COMPLEMENTARY = List.of("expressiveness", "calm", "schedule");
    /** 人类主观噪声幅度（0-1 尺度）。 */
    public static final double NOISE = 0.05;
    /** 把「偏好×属性」的 0-1 原始分线性映射到以 60 分为中心的 0-100 分。 */
    public static final double SCALE = 2.4;

    private TrueScore() {}

    public static double of(HiddenTruth a, HiddenTruth b, long seed) {
        if (hitsDealbreaker(a, b) || hitsDealbreaker(b, a)) return 0.0;
        double raw = compatibility(a.weights(), b.weights());
        double noise = new Random(mix(a.userId(), b.userId(), seed)).nextGaussian() * NOISE;
        return clamp01(raw * SCALE + noise) * 100.0;
    }

    /** 真值与系统预测共用的同构契合函数：0.6·base + 0.4·交互项。 */
    public static double compatibility(TasteWeights a, TasteWeights b) {
        return 0.6 * base(a, b) + 0.4 * interaction(a, b);
    }

    /** 命中任一真实红线 → 硬否决，不给情面分。 */
    public static boolean hitsDealbreaker(HiddenTruth chooser, HiddenTruth partner) {
        for (String tag : chooser.dealbreakers()) {
            if (partner.attributes().contains(tag)) return true;
        }
        return false;
    }

    /**
     * 0-1：**偏好 × 属性**的加权契合，而不是「两份画像长得多像」。
     * 用户 a 越看重维度 d（x 大）、对方 b 在 d 上越强（y 大），越合拍 → x·y；
     * 互补型维度（表达欲/安静度/作息）则是一个爱说一个爱听 → 1-|x-(1-y)|。
     * 这与 §3.3 的 {@code Σ w_i·f(a_i,b_i)} 及交互项 {@code humor_a × expressiveness_b}
     * 的方向一致：画像权重是「看重程度」，不是「自身属性强度」。
     */
    public static double base(TasteWeights a, TasteWeights b) {
        double sum = 0.0, wsum = 0.0;
        List<String> dims = TasteWeights.DIMS;
        for (int i = 0; i < dims.size(); i++) {
            String d = dims.get(i);
            double x = a.get(d), y = b.get(d);
            double f = COMPLEMENTARY.contains(d)
                    ? 1.0 - Math.abs(x - (1.0 - y))
                    : x * y;
            sum += W[i] * f;
            wsum += W[i];
        }
        return sum / wsum;
    }

    /** 0-1：跨表面共鸣项——攀岩男 × 街潮女也能高分的关键。 */
    public static double interaction(TasteWeights a, TasteWeights b) {
        double s = a.get("humor") * b.get("expressiveness")
                + a.get("adventure") * b.get("adventure")
                + a.get("calm") * b.get("calm");
        return s / 3.0;
    }

    /**
     * 这对人真值里贡献最大/最小的维度（§4.1：reason_tag 从真值的贡献维度映射）。
     * 含 base 的加权契合项与交互项，让「条件都对但没感觉」能落到交互维度上。
     */
    public static String topContributionDim(TasteWeights a, TasteWeights b) {
        return contributionDim(a, b, true);
    }

    public static String worstContributionDim(TasteWeights a, TasteWeights b) {
        return contributionDim(a, b, false);
    }

    private static String contributionDim(TasteWeights a, TasteWeights b, boolean top) {
        String best = TasteWeights.DIMS.get(0);
        double edge = top ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        List<String> dims = TasteWeights.DIMS;
        for (int i = 0; i < dims.size(); i++) {
            String d = dims.get(i);
            double x = a.get(d), y = b.get(d);
            double f = COMPLEMENTARY.contains(d) ? 1.0 - Math.abs(x - (1.0 - y)) : x * y;
            double c = W[i] * f;
            if ("humor".equals(d)) c += 0.4 * a.get("humor") * b.get("expressiveness");
            if ("expressiveness".equals(d)) c += 0.4 * a.get("humor") * b.get("expressiveness");
            if ("adventure".equals(d)) c += 0.4 * a.get("adventure") * b.get("adventure");
            if ("calm".equals(d)) c += 0.4 * a.get("calm") * b.get("calm");
            if (top ? c > edge : c < edge) { edge = c; best = d; }
        }
        return best;
    }

    /** 每个维度对这对人的贡献，用于 Oracle 生成 reason_tag / 学习信号。 */
    public static String topDim(HiddenTruth a, HiddenTruth b) {
        String best = TasteWeights.DIMS.get(0);
        double max = -1;
        for (String d : TasteWeights.DIMS) {
            double v = 1.0 - Math.abs(a.weights().get(d) - b.weights().get(d));
            if (v > max) { max = v; best = d; }
        }
        return best;
    }

    public static String worstDim(HiddenTruth a, HiddenTruth b) {
        String worst = TasteWeights.DIMS.get(0);
        double min = 2;
        for (String d : TasteWeights.DIMS) {
            double v = 1.0 - Math.abs(a.weights().get(d) - b.weights().get(d));
            if (v < min) { min = v; worst = d; }
        }
        return worst;
    }

    private static long mix(long a, long b, long seed) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    private static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
