package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One published match, frozen at publication.
 * Later profile edits are not copied into these fields. Responses are labels, not features.
 */
public record MatchingObservation(
        String observationId,
        long matchId,
        String matchingRunId,
        LocalDate matchingPeriod,
        long userA,
        long userB,
        int profileVersionA,
        int profileVersionB,
        long lifecycleGenerationA,
        long lifecycleGenerationB,
        String candidateStrategyVersion,
        String compatibilityPolicyVersion,
        String globalObjectiveVersion,
        String scoreFormula,
        int scoreAB,
        int scoreBA,
        double coverageAB,
        double coverageBA,
        int reciprocalScore,
        Instant publishedAt,
        Instant responseDeadline,
        Instant dateDeadline,
        Instant followupDeadline,
        Response responseA,
        Response responseB,
        boolean viewed,
        boolean dateArranged,
        Attendance attendance,
        Continue continueA,
        Continue continueB,
        Access feedbackAuthorization,
        boolean retracted,
        List<String> sourceEventIds,
        Instant observedAt) {

    public enum Response { UNKNOWN, ACCEPTED, DECLINED, EXPIRED, NO_RESPONSE }
    public enum Attendance { UNKNOWN, NOT_APPLICABLE, ONE_ATTENDED, BOTH_ATTENDED, ONE_ABSENT, BOTH_ABSENT, CONTESTED }
    public enum Continue { UNKNOWN, NOT_APPLICABLE, EXPRESSED, DECLINED }
    public enum Access { OPEN, WITHHELD, REVOKED }

    public MatchingObservation {
        if (observationId == null || observationId.isBlank() || matchingRunId == null || matchingPeriod == null) {
            throw new FeedbackRejection("OBSERVATION");
        }
        if (userA <= 0 || userB <= 0 || userA == userB || matchId <= 0) throw new FeedbackRejection("OBSERVATION");
        if (profileVersionA < 0 || profileVersionB < 0) throw new FeedbackRejection("OBSERVATION");
        if (lifecycleGenerationA < 1 || lifecycleGenerationB < 1) throw new FeedbackRejection("GENERATION");
        if (candidateStrategyVersion == null || compatibilityPolicyVersion == null || globalObjectiveVersion == null
                || scoreFormula == null) {
            throw new FeedbackRejection("STRATEGY_VERSION");
        }
        if (scoreAB < 0 || scoreAB > 100 || scoreBA < 0 || scoreBA > 100 || reciprocalScore < 0 || reciprocalScore > 100) {
            throw new FeedbackRejection("SCORE");
        }
        if (!Double.isFinite(coverageAB) || !Double.isFinite(coverageBA) || coverageAB < 0 || coverageAB > 1
                || coverageBA < 0 || coverageBA > 1) {
            throw new FeedbackRejection("COVERAGE");
        }
        if (publishedAt == null || responseDeadline == null || dateDeadline == null || followupDeadline == null
                || observedAt == null) {
            throw new FeedbackRejection("WINDOW");
        }
        if (responseA == null || responseB == null || attendance == null || continueA == null || continueB == null
                || feedbackAuthorization == null) {
            throw new FeedbackRejection("LABEL");
        }
        sourceEventIds = List.copyOf(sourceEventIds == null ? List.of() : sourceEventIds);
    }

    /** Stable trace of the frozen row. Later profile versions are not written into it. */
    public String canonical() {
        return observationId + "|" + matchId + "|" + matchingRunId + "|" + matchingPeriod
                + "|a=" + userA + "|b=" + userB
                + "|pa=" + profileVersionA + "|pb=" + profileVersionB
                + "|ga=" + lifecycleGenerationA + "|gb=" + lifecycleGenerationB
                + "|" + candidateStrategyVersion + "|" + compatibilityPolicyVersion
                + "|" + globalObjectiveVersion + "|" + scoreFormula
                + "|ab=" + scoreAB + "|ba=" + scoreBA
                + "|cab=" + coverageAB + "|cba=" + coverageBA + "|r=" + reciprocalScore
                + "|pub=" + publishedAt
                + "|ra=" + responseA + "|rb=" + responseB
                + "|view=" + viewed + "|arranged=" + dateArranged
                + "|att=" + attendance + "|ca=" + continueA + "|cb=" + continueB
                + "|auth=" + feedbackAuthorization + "|retracted=" + retracted
                + "|src=" + String.join(",", sourceEventIds)
                + "|rd=" + responseDeadline
                + "|dd=" + dateDeadline
                + "|fd=" + followupDeadline
                + "|at=" + observedAt;
    }

    /** The versions stored at publication. A newer profile version is not substituted. */
    public MatchingObservation historical(int laterProfileA, int laterProfileB) {
        if (laterProfileA == profileVersionA && laterProfileB == profileVersionB) return this;
        return this;
    }

    public PredictionFeatures features() {
        return new PredictionFeatures(scoreAB, scoreBA, coverageAB, coverageBA, reciprocalScore,
                profileVersionA, profileVersionB, candidateStrategyVersion, compatibilityPolicyVersion,
                globalObjectiveVersion, scoreFormula);
    }

    public record PredictionFeatures(int scoreAB, int scoreBA, double coverageAB, double coverageBA, int reciprocalScore,
                                     int profileVersionA, int profileVersionB, String candidateStrategyVersion,
                                     String compatibilityPolicyVersion, String globalObjectiveVersion, String scoreFormula) {}
}
