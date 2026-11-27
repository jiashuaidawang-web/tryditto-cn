package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.matching.PairKey;

import java.util.List;
import java.util.Map;

public record GlobalMatchingResult(
        String runId,
        Objective objective,
        String scope,
        boolean optimalForThisGraph,
        boolean fullPopulationGraph,
        List<MatchedPair> pairs,
        List<UnmatchedPerson> unmatched,
        long totalFitCenti,
        long totalOptimizationWeight,
        int safetyViolations,
        Map<String, Integer> unmatchedReasons,
        Map<String, Integer> safetyBreakdown,
        String status,
        String statusDetail,
        long solveMs,
        long planMs,
        long auditMs,
        long wallMs,
        long heapSample) {

    public GlobalMatchingResult {
        pairs = List.copyOf(pairs);
        unmatched = List.copyOf(unmatched);
        unmatchedReasons = Map.copyOf(unmatchedReasons);
        safetyBreakdown = Map.copyOf(safetyBreakdown);
    }

    public int matchedUsers() {
        return pairs.size() * 2;
    }

    public record MatchedPair(
            long lowId,
            long highId,
            double fitScore,
            int fitCenti,
            Double scoreLowToHigh,
            Double scoreHighToLow,
            Double coverageLowToHigh,
            Double coverageHighToLow,
            Double minDirection,
            long optimizationWeight,
            String components,
            boolean recommendationEligible) {
        public PairKey key() {
            return PairKey.of(lowId, highId);
        }
    }

    public record UnmatchedPerson(long userId, String finalReason, List<String> diagnostics) {
        public UnmatchedPerson {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
