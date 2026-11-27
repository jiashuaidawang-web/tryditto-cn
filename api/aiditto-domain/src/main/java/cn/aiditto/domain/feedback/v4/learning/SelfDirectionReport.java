package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

import java.time.Instant;

/**
 * A structured direction the user stated about their own preference.
 * It is not an F01 reason code and it is not a confirmed feature value.
 */
public record SelfDirectionReport(
        String eventId,
        long userId,
        String featureCode,
        String direction,
        Instant occurredAt) {

    public SelfDirectionReport {
        if (eventId == null || eventId.isBlank()) throw new FeedbackRejection("EVENT_ID");
        if (userId <= 0) throw new FeedbackRejection("USER");
        if (featureCode == null || direction == null || occurredAt == null) throw new FeedbackRejection("REPORT");
    }
}
