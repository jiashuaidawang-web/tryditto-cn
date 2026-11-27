package cn.aiditto.domain.feedback.v4;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackLedgerTest {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void legalFlowKeepsEachSideAndDerivesMutualOnce() {
        FeedbackLedger ledger = ledger(true, true, true);
        RelationshipFeedbackEvent published = ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0);
        ledger.append(person(FeedbackEventType.MATCH_VIEWED, 81001, "view-a"), T0.plusSeconds(1));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0.plusSeconds(2));
        RelationshipFeedbackEvent again = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0.plusSeconds(3));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.plusSeconds(4));
        FeedbackProjection projection = ledger.project();
        List<RelationshipFeedbackEvent> mutual = ledger.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED)
                .toList();
        assertThat(again.eventId()).isEqualTo(ledger.events().get(2).eventId());
        assertThat(mutual).hasSize(1);
        assertThat(mutual.get(0).causationEventId().split(",")).hasSize(2);
        assertThat(mutual.get(0).sourceType()).isEqualTo(FeedbackFacts.SourceType.SYSTEM);
        assertThat(projection.published()).isTrue();
        assertThat(projection.viewedA()).isTrue();
        assertThat(projection.viewedB()).isFalse();
        assertThat(projection.responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(projection.responseB()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(projection.mutualAcceptance()).isTrue();
        assertThat(published.actorProfileVersionAtMatch()).isEqualTo(7);
        assertThat(published.counterpartProfileVersionAtMatch()).isEqualTo(9);
        assertThat(published.candidateStrategyVersion()).isEqualTo("strategy-e-1");
        assertThat(ledger.eligibility().profileUpdateAllowed()).isFalse();
        assertThat(ledger.eligibility().negativeLabelAllowed()).isFalse();
    }

    @Test
    void outsiderForgedIdentityAndForgedSystemEventAreRefused() {
        FeedbackLedger ledger = published();
        assertThatThrownBy(() -> ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 999, "outsider"), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NOT_PARTICIPANT");
        assertThatThrownBy(() -> ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "forged-person", T0, null, null, 999L), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("FORGED_COUNTERPART");
        assertThatThrownBy(() -> ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "forged-system", T0, null, null, null), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("FORGED_SYSTEM_EVENT");
        assertThat(ledger.events()).hasSize(1);
    }

    @Test
    void duplicateAcceptIsOneFactAndASecondKeyIsRefused() {
        FeedbackLedger ledger = published();
        RelationshipFeedbackEvent first = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        RelationshipFeedbackEvent repeat = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0.plusSeconds(5));
        assertThat(repeat).isEqualTo(first);
        assertThatThrownBy(() -> ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-2"), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_EFFECT");
        assertThat(ledger.events()).hasSize(2);
    }

    @Test
    void concurrentAcceptsLeaveOneAndRetractBlocksLearning() throws Exception {
        FeedbackLedger ledger = published();
        AtomicReference<String> failure = new AtomicReference<>();
        Thread first = new Thread(() -> appendQuietly(ledger, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "race-a"), failure));
        Thread second = new Thread(() -> appendQuietly(ledger, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "race-b"), failure));
        first.start();
        second.start();
        first.join();
        second.join();
        assertThat(failure.get()).isEqualTo("DUPLICATE_EFFECT");
        assertThat(ledger.events().stream().filter(event -> event.eventType() == FeedbackEventType.MATCH_ACCEPTED)).hasSize(1);

        FeedbackLedger race = published();
        Thread accept = new Thread(() -> race.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0));
        Thread retract = new Thread(() -> race.append(person(FeedbackEventType.FEEDBACK_RETRACTED, 81001, "retract"), T0));
        accept.start();
        retract.start();
        accept.join();
        retract.join();
        assertThat(race.project().retracted()).isTrue();
        assertThat(race.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.WITHHELD);
        assertThat(race.eligibility().matchFactEligible()).isFalse();
        assertThat(race.eligibility().reasons()).contains("RETRACTED");
        assertThatThrownBy(() -> race.append(new FeedbackCommand(FeedbackEventType.FEEDBACK_CORRECTED, 81001,
                FeedbackFacts.SourceType.PARTICIPANT, "undo-retract", T0, race.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED)
                .findFirst().orElseThrow().eventId(), null, null), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("REVOCATION_STANDS");
    }

    @Test
    void mutualEventIsIdempotentAndLateViewDoesNotRewriteTheAccept() {
        FeedbackLedger ledger = published();
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0.plusSeconds(2));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.plusSeconds(3));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.plusSeconds(4));
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_VIEWED, 81002, FeedbackFacts.SourceType.PARTICIPANT,
                "late-view", T0.minusSeconds(30), null, null, 81001L), T0.plusSeconds(5));
        assertThat(ledger.events().stream().filter(event -> event.eventType() == FeedbackEventType.MUTUAL_ACCEPTANCE_REACHED)).hasSize(1);
        assertThat(ledger.project().viewedB()).isTrue();
        assertThat(ledger.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
    }

    @Test
    void correctionReplacesTheAcceptAndTheOldEventCannotBeConsumed() {
        FeedbackLedger ledger = published();
        RelationshipFeedbackEvent accept = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0);
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.plusSeconds(1));
        ledger.append(new FeedbackCommand(FeedbackEventType.FEEDBACK_CORRECTED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "correct", T0.plusSeconds(2), accept.eventId(), "SCHEDULING_DIFFICULTY", 81002L), T0.plusSeconds(2));
        ledger.append(new FeedbackCommand(FeedbackEventType.MATCH_DECLINED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                "decline", T0.plusSeconds(3), null, "SCHEDULING_DIFFICULTY", 81002L), T0.plusSeconds(3));
        assertThat(ledger.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.DECLINED);
        assertThat(ledger.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(ledger.project().mutualAcceptance()).isFalse();
        assertThat(ledger.eligibility().reasons()).contains("CORRECTED");
        assertThatThrownBy(() -> ledger.markConsumed(accept.eventId()))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("SUPERSEDED");
    }

    @Test
    void contestedAttendanceStaysContestedAndOneSideDoesNotSpeakForTheOther() {
        FeedbackLedger ledger = bothAccepted();
        ledger.append(person(FeedbackEventType.DATE_REPORTED_ATTENDED, 81001, "met-a"), T0);
        ledger.append(person(FeedbackEventType.DATE_REPORTED_NOT_ATTENDED, 81002, "met-b"), T0.plusSeconds(1));
        ledger.append(person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 81001, "more-a"), T0.plusSeconds(2));
        FeedbackProjection projection = ledger.project();
        assertThat(projection.attendance()).isEqualTo(FeedbackFacts.AttendanceState.CONTESTED);
        assertThat(projection.continueA()).isEqualTo(FeedbackFacts.ContinueState.EXPRESSED);
        assertThat(projection.continueB()).isEqualTo(FeedbackFacts.ContinueState.UNKNOWN);
        assertThat(projection.bothContinue()).isFalse();
        assertThat(projection.responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(projection.responseB()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(ledger.eligibility().reasons()).contains("CONTESTED");
        assertThat(ledger.eligibility().negativeLabelAllowed()).isFalse();
    }

    @Test
    void versionsComeFromTheSnapshotAndAForgedReplayIsNotEligible() {
        FeedbackLedger ledger = published();
        RelationshipFeedbackEvent accept = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        assertThat(accept.actorProfileVersionAtMatch()).isEqualTo(7);
        assertThat(accept.counterpartProfileVersionAtMatch()).isEqualTo(9);
        assertThat(accept.compatibilityPolicyVersion()).isEqualTo("policy-1");
        assertThat(accept.matchingObjectiveVersion()).isEqualTo("objective-g2-1");
        assertThat(accept.candidateStrategyVersion()).isEqualTo("strategy-e-1");
        RelationshipFeedbackEvent forged = new RelationshipFeedbackEvent(accept.eventId(), accept.schemaVersion(),
                accept.eventType(), accept.matchId(), accept.matchingRunId(), accept.matchingPeriod(),
                accept.actorUserId(), accept.counterpartUserId(), accept.direction(), accept.sourceType(),
                accept.occurredAt(), accept.recordedAt(), accept.idempotencyKey(), accept.causationEventId(),
                accept.supersedesEventId(), accept.consentScope(), 1, accept.counterpartProfileVersionAtMatch(),
                accept.compatibilityPolicyVersion(), accept.matchingObjectiveVersion(), "client-picked",
                accept.reasonCode(), accept.evidenceStatus());
        FeedbackLedger replayed = FeedbackLedger.replay(context(71001, true, true, true), List.of(publishedEvent(ledger), forged), false);
        assertThat(replayed.eligibility().reasons()).contains("SNAPSHOT_MISMATCH", "STRATEGY_MISMATCH");
    }

    @Test
    void missingMatchAuthorizationAndMissingFeedbackConsentAreDifferentRefusals() {
        FeedbackLedger unpublished = ledger(false, true, true);
        assertThatThrownBy(() -> unpublished.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NOT_AUTHORIZED");
        FeedbackLedger noConsent = ledger(true, false, true);
        noConsent.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0);
        noConsent.append(person(FeedbackEventType.MATCH_VIEWED, 81001, "view"), T0);
        assertThatThrownBy(() -> noConsent.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_FEEDBACK_CONSENT");
        assertThat(noConsent.eligibility().reasons()).contains("NO_FEEDBACK_CONSENT");
        assertThat(noConsent.project().viewedA()).isTrue();
    }

    @Test
    void revokeAndEraseStopLearningButUnknownAndExpiryAreNotDecline() {
        FeedbackLedger revoked = bothAccepted();
        revoked.append(person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "revoke"), T0);
        assertThat(revoked.project().mutualAcceptance()).isTrue();
        assertThat(revoked.project().processingRevoked()).isTrue();
        assertThat(revoked.eligibility().reasons()).contains("PROCESSING_REVOKED");
        assertThat(revoked.markConsumed(revoked.events().get(1).eventId())).isTrue();
        assertThat(revoked.markConsumed(revoked.events().get(1).eventId())).isFalse();
        assertThat(revoked.eligibility().reasons()).contains("ALREADY_CONSUMED", "DUPLICATE_CONSUMPTION");

        FeedbackLedger erased = bothAccepted();
        erased.blockLearning();
        assertThat(erased.eligibility().reasons()).contains("ERASED");

        FeedbackLedger expired = published();
        expired.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc"), T0);
        expired.append(system(FeedbackEventType.MATCH_EXPIRED, "exp"), T0.plusSeconds(1));
        assertThat(expired.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.ACCEPTED);
        assertThat(expired.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.EXPIRED);
        assertThat(expired.project().responseB()).isNotEqualTo(FeedbackFacts.ResponseState.DECLINED);

        FeedbackLedger quiet = published();
        assertThat(quiet.project().responseA()).isEqualTo(FeedbackFacts.ResponseState.UNKNOWN);
        FeedbackMetrics.Report report = FeedbackMetrics.summarize(
                List.of(quiet.project(), expired.project(), quiet.project()),
                List.of(true, true, false));
        assertThat(fraction(report, "decline_rate")).isEqualTo("0/2");
        assertThat(fraction(report, "expiry_rate")).isEqualTo("1/2");
        assertThat(report.immature()).isEqualTo(1);
    }

    @Test
    void replayProjectsTheSameFactsAndTheLabCohortUsesSeparateRatios() throws Exception {
        List<Lab> labs = labs();
        List<FeedbackProjection> projections = new ArrayList<>();
        for (Lab lab : labs) {
            FeedbackLedger replayed = FeedbackLedger.replay(lab.ledger.context(), lab.ledger.events(), false);
            assertThat(replayed.project()).isEqualTo(lab.ledger.project());
            projections.add(lab.ledger.project());
        }
        FeedbackMetrics.Report cohort = FeedbackMetrics.summarize(projections, projections.stream().map(ignored -> true).toList());
        assertThat(fraction(cohort, "view_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "one_accept_rate")).isEqualTo("10/10");
        assertThat(fraction(cohort, "mutual_accept_rate")).isEqualTo("7/10");
        assertThat(fraction(cohort, "mutual_accept_rate_among_responded")).isEqualTo("7/10");
        assertThat(fraction(cohort, "decline_rate")).isEqualTo("2/10");
        assertThat(fraction(cohort, "expiry_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "schedule_rate")).isEqualTo("3/10");
        assertThat(fraction(cohort, "reported_attendance_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "mutual_attendance_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "one_continue_rate")).isEqualTo("2/10");
        assertThat(fraction(cohort, "mutual_continue_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "coverage_rate")).isEqualTo("10/10");
        assertThat(fraction(cohort, "contest_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "retract_rate")).isEqualTo("0/10");
        assertThat(fraction(cohort, "processing_revoked_rate")).isEqualTo("1/10");
        assertThat(fraction(cohort, "learning_eligible_rate")).isEqualTo("7/10");
        assertThat(cohort.immature()).isZero();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/f01-match-lab-cases.json"), json(labs, cohort));
    }

    private static void appendQuietly(FeedbackLedger ledger, FeedbackCommand command, AtomicReference<String> failure) {
        try {
            ledger.append(command, T0);
        } catch (FeedbackRejection rejection) {
            failure.compareAndSet(null, rejection.code());
        }
    }

    private static FeedbackLedger published() {
        FeedbackLedger ledger = ledger(true, true, true);
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub"), T0.minusSeconds(10));
        return ledger;
    }

    private static FeedbackLedger bothAccepted() {
        FeedbackLedger ledger = published();
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "acc-a"), T0.minusSeconds(2));
        ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "acc-b"), T0.minusSeconds(1));
        return ledger;
    }

    private static RelationshipFeedbackEvent publishedEvent(FeedbackLedger ledger) {
        return ledger.events().stream()
                .filter(event -> event.eventType() == FeedbackEventType.MATCH_PUBLISHED)
                .findFirst()
                .orElseThrow();
    }

    private static List<Lab> labs() {
        List<Lab> labs = new ArrayList<>();
        labs.add(lab(1, "双方接受且愿意继续", 62, 71, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_VIEWED, 81001, "c1-view-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_VIEWED, 81002, "c1-view-b"), at(2));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c1-acc-a"), at(3));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c1-acc-b"), at(4));
            ledger.append(person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 81001, "c1-more-a"), at(5));
            ledger.append(person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 81002, "c1-more-b"), at(6));
        }));
        labs.add(lab(2, "A 接受，B 拒绝", 80, 44, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c2-acc-a"), at(1));
            ledger.append(decline(81002, "c2-dec-b", "NO_ROMANTIC_INTEREST"), at(2));
        }));
        labs.add(lab(3, "A 接受，B 超时", 55, 40, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c3-acc-a"), at(1));
            ledger.append(system(FeedbackEventType.MATCH_EXPIRED, "c3-exp"), at(2));
        }));
        labs.add(lab(4, "双方接受，窗口结束仍未安排见面", 70, 70, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c4-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c4-acc-b"), at(2));
            ledger.append(system(FeedbackEventType.MATCH_EXPIRED, "c4-exp"), at(3));
        }));
        labs.add(lab(5, "安排见面后取消", 66, 61, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c5-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c5-acc-b"), at(2));
            ledger.append(person(FeedbackEventType.DATE_SCHEDULED, 81001, "c5-when"), at(3));
            ledger.append(person(FeedbackEventType.DATE_CANCELLED, 81002, "c5-cancel"), at(4));
        }));
        labs.add(lab(6, "双方报告已经见面", 74, 69, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c6-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c6-acc-b"), at(2));
            ledger.append(person(FeedbackEventType.DATE_SCHEDULED, 81001, "c6-when"), at(3));
            ledger.append(person(FeedbackEventType.DATE_REPORTED_ATTENDED, 81001, "c6-met-a"), at(4));
            ledger.append(person(FeedbackEventType.DATE_REPORTED_ATTENDED, 81002, "c6-met-b"), at(5));
        }));
        labs.add(lab(7, "双方见面报告矛盾", 58, 63, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c7-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c7-acc-b"), at(2));
            ledger.append(person(FeedbackEventType.DATE_SCHEDULED, 81002, "c7-when"), at(3));
            ledger.append(person(FeedbackEventType.DATE_REPORTED_ATTENDED, 81001, "c7-met-a"), at(4));
            ledger.append(person(FeedbackEventType.DATE_REPORTED_NOT_ATTENDED, 81002, "c7-miss-b"), at(5));
        }));
        labs.add(lab(8, "一方愿意继续，另一方不愿", 64, 52, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c8-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c8-acc-b"), at(2));
            ledger.append(person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 81001, "c8-more-a"), at(3));
            ledger.append(person(FeedbackEventType.CONTINUE_INTEREST_DECLINED, 81002, "c8-stop-b"), at(4));
        }));
        labs.add(lab(9, "用户更正之前的接受", 48, 77, ledger -> {
            open(ledger);
            RelationshipFeedbackEvent accept = ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c9-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c9-acc-b"), at(2));
            ledger.append(new FeedbackCommand(FeedbackEventType.FEEDBACK_CORRECTED, 81001, FeedbackFacts.SourceType.PARTICIPANT,
                    "c9-correct", at(3), accept.eventId(), "INSUFFICIENT_INFORMATION", 81002L), at(3));
            ledger.append(decline(81001, "c9-dec-a", "INSUFFICIENT_INFORMATION"), at(4));
        }));
        labs.add(lab(10, "用户撤回反馈处理授权", 73, 73, ledger -> {
            open(ledger);
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "c10-acc-a"), at(1));
            ledger.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "c10-acc-b"), at(2));
            ledger.append(person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "c10-revoke"), at(3));
        }));
        assertThat(labs.get(0).ledger.project().bothContinue()).isTrue();
        assertThat(labs.get(1).ledger.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.DECLINED);
        assertThat(labs.get(2).ledger.project().responseB()).isEqualTo(FeedbackFacts.ResponseState.EXPIRED);
        assertThat(labs.get(3).ledger.project().schedule()).isEqualTo(FeedbackFacts.ScheduleState.NOT_OCCURRED);
        assertThat(labs.get(4).ledger.project().schedule()).isEqualTo(FeedbackFacts.ScheduleState.CANCELLED);
        assertThat(labs.get(5).ledger.project().attendance()).isEqualTo(FeedbackFacts.AttendanceState.BOTH_REPORTED_ATTENDED);
        assertThat(labs.get(6).ledger.project().contested()).isTrue();
        assertThat(labs.get(7).ledger.project().bothContinue()).isFalse();
        assertThat(labs.get(8).ledger.eligibility().matchFactEligible()).isFalse();
        assertThat(labs.get(9).ledger.project().processingRevoked()).isTrue();
        return labs;
    }

    private static void open(FeedbackLedger ledger) {
        ledger.append(system(FeedbackEventType.MATCH_PUBLISHED, "pub-" + ledger.context().matchId()), T0);
    }

    private static Lab lab(int number, String title, int scoreA, int scoreB, java.util.function.Consumer<FeedbackLedger> script) {
        FeedbackLedger ledger = new FeedbackLedger(context(71000 + number, true, true, true));
        script.accept(ledger);
        return new Lab(number, title, scoreA, scoreB, ledger);
    }

    private static String json(List<Lab> labs, FeedbackMetrics.Report cohort) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schema\": \"feedback-lab-1\",\n  \"cases\": [\n");
        for (int i = 0; i < labs.size(); i++) {
            Lab lab = labs.get(i);
            FeedbackProjection projection = lab.ledger.project();
            FeedbackLearningEligibility eligibility = lab.ledger.eligibility();
            json.append("    {\n");
            json.append("      \"id\": ").append(lab.number).append(",\n");
            json.append("      \"title\": \"").append(lab.title).append("\",\n");
            json.append("      \"snapshot\": {\"matchId\": ").append(lab.ledger.context().matchId())
                    .append(", \"userA\": 81001, \"userB\": 81002, \"profileVersionA\": 7, \"profileVersionB\": 9, \"published\": true},\n");
            json.append("      \"scores\": {\"aToB\": ").append(lab.scoreA).append(", \"bToA\": ").append(lab.scoreB).append("},\n");
            json.append("      \"strategy\": {\"policy\": \"policy-1\", \"objective\": \"objective-g2-1\", \"candidate\": \"strategy-e-1\"},\n");
            json.append("      \"events\": [\n");
            List<RelationshipFeedbackEvent> events = lab.ledger.events();
            for (int e = 0; e < events.size(); e++) {
                RelationshipFeedbackEvent event = events.get(e);
                json.append("        {\"type\": \"").append(event.eventType())
                        .append("\", \"actor\": ").append(event.actorUserId())
                        .append(", \"direction\": \"").append(event.direction())
                        .append("\", \"source\": \"").append(event.sourceType())
                        .append("\", \"reason\": ");
                json.append(event.reasonCode() == null ? "null" : "\"" + event.reasonCode() + "\"");
                json.append(", \"supersedes\": ");
                json.append(event.supersedesEventId() == null ? "null" : "\"" + event.supersedesEventId() + "\"");
                json.append(", \"causation\": ");
                json.append(event.causationEventId() == null ? "null" : "\"" + event.causationEventId() + "\"");
                json.append("}");
                if (e + 1 < events.size()) json.append(",");
                json.append("\n");
            }
            json.append("      ],\n");
            json.append("      \"projection\": {")
                    .append("\"published\": ").append(projection.published())
                    .append(", \"viewedA\": ").append(projection.viewedA())
                    .append(", \"viewedB\": ").append(projection.viewedB())
                    .append(", \"responseA\": \"").append(projection.responseA())
                    .append("\", \"responseB\": \"").append(projection.responseB())
                    .append("\", \"mutualAcceptance\": ").append(projection.mutualAcceptance())
                    .append(", \"schedule\": \"").append(projection.schedule())
                    .append("\", \"attendance\": \"").append(projection.attendance())
                    .append("\", \"continueA\": \"").append(projection.continueA())
                    .append("\", \"continueB\": \"").append(projection.continueB())
                    .append("\", \"bothContinue\": ").append(projection.bothContinue())
                    .append(", \"contested\": ").append(projection.contested())
                    .append(", \"retracted\": ").append(projection.retracted())
                    .append(", \"processingRevoked\": ").append(projection.processingRevoked())
                    .append(", \"learningAllowed\": ").append(projection.learningAllowed())
                    .append("},\n");
            json.append("      \"unknown\": ").append(quoted(projection.unknown())).append(",\n");
            json.append("      \"learning\": {\"matchFactEligible\": ").append(eligibility.matchFactEligible())
                    .append(", \"profileUpdateAllowed\": false, \"negativeLabelAllowed\": false, \"reasons\": ")
                    .append(quoted(eligibility.reasons())).append("}\n");
            json.append("    }");
            if (i + 1 < labs.size()) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n  \"cohort\": {\n    \"window\": \"ten fictional mature published matches\",\n    \"dedupe\": \"one match\",\n");
        json.append("    \"published\": {\n");
        for (int i = 0; i < cohort.publishedBase().size(); i++) {
            FeedbackMetrics.Ratio ratio = cohort.publishedBase().get(i);
            json.append("      \"").append(ratio.name()).append("\": \"").append(ratio.fraction()).append("\"");
            if (i + 1 < cohort.publishedBase().size()) json.append(",");
            json.append("\n");
        }
        json.append("    },\n    \"responded\": {\n");
        for (int i = 0; i < cohort.respondedBase().size(); i++) {
            FeedbackMetrics.Ratio ratio = cohort.respondedBase().get(i);
            json.append("      \"").append(ratio.name()).append("\": \"").append(ratio.fraction()).append("\"");
            if (i + 1 < cohort.respondedBase().size()) json.append(",");
            json.append("\n");
        }
        json.append("    }\n  }\n}\n");
        return json.toString();
    }

    private static String quoted(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append("\"").append(values.get(i)).append("\"");
        }
        return builder.append("]").toString();
    }

    private static String fraction(FeedbackMetrics.Report report, String name) {
        for (FeedbackMetrics.Ratio ratio : report.publishedBase()) {
            if (ratio.name().equals(name)) return ratio.fraction();
        }
        for (FeedbackMetrics.Ratio ratio : report.respondedBase()) {
            if (ratio.name().equals(name)) return ratio.fraction();
        }
        throw new AssertionError(name);
    }

    private static FeedbackLedger ledger(boolean published, boolean consentA, boolean consentB) {
        return new FeedbackLedger(context(71001, published, consentA, consentB));
    }

    private static MatchFeedbackContext context(long matchId, boolean published, boolean consentA, boolean consentB) {
        return new MatchFeedbackContext(matchId, "run-f01", LocalDate.of(2026, 11, 23),
                81001, 81002, published, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", consentA, consentB);
    }

    private static FeedbackCommand system(FeedbackEventType type, String key) {
        return new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, key, T0, null, null, null);
    }

    private static FeedbackCommand person(FeedbackEventType type, long actor, String key) {
        return new FeedbackCommand(type, actor, FeedbackFacts.SourceType.PARTICIPANT, key, T0, null, null, null);
    }

    private static FeedbackCommand decline(long actor, String key, String reason) {
        return new FeedbackCommand(FeedbackEventType.MATCH_DECLINED, actor, FeedbackFacts.SourceType.PARTICIPANT,
                key, T0, null, reason, null);
    }

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    private record Lab(int number, String title, int scoreA, int scoreB, FeedbackLedger ledger) {}
}
