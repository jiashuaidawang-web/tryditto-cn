package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The whole solver input: a frozen snapshot, no clock, no database (技术方案 §7). The same input and
 * seed always produce the same result.
 *
 * @param weekStart drop Wednesday; ages are measured on this date
 * @param pastPairs every pair ever matched in any week, whatever the outcome
 * @param reportedPairs every pair with a report in either direction
 * @param seed tie-break seed, normally derived from the week id
 * @param manualRun admin "立即匹配"; enables the open-match rule (§7.2 rule 11)
 * @param aiScores Step 6 Agent 精排分（PairKey → 0-100）。**只影响边权与配对排序**，
 *                 不参与 HardFilter 硬约束、风格门槛或展示分；空 Map 时求解器行为与 M2 完全一致。
 *                 这是 Q1「AI 决定配对」与「C 端不能被模型幻觉越过硬约束」两条要求的交界点。
 * @param learnedScores Step 6/8 学习排序分（PairKey → 0-100）：由双方 {@code user_taste.weights}
 *                 直接计算的确定性契合分，是「反馈 → 调权重 → 边权」链路的直接载体（D6 方案 A）。
 *                 与 {@code aiScores} 同契约：只并入边权，不越过硬约束/门槛/展示分；空 Map 时零回归。
 */
public record MatchingInput(
        LocalDate weekStart,
        List<Candidate> candidates,
        Set<PairKey> pastPairs,
        Set<PairKey> reportedPairs,
        Weights weights,
        long seed,
        boolean manualRun,
        Map<PairKey, Integer> aiScores,
        Map<PairKey, Integer> learnedScores) {

    public MatchingInput {
        Objects.requireNonNull(weekStart, "weekStart");
        Objects.requireNonNull(weights, "weights");
        candidates = List.copyOf(candidates);
        pastPairs = Set.copyOf(pastPairs);
        reportedPairs = Set.copyOf(reportedPairs);
        aiScores = aiScores == null ? Map.of() : Map.copyOf(aiScores);
        for (Map.Entry<PairKey, Integer> e : aiScores.entrySet()) {
            int v = e.getValue() == null ? -1 : e.getValue();
            if (v < 0 || v > 100) {
                throw new IllegalArgumentException("aiScore out of [0,100]: " + e.getKey() + "=" + e.getValue());
            }
        }
        learnedScores = learnedScores == null ? Map.of() : Map.copyOf(learnedScores);
        for (Map.Entry<PairKey, Integer> e : learnedScores.entrySet()) {
            int v = e.getValue() == null ? -1 : e.getValue();
            if (v < 0 || v > 100) {
                throw new IllegalArgumentException("learnedScore out of [0,100]: " + e.getKey() + "=" + e.getValue());
            }
        }
        Set<Long> ids = new HashSet<>();
        for (Candidate c : candidates) {
            if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate candidate id " + c.id());
        }
    }

    /** 兼容 M2 及全部既有性质测试：不带 AI 分。 */
    public MatchingInput(LocalDate weekStart, List<Candidate> candidates,
                         Set<PairKey> pastPairs, Set<PairKey> reportedPairs,
                         Weights weights, long seed, boolean manualRun) {
        this(weekStart, candidates, pastPairs, reportedPairs, weights, seed, manualRun, Map.of(), Map.of());
    }

    /** 兼容历史「带 AI 分」构造：不带学习排序分。 */
    public MatchingInput(LocalDate weekStart, List<Candidate> candidates,
                         Set<PairKey> pastPairs, Set<PairKey> reportedPairs,
                         Weights weights, long seed, boolean manualRun, Map<PairKey, Integer> aiScores) {
        this(weekStart, candidates, pastPairs, reportedPairs, weights, seed, manualRun, aiScores, Map.of());
    }

    public static MatchingInput of(LocalDate weekStart, List<Candidate> candidates, long seed) {
        return new MatchingInput(weekStart, candidates, Set.of(), Set.of(), Weights.W1, seed, false);
    }

    public MatchingInput withPastPairs(Set<PairKey> past) {
        return new MatchingInput(weekStart, candidates, past, reportedPairs, weights, seed, manualRun, aiScores, learnedScores);
    }

    public MatchingInput withSeed(long s) {
        return new MatchingInput(weekStart, candidates, pastPairs, reportedPairs, weights, s, manualRun, aiScores, learnedScores);
    }

    public MatchingInput withAiScores(Map<PairKey, Integer> scores) {
        return new MatchingInput(weekStart, candidates, pastPairs, reportedPairs, weights, seed, manualRun, scores, learnedScores);
    }

    public MatchingInput withLearnedScores(Map<PairKey, Integer> scores) {
        return new MatchingInput(weekStart, candidates, pastPairs, reportedPairs, weights, seed, manualRun, aiScores, scores);
    }
}
