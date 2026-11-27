package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.time.Instant;
import java.util.List;

/** A suggested profile change. It is not an active profile and it is not a score. */
public record ProfileLearningProposal(
        String proposalId,
        long userId,
        int profileVersion,
        long lifecycleGeneration,
        String featureCode,
        Section profileSection,
        String oldValue,
        String proposedValue,
        Operator operator,
        Importance importance,
        List<String> sourceEventIds,
        String evidenceType,
        String evidenceStatus,
        String proposalReason,
        String policyVersion,
        Status status,
        Instant createdAt,
        Instant expiresAt) {

    public enum Status {
        PENDING_CONFIRMATION,
        CONFIRMED,
        REJECTED,
        EXPIRED,
        RETRACTED,
        SUPERSEDED,
        STALE_PROFILE,
        INVALID_EVIDENCE
    }

    public ProfileLearningProposal {
        if (proposalId == null || proposalId.isBlank()) throw new FeedbackRejection("PROPOSAL");
        if (userId <= 0 || featureCode == null || proposedValue == null) throw new FeedbackRejection("PROPOSAL");
        if (profileSection == null || operator == null || importance == null || status == null) {
            throw new FeedbackRejection("PROPOSAL");
        }
        if (policyVersion == null || createdAt == null || expiresAt == null) throw new FeedbackRejection("PROPOSAL");
        sourceEventIds = List.copyOf(sourceEventIds);
        if (profileVersion < 0 || lifecycleGeneration < 1) throw new FeedbackRejection("GENERATION");
    }

    public ProfileLearningProposal withStatus(Status next, String evidenceStatus) {
        return new ProfileLearningProposal(proposalId, userId, profileVersion, lifecycleGeneration, featureCode,
                profileSection, oldValue, proposedValue, operator, importance, sourceEventIds, evidenceType,
                evidenceStatus, proposalReason, policyVersion, next, createdAt, expiresAt);
    }
}
