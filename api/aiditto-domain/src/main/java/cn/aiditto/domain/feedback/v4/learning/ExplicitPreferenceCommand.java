package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;

import java.time.Instant;
import java.util.List;

/**
 * The user's own structured preference change. A model cannot confirm it,
 * and another participant cannot submit it for this user.
 */
public record ExplicitPreferenceCommand(
        String commandId,
        long userId,
        String featureCode,
        String oldValue,
        String newValue,
        Importance importance,
        Operator operator,
        Action action,
        int profileVersion,
        long lifecycleGeneration,
        long authorizationEpoch,
        boolean feedbackProcessingGranted,
        boolean learningGranted,
        List<String> sourceEventIds,
        Instant confirmedAt) {

    public enum Action { CONFIRM, REJECT }

    public ExplicitPreferenceCommand {
        if (commandId == null || !commandId.matches("[a-z][a-z0-9.]{0,40}")) throw new FeedbackRejection("COMMAND_ID");
        if (userId <= 0) throw new FeedbackRejection("USER");
        if (featureCode == null || featureCode.isBlank()) throw new FeedbackRejection("FEATURE");
        if (newValue == null || newValue.isBlank()) throw new FeedbackRejection("VALUE");
        if (importance == null || operator == null || action == null || confirmedAt == null) {
            throw new FeedbackRejection("CONFIRMATION");
        }
        if (profileVersion < 0 || lifecycleGeneration < 1 || authorizationEpoch < 1) {
            throw new FeedbackRejection("GENERATION");
        }
        sourceEventIds = sourceEventIds == null ? List.of() : List.copyOf(sourceEventIds);
    }
}
