package cn.aiditto.domain.feedback.v4.learning;

/**
 * Explains which evidence may affect a profile. A later map write is not a resolver.
 */
public final class EvidencePriority {
    private EvidencePriority() {}

    public static int rank(LearningSignal signal) {
        if (signal.authorization() == LearningSignal.Authorization.WITHHELD
                || signal.category() == LearningSignal.Category.WITHHELD) {
            return 0;
        }
        return switch (signal.category()) {
            case EXPLICIT_SELF_CORRECTION, EXPLICIT_PARTNER_PREFERENCE -> 4;
            case PREFERENCE_HYPOTHESIS -> 2;
            case BEHAVIORAL_OBSERVATION, AMBIGUOUS_REASON -> 1;
            case RELATIONSHIP_OUTCOME, NO_LEARNING_SIGNAL, COUNTERPART_OPINION, WITHHELD -> 0;
        };
    }

    public static String explain(LearningSignal signal) {
        return switch (rank(signal)) {
            case 4 -> "用户明确选择的偏好";
            case 2 -> "多条来源的待确认假设";
            case 1 -> "单次观察或还不能对应到特征的原因";
            default -> "不进入画像";
        };
    }
}
