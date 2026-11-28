package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackProjection;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds one observation from the server snapshot and the effective event projection.
 * Callers do not supply accept, attendance, or continue labels. Synthetic fixtures use the record constructor.
 */
public final class ObservationBuilder {
    private ObservationBuilder() {}

    public static MatchingObservation fromLedger(MatchFeedbackContext context, FeedbackLedger ledger, MatchScoreSnapshot scores,
                                                 ObservationWindow window, String observationId, Instant observedAt, Instant now,
                                                 List<String> sourceEventIds) {
        if (context == null || ledger == null || scores == null || window == null || now == null || observedAt == null) {
            throw new FeedbackRejection("OBSERVATION");
        }
        if (observationId == null || observationId.isBlank() || observationId.length() > 64) {
            throw new FeedbackRejection("OBSERVATION");
        }
        if (ledger.context().matchId() != context.matchId()
                || !ledger.context().matchingRunId().equals(context.matchingRunId())
                || !ledger.context().matchingPeriod().equals(context.matchingPeriod())
                || ledger.context().userA() != context.userA()
                || ledger.context().userB() != context.userB()) {
            throw new FeedbackRejection("MATCH_IDENTITY");
        }
        if (!context.feedbackConsent(context.userA()) || !context.feedbackConsent(context.userB())) {
            throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        }
        List<RelationshipFeedbackEvent> events = ledger.events();
        Set<String> superseded = new HashSet<>();
        for (RelationshipFeedbackEvent event : events) {
            if (event.supersedesEventId() != null && !event.supersedesEventId().isBlank()) {
                superseded.add(event.supersedesEventId());
            }
        }
        List<String> effective = new ArrayList<>();
        RelationshipFeedbackEvent published = null;
        for (RelationshipFeedbackEvent event : events) {
            if (event.matchId() != context.matchId() || !event.matchingRunId().equals(context.matchingRunId())
                    || !event.matchingPeriod().equals(context.matchingPeriod())) {
                throw new FeedbackRejection("MATCH_IDENTITY");
            }
            if (superseded.contains(event.eventId())) continue;
            checkEvent(context, event);
            if (event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED) throw new FeedbackRejection("RETRACTED_SOURCE");
            if (event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED) {
                throw new FeedbackRejection("PROCESSING_REVOKED");
            }
            if (event.eventType() == FeedbackEventType.MATCH_PUBLISHED) published = event;
            effective.add(event.eventId());
        }
        if (published == null) throw new FeedbackRejection("NOT_PUBLISHED");
        if (sourceEventIds == null || !new HashSet<>(sourceEventIds).equals(new HashSet<>(effective))) {
            throw new FeedbackRejection("SOURCE_MISMATCH");
        }
        Instant publishedAt = published.recordedAt();
        if (observedAt.isBefore(publishedAt) || observedAt.isAfter(now)) throw new FeedbackRejection("WINDOW");
        if (!window.responseDeadline().isAfter(publishedAt) || !window.dateDeadline().isAfter(publishedAt)
                || !window.followupDeadline().isAfter(publishedAt)) {
            throw new FeedbackRejection("WINDOW");
        }
        FeedbackProjection projection = ledger.project();
        boolean viewed = projection.viewedA() || projection.viewedB();
        return new MatchingObservation(observationId, context.matchId(), context.matchingRunId(), context.matchingPeriod(),
                context.userA(), context.userB(), version(context.profileVersionA()), version(context.profileVersionB()),
                context.lifecycleGenerationA(), context.lifecycleGenerationB(), context.candidateStrategyVersion(),
                context.compatibilityPolicyVersion(), context.matchingObjectiveVersion(), scores.formula(),
                scores.scoreAB(), scores.scoreBA(), scores.coverageAB(), scores.coverageBA(), scores.reciprocalScore(),
                publishedAt, window.responseDeadline(), window.dateDeadline(), window.followupDeadline(),
                response(projection.responseA()), response(projection.responseB()), viewed,
                projection.schedule() == FeedbackFacts.ScheduleState.SCHEDULED,
                attendance(projection), cont(projection.continueA()), cont(projection.continueB()),
                Access.OPEN, false, List.copyOf(effective), observedAt);
    }

    private static void checkEvent(MatchFeedbackContext context, RelationshipFeedbackEvent event) {
        if (!event.compatibilityPolicyVersion().equals(context.compatibilityPolicyVersion())
                || !event.matchingObjectiveVersion().equals(context.matchingObjectiveVersion())
                || !event.candidateStrategyVersion().equals(context.candidateStrategyVersion())) {
            throw new FeedbackRejection("STRATEGY_VERSION");
        }
        if (event.actorUserId() == 0) {
            if (event.direction() != FeedbackFacts.Direction.JOINT) throw new FeedbackRejection("DIRECTION");
            if (event.actorProfileVersionAtMatch() != context.profileVersionA()
                    || event.counterpartProfileVersionAtMatch() != context.profileVersionB()) {
                throw new FeedbackRejection("SNAPSHOT");
            }
            return;
        }
        FeedbackFacts.Direction expected = event.actorUserId() == context.userA()
                ? FeedbackFacts.Direction.A_TO_B : FeedbackFacts.Direction.B_TO_A;
        if (event.direction() != expected) throw new FeedbackRejection("DIRECTION");
        if (event.actorProfileVersionAtMatch() != context.profileVersion(event.actorUserId())
                || event.counterpartProfileVersionAtMatch() != context.profileVersion(event.counterpartUserId())) {
            throw new FeedbackRejection("SNAPSHOT");
        }
    }

    private static int version(long version) {
        if (version < 1 || version > Integer.MAX_VALUE) throw new FeedbackRejection("SNAPSHOT");
        return (int) version;
    }

    private static Response response(FeedbackFacts.ResponseState state) {
        return switch (state) {
            case ACCEPTED -> Response.ACCEPTED;
            case DECLINED -> Response.DECLINED;
            case EXPIRED -> Response.EXPIRED;
            case UNKNOWN, NOT_APPLICABLE, NOT_OCCURRED, WITHHELD -> Response.UNKNOWN;
        };
    }

    private static Attendance attendance(FeedbackProjection projection) {
        return switch (projection.attendance()) {
            case BOTH_REPORTED_ATTENDED -> Attendance.BOTH_ATTENDED;
            case BOTH_REPORTED_NOT_ATTENDED -> Attendance.BOTH_ABSENT;
            case ONE_REPORTED_ATTENDED -> Attendance.ONE_ATTENDED;
            case ONE_REPORTED_NOT_ATTENDED -> Attendance.ONE_ABSENT;
            case CONTESTED -> Attendance.CONTESTED;
            case NOT_APPLICABLE, NOT_OCCURRED -> Attendance.NOT_APPLICABLE;
            case UNKNOWN, WITHHELD -> Attendance.UNKNOWN;
        };
    }

    private static Continue cont(FeedbackFacts.ContinueState state) {
        return switch (state) {
            case EXPRESSED -> Continue.EXPRESSED;
            case DECLINED -> Continue.DECLINED;
            case NOT_APPLICABLE -> Continue.NOT_APPLICABLE;
            case UNKNOWN, WITHHELD -> Continue.UNKNOWN;
        };
    }
}
