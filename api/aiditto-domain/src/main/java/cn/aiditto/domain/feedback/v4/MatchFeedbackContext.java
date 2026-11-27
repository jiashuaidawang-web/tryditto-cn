package cn.aiditto.domain.feedback.v4;

import java.time.LocalDate;

/**
 * The snapshot saved with the match. Profile versions come from that snapshot.
 * A client command cannot replace them.
 */
public record MatchFeedbackContext(
        long matchId,
        String matchingRunId,
        LocalDate matchingPeriod,
        long userA,
        long userB,
        boolean published,
        long profileVersionA,
        long profileVersionB,
        String compatibilityPolicyVersion,
        String matchingObjectiveVersion,
        String candidateStrategyVersion,
        boolean feedbackConsentA,
        boolean feedbackConsentB) {

    public MatchFeedbackContext {
        if (matchId <= 0 || userA <= 0 || userB <= 0 || userA == userB) throw new FeedbackRejection("MATCH_IDENTITY");
        if (matchingRunId == null || matchingRunId.isBlank()) throw new FeedbackRejection("RUN");
        if (matchingPeriod == null) throw new FeedbackRejection("PERIOD");
        if (profileVersionA < 1 || profileVersionB < 1) throw new FeedbackRejection("SNAPSHOT_VERSION");
        if (compatibilityPolicyVersion == null || matchingObjectiveVersion == null || candidateStrategyVersion == null) {
            throw new FeedbackRejection("STRATEGY_VERSION");
        }
    }

    public boolean participant(long userId) {
        return userId == userA || userId == userB;
    }

    public long counterpart(long userId) {
        if (userId == userA) return userB;
        if (userId == userB) return userA;
        throw new FeedbackRejection("NOT_PARTICIPANT");
    }

    public boolean feedbackConsent(long userId) {
        if (userId == userA) return feedbackConsentA;
        if (userId == userB) return feedbackConsentB;
        return false;
    }

    public long profileVersion(long userId) {
        if (userId == userA) return profileVersionA;
        if (userId == userB) return profileVersionB;
        throw new FeedbackRejection("NOT_PARTICIPANT");
    }
}
