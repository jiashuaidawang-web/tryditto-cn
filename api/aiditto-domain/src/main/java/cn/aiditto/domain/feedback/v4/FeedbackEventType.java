package cn.aiditto.domain.feedback.v4;

/** What happened. A derived event is not a second user action. */
public enum FeedbackEventType {
    MATCH_PUBLISHED(true, FeedbackFacts.EvidenceStatus.SYSTEM_VERIFIED),
    MATCH_VIEWED(false, FeedbackFacts.EvidenceStatus.SYSTEM_VERIFIED),
    MATCH_ACCEPTED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    MATCH_DECLINED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    MATCH_EXPIRED(true, FeedbackFacts.EvidenceStatus.SYSTEM_VERIFIED),
    MUTUAL_ACCEPTANCE_REACHED(true, FeedbackFacts.EvidenceStatus.SYSTEM_VERIFIED),
    DATE_SCHEDULED(false, FeedbackFacts.EvidenceStatus.SYSTEM_VERIFIED),
    DATE_CANCELLED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    DATE_REPORTED_ATTENDED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    DATE_REPORTED_NOT_ATTENDED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    CONTINUE_INTEREST_EXPRESSED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    CONTINUE_INTEREST_DECLINED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    PREFERENCE_CORRECTION_SUBMITTED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    FEEDBACK_CORRECTED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    FEEDBACK_RETRACTED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED),
    FEEDBACK_PROCESSING_REVOKED(false, FeedbackFacts.EvidenceStatus.SELF_REPORTED);

    private final boolean system;
    private final FeedbackFacts.EvidenceStatus evidence;

    FeedbackEventType(boolean system, FeedbackFacts.EvidenceStatus evidence) {
        this.system = system;
        this.evidence = evidence;
    }

    public boolean system() {
        return system;
    }

    public FeedbackFacts.EvidenceStatus evidence() {
        return evidence;
    }
}
