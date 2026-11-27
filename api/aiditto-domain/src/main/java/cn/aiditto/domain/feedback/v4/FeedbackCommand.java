package cn.aiditto.domain.feedback.v4;

import java.time.Instant;

/**
 * A request to record one fact. It does not carry profile versions or the other person's identity
 * as something the client may choose.
 */
public record FeedbackCommand(
        FeedbackEventType type,
        long actorUserId,
        FeedbackFacts.SourceType source,
        String idempotencyKey,
        Instant occurredAt,
        String supersedesEventId,
        String reasonCode,
        Long claimedCounterpartUserId) {

    public FeedbackCommand {
        if (type == null || source == null || occurredAt == null) throw new FeedbackRejection("COMMAND");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new FeedbackRejection("IDEMPOTENCY");
        if (reasonCode != null && !FeedbackFacts.REASONS.contains(reasonCode)) throw new FeedbackRejection("REASON");
    }
}
