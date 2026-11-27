package cn.aiditto.domain.matching.v4;

public record GraphStats(
        int users,
        long consideredPairs,
        int diagnosticRetained,
        int solverEdges,
        int hardBlocked,
        int notEligible,
        int eligible,
        int truncated,
        int withdrawnPairs,
        int consentPairs,
        int historicalPairs,
        long profileMs,
        long filterMs,
        long scoreMs,
        long buildMs) {
}
