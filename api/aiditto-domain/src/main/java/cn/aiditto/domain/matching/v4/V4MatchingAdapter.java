package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.matching.BlossomMatcher;
import cn.aiditto.domain.matching.Edge;
import cn.aiditto.domain.matching.GreedySolver;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.ScoreBreakdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends V4 weights through the existing solvers. The {@link ScoreBreakdown} is unused zeros.
 * {@link BlossomMatcher} reads {@link Edge#weight()} only, and that weight is the V4 optimization weight.
 */
final class V4MatchingAdapter {
    private static final ScoreBreakdown UNUSED = new ScoreBreakdown(0, 0, 0, 0, 0, 0, 0, 0);

    private V4MatchingAdapter() {}

    static List<PairKey> match(GlobalMatchingInput input, List<PreparedEdge> edges, Objective objective, boolean greedy) {
        Map<PairKey, Long> weights = new LinkedHashMap<>();
        List<PreparedEdge> sorted = new ArrayList<>();
        for (PreparedEdge edge : edges) {
            if (!edge.solverEligible() || edge.fitCenti() <= 0) continue;
            sorted.add(edge);
        }
        sorted.sort(Comparator.comparingLong(PreparedEdge::lowId).thenComparingLong(PreparedEdge::highId));
        List<Edge> carrier = new ArrayList<>();
        for (PreparedEdge edge : sorted) {
            int fairness = objective == Objective.G4_FAIRNESS
                    ? OptimizationWeight.fairnessBonus(input.unmatchedWeeks(edge.lowId()), input.unmatchedWeeks(edge.highId()))
                    : 0;
            long weight = OptimizationWeight.edgeWeight(objective, input.eligibleUsers().size(), edge.fitCenti(), fairness);
            if (weight <= 0) continue;
            if (weights.put(edge.key(), weight) != null) throw new IllegalArgumentException("duplicate " + edge.key());
            carrier.add(new Edge(edge.lowId(), edge.highId(), UNUSED, weight));
        }
        List<Edge> chosen = greedy ? new GreedySolver().match(carrier) : new BlossomMatcher().match(carrier);
        List<PairKey> out = new ArrayList<>();
        for (Edge edge : chosen) {
            if (!weights.containsKey(edge.key())) throw new IllegalStateException("solver returned an unknown edge " + edge.key());
            if (edge.weight() <= 0) throw new IllegalStateException("solver returned a non-positive edge " + edge.key());
            out.add(edge.key());
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
