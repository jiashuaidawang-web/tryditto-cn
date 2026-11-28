package cn.aiditto.shadow;

/** A stored shadow outcome. {@code executable} is never a production publish flag. */
public record ShadowView(
        String runId,
        String status,
        boolean executable,
        String inputHash,
        String resultHash,
        int eligibleUsers,
        int legalEdges,
        int recommendedEdges,
        int matchedPairs,
        int unmatchedUsers,
        int safetyViolations,
        String failureReason,
        String comparisonJson) {
}
