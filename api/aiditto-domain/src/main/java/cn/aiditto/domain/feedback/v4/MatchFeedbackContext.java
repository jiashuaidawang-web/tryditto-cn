package cn.aiditto.domain.feedback.v4;

import java.time.LocalDate;

/**
 * Server snapshot for one match. Tests build it with {@link #experiment}. A production
 * snapshot is not constructable in this round.
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
        boolean feedbackConsentB,
        long lifecycleGenerationA,
        long lifecycleGenerationB,
        long authorizationEpochA,
        long authorizationEpochB,
        boolean learningConsentA,
        boolean learningConsentB,
        Origin origin) {

    public enum Origin { SYNTHETIC, PRODUCTION }

    public MatchFeedbackContext {
        if (origin == Origin.PRODUCTION) throw new FeedbackRejection("PRODUCTION_CONTEXT_NOT_WIRED");
        if (origin == null) throw new FeedbackRejection("ORIGIN");
        if (matchId <= 0 || userA <= 0 || userB <= 0 || userA == userB) throw new FeedbackRejection("MATCH_IDENTITY");
        if (matchingRunId == null || matchingRunId.isBlank()) throw new FeedbackRejection("RUN");
        if (matchingPeriod == null) throw new FeedbackRejection("PERIOD");
        if (profileVersionA < 1 || profileVersionB < 1) throw new FeedbackRejection("SNAPSHOT_VERSION");
        if (lifecycleGenerationA < 1 || lifecycleGenerationB < 1) throw new FeedbackRejection("GENERATION");
        if (authorizationEpochA < 1 || authorizationEpochB < 1) throw new FeedbackRejection("AUTHORIZATION_EPOCH");
        if (compatibilityPolicyVersion == null || matchingObjectiveVersion == null || candidateStrategyVersion == null) {
            throw new FeedbackRejection("STRATEGY_VERSION");
        }
    }

    /** Isolated experiment snapshot. Learning consent is stored separately from feedback processing. */
    public static MatchFeedbackContext experiment(long matchId, String matchingRunId, LocalDate matchingPeriod,
                                                  long userA, long userB, boolean published,
                                                  long profileVersionA, long profileVersionB,
                                                  String compatibilityPolicyVersion, String matchingObjectiveVersion,
                                                  String candidateStrategyVersion,
                                                  boolean feedbackConsentA, boolean feedbackConsentB) {
        return experiment(matchId, matchingRunId, matchingPeriod, userA, userB, published, profileVersionA, profileVersionB,
                compatibilityPolicyVersion, matchingObjectiveVersion, candidateStrategyVersion,
                feedbackConsentA, feedbackConsentB, feedbackConsentA, feedbackConsentB);
    }

    public static MatchFeedbackContext experiment(long matchId, String matchingRunId, LocalDate matchingPeriod,
                                                  long userA, long userB, boolean published,
                                                  long profileVersionA, long profileVersionB,
                                                  String compatibilityPolicyVersion, String matchingObjectiveVersion,
                                                  String candidateStrategyVersion,
                                                  boolean feedbackConsentA, boolean feedbackConsentB,
                                                  boolean learningConsentA, boolean learningConsentB) {
        return new MatchFeedbackContext(matchId, matchingRunId, matchingPeriod, userA, userB, published,
                profileVersionA, profileVersionB, compatibilityPolicyVersion, matchingObjectiveVersion,
                candidateStrategyVersion, feedbackConsentA, feedbackConsentB,
                1, 1, 1, 1, learningConsentA, learningConsentB, Origin.SYNTHETIC);
    }

    public static MatchFeedbackContext production() {
        throw new FeedbackRejection("PRODUCTION_CONTEXT_NOT_WIRED");
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

    public boolean learningConsent(long userId) {
        if (userId == userA) return learningConsentA;
        if (userId == userB) return learningConsentB;
        return false;
    }

    public long profileVersion(long userId) {
        if (userId == userA) return profileVersionA;
        if (userId == userB) return profileVersionB;
        throw new FeedbackRejection("NOT_PARTICIPANT");
    }

    public long lifecycleGeneration(long userId) {
        if (userId == userA) return lifecycleGenerationA;
        if (userId == userB) return lifecycleGenerationB;
        throw new FeedbackRejection("NOT_PARTICIPANT");
    }

    public long authorizationEpoch(long userId) {
        if (userId == userA) return authorizationEpochA;
        if (userId == userB) return authorizationEpochB;
        throw new FeedbackRejection("NOT_PARTICIPANT");
    }
}
