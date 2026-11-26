package cn.aiditto.domain.relationship;

/**
 * Boundary for a future matcher. Official scoring may read only a semantically supported
 * or user-confirmed feature. This class is not called by the current scorer.
 */
public final class MatchingFeatureGate {
    private MatchingFeatureGate() {}

    public static boolean eligibleForOfficialScore(SemanticSupport support) {
        return support == SemanticSupport.SEMANTIC_SUPPORTED || support == SemanticSupport.USER_CONFIRMED;
    }
}
