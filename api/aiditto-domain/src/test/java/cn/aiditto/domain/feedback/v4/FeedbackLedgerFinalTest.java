package cn.aiditto.domain.feedback.v4;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackLedgerFinalTest {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void aParticipantCannotCorrectSomeoneElseOrASystemFact() {
        FeedbackLedger ledger = published();
        RelationshipFeedbackEvent accept = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0);
        RelationshipFeedbackEvent published = ledger.events().get(0);
        assertThatThrownBy(() -> ledger.append(correction(81002, "steal", accept.eventId()), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NOT_EVENT_OWNER");
        assertThatThrownBy(() -> ledger.append(correction(81001, "system", published.eventId()), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("CANNOT_CORRECT_SYSTEM");
        RelationshipFeedbackEvent view = ledger.append(person(FeedbackEventType.MATCH_VIEWED, 81001, "view"), T0);
        assertThatThrownBy(() -> ledger.append(correction(81001, "view-fix", view.eventId()), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ILLEGAL_REPLACEMENT");
        assertThat(ledger.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
    }

    @Test
    void correctingAnAcceptDropsMutualUntilANewAcceptExists() {
        FeedbackLedger ledger = published();
        RelationshipFeedbackEvent accept = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0);
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.plusSeconds(1));
        assertThat(ledger.project().mutualAcceptance()).isTrue();
        RelationshipFeedbackEvent fix = ledger.append(correction(81001, "fix", accept.eventId()), T0.plusSeconds(2));
        assertThat(ledger.project().mutualAcceptance()).isFalse();
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "acc-a-2", T0.plusSeconds(3), fix.eventId(), null, null), T0.plusSeconds(3));
        assertThat(ledger.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(ledger.project().mutualAcceptance()).isTrue();
        assertThat(ledger.events().stream().filter(event -> event.eventType() == FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED)).hasSize(2);
        assertThat(ledger.eligibility().reasons()).contains("CORRECTED");
    }

    @Test
    void expiryBlocksALateAcceptAndOneSidedAbsenceStaysAReport() {
        FeedbackLedger expired = published();
        expired.append(system(FeedbackEventType.MATCH_EXPIRED, "exp"), T0);
        assertThatThrownBy(() -> expired.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "late"), T0.minusSeconds(5)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("EXPIRED");
        FeedbackLedger absent = bothAccepted();
        absent.append(person(FeedbackEventType.DATE_SCHEDULED, 81001, "when"), T0);
        absent.append(person(FeedbackEventType.DATE_REPORTED_NOT_ATTENDED, 81001, "miss-a"), T0.plusSeconds(1));
        assertThat(absent.project().attendance()).isEqualTo(FeedbackFacts.AttendanceState.ONE_REPORTED_NOT_ATTENDED);
        absent.append(person(FeedbackEventType.DATE_REPORTED_NOT_ATTENDED, 81002, "miss-b"), T0.plusSeconds(2));
        assertThat(absent.project().attendance()).isEqualTo(FeedbackFacts.AttendanceState.BOTH_REPORTED_NOT_ATTENDED);
        assertThat(absent.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.DATE_REPORTED_NOT_ATTENDED)
                .allMatch(event -> event.evidenceStatus() == FeedbackFacts.EvidenceStatus.SELF_REPORTED)).isTrue();
    }

    @Test
    void learningRefusalStillAllowsAcceptAndDecline() {
        MatchFeedbackContext context = MatchFeedbackContext.experiment(71021, "run-f01", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true, false, false);
        FeedbackLedger ledger = new FeedbackLedger(context);
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0);
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        ledger.append(person(FeedbackEventType.MATCH_DECLINED, 81002, "dec"), T0.plusSeconds(1));
        assertThat(ledger.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(ledger.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.DECLINED);
        assertThat(ledger.eligibility().reasons()).contains("NO_LEARNING_CONSENT");
        assertThat(ledger.eligibility().matchFactEligible()).isFalse();
    }

    @Test
    void theSameKeyCannotChangeItsPayloadAndRevocationStaysClosed() {
        FeedbackLedger ledger = published();
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        assertThatThrownBy(() -> ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "acc", T0, null, "OTHER", null), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        ledger.append(person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "revoke"), T0.plusSeconds(1));
        RelationshipFeedbackEvent again = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0.plusSeconds(2));
        assertThat(again.reasonCode()).isNull();
        assertThat(ledger.eligibility().reasons()).contains("PROCESSING_REVOKED");
        assertThat(ledger.project().mutualAcceptance()).isFalse();
    }

    @Test
    void deletionBlocksALaterWriteAndAMutualWithoutCausesDoesNotCount() {
        FeedbackLedger ledger = bothAccepted();
        ledger.markErased(81001);
        assertThatThrownBy(() -> ledger.append(person(FeedbackEventType.MATCH_VIEWED, 81002, "late"), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ERASED");
        RelationshipFeedbackEvent forged = ledger.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED)
                .findFirst().orElseThrow();
        RelationshipFeedbackEvent broken = new RelationshipFeedbackEvent(forged.eventId(), forged.schemaVersion(),
                forged.eventType(), forged.matchId(), forged.matchingRunId(), forged.matchingPeriod(), 0, 0,
                forged.direction(), forged.sourceType(), forged.occurredAt(), forged.recordedAt(), forged.idempotencyKey(),
                null, null, forged.consentScope(), forged.actorProfileVersionAtMatch(), forged.counterpartProfileVersionAtMatch(),
                forged.compatibilityPolicyVersion(), forged.matchingObjectiveVersion(), forged.candidateStrategyVersion(),
                null, forged.evidenceStatus());
        FeedbackLedger replayed = FeedbackLedger.replay(ledger.context(), List.of(ledger.events().get(0), broken), false);
        assertThat(replayed.project().mutualAcceptance()).isFalse();
    }

    @Test
    void consumptionRequiresCurrentAuthorizationAndDistinctPurposes() {
        FeedbackLedger ledger = bothAccepted();
        String eventId = ledger.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.MATCH_ACCEPTED)
                .findFirst().orElseThrow().eventId();
        FeedbackConsumption first = FeedbackConsumption.prepare(ledger, eventId, FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-1", T0, "proposal-1");
        assertThat(first.status()).isEqualTo(FeedbackConsumption.Status.APPLIED);
        assertThat(FeedbackConsumption.purpose(FeedbackConsumption.Consumer.PROFILE_LEARNING, "learn-1"))
                .isNotEqualTo(FeedbackConsumption.purpose(FeedbackConsumption.Consumer.GROUP_EVALUATION, "group-1"));
        FeedbackConsumption group = FeedbackConsumption.prepare(ledger, eventId, FeedbackConsumption.Consumer.GROUP_EVALUATION,
                "group-1", T0, "obs-1");
        assertThat(group.status()).isEqualTo(FeedbackConsumption.Status.APPLIED);
        assertThat(group.consumer()).isEqualTo(FeedbackConsumption.Consumer.GROUP_EVALUATION);
        ledger.append(person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "revoke"), T0);
        assertThatThrownBy(() -> FeedbackConsumption.prepare(ledger, eventId, FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-1", T0, "proposal-1"))
                .isInstanceOf(FeedbackRejection.class);
        assertThatThrownBy(() -> FeedbackConsumption.prepare(ledger, eventId, FeedbackConsumption.Consumer.GROUP_EVALUATION,
                "group-1", T0, "obs-1"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("PROCESSING_REVOKED");
        assertThatThrownBy(MatchFeedbackContext::production)
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("PRODUCTION_CONTEXT_NOT_WIRED");
        assertThatThrownBy(() -> FeedbackMetrics.summarizeDistinct(List.of(ledger.project(), ledger.project()), List.of(true, true)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_MATCH");
    }

    private static FeedbackLedger published() {
        FeedbackLedger ledger = new FeedbackLedger(MatchFeedbackContext.experiment(71031, "run-f01", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true));
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub-final"), T0.minusSeconds(10));
        return ledger;
    }

    private static FeedbackLedger bothAccepted() {
        FeedbackLedger ledger = published();
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0.minusSeconds(2));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.minusSeconds(1));
        return ledger;
    }

    private static FeedbackCommand system(FeedbackEventType type, String key) {
        return new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, key, T0, null, null, null);
    }

    private static FeedbackCommand person(FeedbackEventType type, long actor, String key) {
        return new FeedbackCommand(type, actor, FeedbackFacts.SourceType.PARTICIPANT, key, T0, null, null, null);
    }

    private static FeedbackCommand correction(long actor, String key, String supersedes) {
        return new FeedbackCommand(FeedbackEventType.FEEDBACK_CORRECTED, actor, FeedbackFacts.SourceType.PARTICIPANT,
                key, T0, supersedes, "INSUFFICIENT_INFORMATION", null);
    }
}
