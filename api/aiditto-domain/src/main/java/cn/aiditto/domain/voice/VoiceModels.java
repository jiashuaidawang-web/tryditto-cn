package cn.aiditto.domain.voice;

import java.util.List;

/**
 * 自适应情景采集引擎的核心值对象。纯数据 + 纯函数，无 IO、无模型调用，便于单测。
 *
 * 设计灵魂（创始人 2026-11-18）：让分身像本人 = 攒下「本人怎么说话」的资产。
 * 做法是 LLM 动态出题、按「预测用户最可能选什么」给选项排序，用户一选，
 * 「预测 vs 实际」的差距就成了校准信号，下一题更准，最终推断出真实性格与语气。
 */
public final class VoiceModels {
    private VoiceModels() {}

    /** 一道情景题的一个选项。predictRank=1 表示系统预测用户最可能选这个（即放在 A 位）。 */
    public record Option(String key, String text, String trait, int predictRank) {
        public Option {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
            if (text == null || text.isBlank()) throw new IllegalArgumentException("text");
            trait = trait == null ? "" : trait.strip();
            if (predictRank < 1) predictRank = 99;
        }
    }

    /** LLM 生成的一道情景题。 */
    public record Scenario(String scenario, List<Option> options) {
        public Scenario {
            if (scenario == null || scenario.isBlank()) throw new IllegalArgumentException("scenario");
            options = options == null ? List.of() : List.copyOf(options);
        }
        public boolean valid() {
            return !scenario.isBlank() && options.size() >= 2 && options.size() <= 6;
        }
    }

    /**
     * 一次作答的结果。predictHit = 用户选的是不是系统预测第一（A 位）——
     * 这是「模型对用户的理解准不准」的直接信号；连续命中说明画像收敛，频繁不中说明需要修正。
     */
    public record AnswerOutcome(
            String chosenKey,
            boolean isCustom,
            boolean predictHit,
            String traitSignal) {
        public static AnswerOutcome ofCustom() {
            return new AnswerOutcome(null, true, false, "custom");
        }
    }

    /** 由「题 + 用户选的 key」算出校准信号。纯函数。 */
    public static AnswerOutcome evaluate(Scenario q, String chosenKey) {
        if (chosenKey == null || chosenKey.isBlank()) return AnswerOutcome.ofCustom();
        String upper = chosenKey.trim().toUpperCase();
        String chosenTrait = "";
        boolean hit = false;
        for (Option o : q.options()) {
            if (o.key().equalsIgnoreCase(upper)) {
                chosenTrait = o.trait();
                hit = o.predictRank() == 1;
                break;
            }
        }
        return new AnswerOutcome(upper, false, hit, chosenTrait);
    }

    /** 当前预测命中率：系统对用户理解的可信度代理。 */
    public static double calibrationRate(List<AnswerOutcome> answers) {
        if (answers == null || answers.isEmpty()) return 0.0;
        long hits = answers.stream().filter(a -> !a.isCustom() && a.predictHit()).count();
        long decided = answers.stream().filter(a -> !a.isCustom()).count();
        return decided == 0 ? 0.0 : (double) hits / decided;
    }

    /**
     * 是否应当停止出题。用户主动停止由外部决定；这里是自动收敛判断——
     * 答够 minSteps 且连续命中足够多（系统已能稳定预测此人），即可推断人格收尾。
     */
    public static boolean converged(List<AnswerOutcome> answers, int minSteps, int streakNeeded) {
        if (answers == null || answers.size() < minSteps) return false;
        int streak = 0;
        for (int i = answers.size() - 1; i >= 0; i--) {
            AnswerOutcome a = answers.get(i);
            if (a.isCustom()) break;             // 自定义输入是强信号，继续挖掘
            if (a.predictHit()) streak++; else break;
        }
        return streak >= streakNeeded;
    }
}
