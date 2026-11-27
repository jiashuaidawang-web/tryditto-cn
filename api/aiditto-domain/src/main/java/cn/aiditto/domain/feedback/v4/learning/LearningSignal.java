package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

import java.time.Instant;

/**
 * One classified reading of one event or one self-report.
 * A signal is not a profile change.
 */
public record LearningSignal(
        String sourceEventId,
        long userId,
        Category category,
        Direction direction,
        SourceTrust sourceTrust,
        String learnableTarget,
        Scope scope,
        Authorization authorization,
        Instant validUntil,
        EvidenceState evidenceState) {

    public enum Category {
        EXPLICIT_SELF_CORRECTION,
        EXPLICIT_PARTNER_PREFERENCE,
        BEHAVIORAL_OBSERVATION,
        RELATIONSHIP_OUTCOME,
        PREFERENCE_HYPOTHESIS,
        COUNTERPART_OPINION,
        AMBIGUOUS_REASON,
        NO_LEARNING_SIGNAL,
        WITHHELD
    }

    public enum Direction { POSITIVE, NEGATIVE, NONE }

    public enum SourceTrust { SELF, COUNTERPART, SYSTEM }

    public enum Scope { SELF_PROFILE, PARTNER_PREFERENCE, RELATIONSHIP, NONE }

    public enum Authorization { OPEN, WITHHELD }

    public enum EvidenceState { USABLE, NOT_USABLE }

    public LearningSignal {
        if (sourceEventId == null || sourceEventId.isBlank()) throw new FeedbackRejection("EVENT_ID");
        if (userId <= 0) throw new FeedbackRejection("USER");
        if (category == null || direction == null || sourceTrust == null || scope == null
                || authorization == null || evidenceState == null) {
            throw new FeedbackRejection("SIGNAL");
        }
        if (category == Category.WITHHELD || authorization == Authorization.WITHHELD) {
            authorization = Authorization.WITHHELD;
            evidenceState = EvidenceState.NOT_USABLE;
        }
        if (category == Category.COUNTERPART_OPINION || category == Category.NO_LEARNING_SIGNAL
                || category == Category.RELATIONSHIP_OUTCOME || category == Category.AMBIGUOUS_REASON) {
            learnableTarget = null;
        }
    }

    public boolean canPropose() {
        return evidenceState == EvidenceState.USABLE
                && (category == Category.EXPLICIT_SELF_CORRECTION
                || category == Category.EXPLICIT_PARTNER_PREFERENCE
                || category == Category.PREFERENCE_HYPOTHESIS);
    }
}
