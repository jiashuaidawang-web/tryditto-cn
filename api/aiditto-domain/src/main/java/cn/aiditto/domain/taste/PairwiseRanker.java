package cn.aiditto.domain.taste;

import java.util.List;

/**
 * Pairwise learning-to-rank：从「成功对 vs 失败对」的成对比较里，学一组全局维度权重，
 * 直接用它给候选对打分。区别于现有 {@link TasteWeights#apply} 的「每次只调一个人格的一个维度」——
 * 这里学的是「哪些维度真正决定契合」，是对排序函数本身的学习（D6 方案 B）。
 *
 * 设计要点：
 *  - 特征与 {@code TrueScore.compatibility} 同构（10 维贡献 + 交互项），但**权重从反馈数据学**，不硬编码；
 *  - pairwise logistic：观察到 (win, lose) 时，若当前评分把 lose 排前了，就朝 win 的方向更新权重；
 *  - 防过拟合：权重夹在 [0, 3]、L2 正则、样本数不足时收敛到初始化（TrueScore 的先验结构）；
 *  - 纯 Java、无 IO、无模型调用，可单测、可回放；只在 learnedScores 通道输出分数，
 *    不碰硬约束 / 门槛 / 展示分。
 *
 * 注意：这是阶段 B 实验组件，验证「把学习幅度做大」是否可行；生产接入需另行闸门。
 */
public final class PairwiseRanker {
    private static final List<String> DIMS = TasteWeights.DIMS;
    private static final List<String> COMPLEMENTARY = List.of("expressiveness", "calm", "schedule");
    /** 初始化用的维度权重先验（与 TrueScore.W 同构），数据不足时退回它。 */
    private static final double[] PRIOR = {1.2, 1.1, 1.2, 0.8, 0.7, 0.8, 0.7, 1.0, 0.5, 0.5};
    private static final double PRIOR_INTERACTION = 0.4 / 0.6; // TrueScore 里交互项 0.4 vs base 0.6

    private final double[] w = new double[DIMS.size()];
    private double wInteraction;
    private final double lr;
    private final double l2;
    private int observations;

    public PairwiseRanker() {
        this(0.05, 0.001);
    }

    public PairwiseRanker(double lr, double l2) {
        this.lr = lr;
        this.l2 = l2;
        reset();
    }

    /** 回到先验（新周批 / 样本不足时使用）。 */
    public void reset() {
        System.arraycopy(PRIOR, 0, w, 0, w.length);
        wInteraction = PRIOR_INTERACTION;
        observations = 0;
    }

    public int observations() {
        return observations;
    }

    /** 提取一对在各维度上的贡献（与 TrueScore.base 的 f 同构），归一化到贡献向量。 */
    private double[] features(TasteWeights a, TasteWeights b) {
        double[] f = new double[DIMS.size() + 1]; // 最后一维是交互项
        for (int i = 0; i < DIMS.size(); i++) {
            String d = DIMS.get(i);
            double x = a.get(d), y = b.get(d);
            f[i] = COMPLEMENTARY.contains(d) ? 1.0 - Math.abs(x - (1.0 - y)) : x * y;
        }
        f[DIMS.size()] = (a.get("humor") * b.get("expressiveness")
                + a.get("adventure") * b.get("adventure")
                + a.get("calm") * b.get("calm")) / 3.0;
        return f;
    }

    /** 原始线性分（未归一化）；排序单调等价 logistic，省一次指数运算。 */
    private double raw(TasteWeights a, TasteWeights b) {
        double[] f = features(a, b);
        double s = 0;
        for (int i = 0; i < DIMS.size(); i++) s += w[i] * f[i];
        return s + wInteraction * f[DIMS.size()];
    }

    /** 给一对打分，映射到 0-100（接 learnedScores 通道）。 */
    public int score(TasteWeights a, TasteWeights b) {
        // raw 量级约 [0, 2.2]；用 sigmoid-ish 压缩到 0-1 再乘 100，避免和 AI 分(0-100)量纲冲突
        double s = raw(a, b);
        double p = 1.0 / (1.0 + Math.exp(-1.5 * (s - 0.9)));
        return (int) Math.round(Math.max(0, Math.min(100, p * 100)));
    }

    /**
     * 观察一次成对比较：win 是成功的对，lose 是失败的对。
     * pairwise logistic 梯度：若当前把 lose 排前（score_win &lt; score_lose），朝 win 更新权重。
     */
    public void observe(TasteWeights winA, TasteWeights winB, TasteWeights loseA, TasteWeights loseB) {
        double diff = raw(winA, winB) - raw(loseA, loseB);
        double g = 1.0 / (1.0 + Math.exp(diff)); // = 1 - sigmoid(diff)，排序越错越大
        if (g < 1e-6) return;                    // 已排对，不动
        double[] fw = features(winA, winB), fl = features(loseA, loseB);
        for (int i = 0; i < DIMS.size(); i++) {
            double next = w[i] + lr * g * (fw[i] - fl[i]) - lr * l2 * w[i];
            w[i] = Math.max(0.0, Math.min(3.0, next));
        }
        double ni = wInteraction + lr * g * (fw[DIMS.size()] - fl[DIMS.size()]) - lr * l2 * wInteraction;
        wInteraction = Math.max(0.0, Math.min(3.0, ni));
        observations++;
    }

    /** 调试/审计用：当前学到的权重副本。 */
    public double[] weights() {
        double[] copy = new double[w.length + 1];
        System.arraycopy(w, 0, copy, 0, w.length);
        copy[w.length] = wInteraction;
        return copy;
    }
}
