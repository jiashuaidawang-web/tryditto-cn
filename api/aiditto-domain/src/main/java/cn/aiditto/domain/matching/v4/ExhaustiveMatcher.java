package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.matching.PairKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All matchings on a graph of at most 12 users. Used only to check the solver. */
final class ExhaustiveMatcher {
    static final int MAX_USERS = 12;

    record Best(List<PairKey> pairs, long fitCenti, int count) {}

    private ExhaustiveMatcher() {}

    static Best best(GlobalMatchingInput input, List<PreparedEdge> edges, Objective objective) {
        if (input.eligibleUsers().size() > MAX_USERS) throw new IllegalArgumentException("exhaustive limit");
        List<PreparedEdge> sorted = new ArrayList<>();
        for (PreparedEdge edge : edges) if (edge.solverEligible() && edge.fitCenti() > 0) sorted.add(edge);
        if (objective == Objective.G3_QUALITY_TIER) sorted.removeIf(edge -> !admitsQuality(edge));
        sorted.sort(Comparator.comparingLong(PreparedEdge::lowId).thenComparingLong(PreparedEdge::highId));
        Best[] holder = {new Best(List.of(), Long.MIN_VALUE, -1)};
        search(input, sorted, objective, 0, new boolean[input.eligibleUsers().size()], index(input),
                new ArrayList<>(), 0L, 0, holder);
        if (holder[0].count() < 0) return new Best(List.of(), 0, 0);
        return holder[0];
    }

    static boolean admitsQuality(PreparedEdge edge) {
        if (edge.fitCenti() < GlobalBudgets.G3_MIN_FIT_CENTI) return false;
        if (edge.minDirection() != null && edge.minDirection() < GlobalBudgets.G3_MIN_DIRECTION) return false;
        if (edge.coverageLowToHigh() != null && edge.coverageLowToHigh() < GlobalBudgets.G3_MIN_COVERAGE) return false;
        return edge.coverageHighToLow() == null || edge.coverageHighToLow() >= GlobalBudgets.G3_MIN_COVERAGE;
    }

    private static java.util.Map<Long, Integer> index(GlobalMatchingInput input) {
        java.util.Map<Long, Integer> ids = new java.util.LinkedHashMap<>();
        for (var user : input.eligibleUsers()) ids.put(user.userId(), ids.size());
        return ids;
    }

    private static void search(GlobalMatchingInput input, List<PreparedEdge> edges, Objective objective, int from,
                               boolean[] used, java.util.Map<Long, Integer> index, List<PairKey> chosen,
                               long fit, int count, Best[] holder) {
        consider(input, objective, chosen, fit, count, holder);
        for (int i = from; i < edges.size(); i++) {
            PreparedEdge edge = edges.get(i);
            int left = index.get(edge.lowId());
            int right = index.get(edge.highId());
            if (used[left] || used[right]) continue;
            used[left] = true;
            used[right] = true;
            chosen.add(edge.key());
            int bonus = objective == Objective.G4_FAIRNESS
                    ? OptimizationWeight.fairnessBonus(input.unmatchedWeeks(edge.lowId()), input.unmatchedWeeks(edge.highId()))
                    : 0;
            search(input, edges, objective, i + 1, used, index, chosen, fit + edge.fitCenti() + bonus, count + 1, holder);
            chosen.remove(chosen.size() - 1);
            used[left] = false;
            used[right] = false;
        }
    }

    private static void consider(GlobalMatchingInput input, Objective objective, List<PairKey> chosen, long fit, int count, Best[] holder) {
        long rank = switch (objective) {
            case G0_GREEDY, G1_MAX_WEIGHT -> fit;
            case G2_CARDINALITY_THEN_WEIGHT, G3_QUALITY_TIER, G4_FAIRNESS ->
                    count * OptimizationWeight.cardinalityBase(input.eligibleUsers().size()) + fit;
        };
        Best current = holder[0];
        long currentRank = current.count() < 0 ? Long.MIN_VALUE : switch (objective) {
            case G0_GREEDY, G1_MAX_WEIGHT -> current.fitCenti();
            default -> current.count() * OptimizationWeight.cardinalityBase(input.eligibleUsers().size()) + current.fitCenti();
        };
        if (rank > currentRank) {
            holder[0] = new Best(List.copyOf(chosen), fit, count);
        }
    }
}
