package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.matching.PairKey;

import java.util.List;

/**
 * One undirected pair. {@code lowId < highId}. Scores point from low to high and back.
 * A math edge is only for solver checks. It is not a population recommendation.
 */
public record PreparedEdge(
        long lowId,
        long highId,
        long versionLow,
        long versionHigh,
        long generationLow,
        long generationHigh,
        String strategy,
        CompatibilityDecision decision,
        Double scoreLowToHigh,
        Double scoreHighToLow,
        Double coverageLowToHigh,
        Double coverageHighToLow,
        Double minDirection,
        Double fitScore,
        int fitCenti,
        boolean solverEligible,
        boolean mathAdmitted,
        String exclusion,
        List<String> diagnostics) {

    public PreparedEdge {
        if (lowId >= highId) throw new IllegalArgumentException("low < high");
        diagnostics = List.copyOf(diagnostics);
        if (strategy == null || exclusion == null) throw new IllegalArgumentException("edge");
    }

    public PairKey key() {
        return PairKey.of(lowId, highId);
    }

    public static PreparedEdge math(long low, long high, double fit) {
        int centi = OptimizationWeight.fitCenti(fit);
        boolean admitted = centi > 0;
        return new PreparedEdge(Math.min(low, high), Math.max(low, high), 1, 1, 1, 1, "MATH", null,
                fit, fit, fit / 100.0, fit / 100.0, fit, admitted ? fit : null, centi, admitted, true,
                admitted ? "" : "WEIGHT_NON_POSITIVE", List.of());
    }
}
