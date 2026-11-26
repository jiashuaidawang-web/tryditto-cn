package cn.aiditto.domain.relationship;

/**
 * Thresholds fixed before scoring. They are not revised to match a run.
 * A mock or reference pipeline meeting them does not establish live-model quality.
 */
public final class ProfileEvalThresholds {
    public static final int HOLDOUT_MOD = 5;
    public static final int MIN_SAMPLES = 100;
    public static final int MIN_HOLDOUT = 20;
    public static final double PRECISION = 0.95;
    public static final double RECALL = 0.95;
    public static final double CONFUSION_MAX = 0.02;
    public static final double UNKNOWN_RETENTION = 0.95;
    public static final double STRUCTURED_SUCCESS = 1.0;

    private ProfileEvalThresholds() {}

    public static boolean meets(ProfileEval.Metrics metrics) {
        return metrics.samples() >= (metrics.split().equals("holdout") ? MIN_HOLDOUT : 1)
                && metrics.precision() >= PRECISION
                && metrics.recall() >= RECALL
                && metrics.confusionRate() <= CONFUSION_MAX
                && metrics.ungroundedAccepted() == 0
                && metrics.invalidRefAccepted() == 0
                && metrics.unknownRetention() >= UNKNOWN_RETENTION
                && metrics.hardAccepted() == 0
                && metrics.sensitiveLeaks() == 0
                && metrics.structuredSuccessRate() == STRUCTURED_SUCCESS
                && metrics.badFormatSuccessRate() == 0.0;
    }
}
