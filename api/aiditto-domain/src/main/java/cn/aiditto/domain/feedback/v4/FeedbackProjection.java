package cn.aiditto.domain.feedback.v4;

import java.time.Instant;
import java.util.List;

/** The effective facts of one match. Unknown and expired are not decline. */
public record FeedbackProjection(
        long matchId,
        boolean published,
        boolean viewedA,
        boolean viewedB,
        FeedbackFacts.ResponseState responseA,
        FeedbackFacts.ResponseState responseB,
        boolean mutualAcceptance,
        FeedbackFacts.ScheduleState schedule,
        FeedbackFacts.AttendanceState attendance,
        FeedbackFacts.ContinueState continueA,
        FeedbackFacts.ContinueState continueB,
        boolean bothContinue,
        boolean contested,
        boolean retracted,
        boolean processingRevoked,
        boolean learningAllowed,
        Instant lastEffectiveAt,
        List<String> unknown) {

    public FeedbackProjection {
        unknown = List.copyOf(unknown);
    }
}
