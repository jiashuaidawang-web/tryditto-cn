package cn.aiditto.domain.feedback.v4;

import java.util.List;

/**
 * Whether a match's facts may be read by a future learner.
 * This round does not learn, and a single decline is not a profile fact.
 */
public record FeedbackLearningEligibility(
        long matchId,
        boolean matchFactEligible,
        boolean profileUpdateAllowed,
        boolean negativeLabelAllowed,
        List<String> reasons) {

    public FeedbackLearningEligibility {
        reasons = List.copyOf(reasons);
        if (profileUpdateAllowed) throw new FeedbackRejection("LEARNING_NOT_THIS_ROUND");
    }
}
