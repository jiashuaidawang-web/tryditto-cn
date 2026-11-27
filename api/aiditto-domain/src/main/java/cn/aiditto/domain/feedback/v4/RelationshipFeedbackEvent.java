package cn.aiditto.domain.feedback.v4;

import java.time.Instant;
import java.time.LocalDate;

public record RelationshipFeedbackEvent(
        String eventId,
        String schemaVersion,
        FeedbackEventType eventType,
        long matchId,
        String matchingRunId,
        LocalDate matchingPeriod,
        long actorUserId,
        long counterpartUserId,
        FeedbackFacts.Direction direction,
        FeedbackFacts.SourceType sourceType,
        Instant occurredAt,
        Instant recordedAt,
        String idempotencyKey,
        String causationEventId,
        String supersedesEventId,
        String consentScope,
        long actorProfileVersionAtMatch,
        long counterpartProfileVersionAtMatch,
        String compatibilityPolicyVersion,
        String matchingObjectiveVersion,
        String candidateStrategyVersion,
        String reasonCode,
        FeedbackFacts.EvidenceStatus evidenceStatus) {

    public RelationshipFeedbackEvent {
        if (eventId == null || eventId.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new FeedbackRejection("EVENT_ID");
        }
        if (!FeedbackFacts.SCHEMA.equals(schemaVersion)) throw new FeedbackRejection("SCHEMA");
        if (occurredAt == null || recordedAt == null) throw new FeedbackRejection("TIME");
        if (reasonCode != null && !FeedbackFacts.REASONS.contains(reasonCode)) throw new FeedbackRejection("REASON");
    }
}
