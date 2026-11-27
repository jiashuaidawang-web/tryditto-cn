package cn.aiditto.domain.feedback.v4;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * One learning use of one event. {@code learning_blocked == false} is not enough to consume it.
 * Group evaluation is named so its key stays distinct. This round does not run that learner.
 */
public record FeedbackConsumption(
        String eventId,
        Consumer consumer,
        String policyVersion,
        long lifecycleGeneration,
        long authorizationEpoch,
        Status status,
        Instant consumedAt,
        String derivedArtifactId) {

    public enum Consumer { PROFILE_LEARNING, GROUP_EVALUATION }

    public enum Status { PENDING, APPLIED, FAILED, REVOKED }

    private static final Set<FeedbackEventType> CONSUMABLE = EnumSet.of(
            FeedbackEventType.MATCH_ACCEPTED,
            FeedbackEventType.MATCH_DECLINED,
            FeedbackEventType.DATE_REPORTED_ATTENDED,
            FeedbackEventType.DATE_REPORTED_NOT_ATTENDED,
            FeedbackEventType.CONTINUE_INTEREST_EXPRESSED,
            FeedbackEventType.CONTINUE_INTEREST_DECLINED,
            FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED,
            FeedbackEventType.FEEDBACK_CORRECTED);

    public FeedbackConsumption {
        if (eventId == null || eventId.isBlank()) throw new FeedbackRejection("EVENT_ID");
        if (consumer == null || status == null || consumedAt == null) throw new FeedbackRejection("CONSUMPTION");
        if (policyVersion == null || policyVersion.isBlank()) throw new FeedbackRejection("POLICY");
        if (lifecycleGeneration < 1 || authorizationEpoch < 1) throw new FeedbackRejection("GENERATION");
        if (consumer == Consumer.GROUP_EVALUATION) throw new FeedbackRejection("F03_NOT_IMPLEMENTED");
    }

    public static String purpose(Consumer consumer, String policyVersion) {
        return consumer.name() + ":" + policyVersion;
    }

    public static FeedbackConsumption prepare(FeedbackLedger ledger, String eventId, Consumer consumer, String policyVersion,
                                              Instant consumedAt, String derivedArtifactId) {
        if (consumer == Consumer.GROUP_EVALUATION) throw new FeedbackRejection("F03_NOT_IMPLEMENTED");
        RelationshipFeedbackEvent event = ledger.events().stream()
                .filter(item -> item.eventId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> new FeedbackRejection("UNKNOWN_EVENT"));
        if (!CONSUMABLE.contains(event.eventType())) throw new FeedbackRejection("NOT_CONSUMABLE");
        if (event.actorUserId() == 0) throw new FeedbackRejection("NOT_CONSUMABLE");
        FeedbackLearningEligibility eligibility = ledger.eligibility();
        if (!eligibility.matchFactEligible()) {
            throw new FeedbackRejection(eligibility.reasons().isEmpty() ? "NOT_CONSUMABLE" : eligibility.reasons().get(0));
        }
        if (!ledger.context().learningConsent(event.actorUserId())) throw new FeedbackRejection("NO_LEARNING_CONSENT");
        if (!ledger.context().feedbackConsent(event.actorUserId())) throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        long actor = event.actorUserId();
        return new FeedbackConsumption(eventId, consumer, policyVersion,
                ledger.context().lifecycleGeneration(actor), ledger.context().authorizationEpoch(actor),
                Status.APPLIED, consumedAt, derivedArtifactId);
    }
}
