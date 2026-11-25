package cn.aiditto.domain.match;

import static cn.aiditto.domain.match.MatchFixtures.A;
import static cn.aiditto.domain.match.MatchFixtures.B;
import static cn.aiditto.domain.match.MatchFixtures.OUTSIDER;
import static cn.aiditto.domain.match.MatchFixtures.RESPOND_DUE;
import static cn.aiditto.domain.match.MatchFixtures.T_REVEALED;
import static cn.aiditto.domain.match.MatchFixtures.T_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.aiditto.domain.match.FeedbackAnswer.Again;
import cn.aiditto.domain.match.FeedbackAnswer.Met;
import cn.aiditto.domain.match.MatchEvent.Cancel;
import cn.aiditto.domain.match.MatchEvent.CheckIn;
import cn.aiditto.domain.match.MatchEvent.DateEnded;
import cn.aiditto.domain.match.MatchEvent.Decline;
import cn.aiditto.domain.match.MatchEvent.FeedbackDeadline;
import cn.aiditto.domain.match.MatchEvent.Hold;
import cn.aiditto.domain.match.MatchEvent.Publish;
import cn.aiditto.domain.match.MatchEvent.Report;
import cn.aiditto.domain.match.MatchEvent.ReselectDeadline;
import cn.aiditto.domain.match.MatchEvent.RespondDeadline;
import cn.aiditto.domain.match.MatchEvent.SubmitFeedback;
import cn.aiditto.domain.match.MatchEvent.SubmitSlots;
import cn.aiditto.domain.slot.Deadlines;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class MatchMachineTest {

    // ── every allowed transition ────────────────────────────────────────────────────────────────

    record Case(String rule, Supplier<MatchSnapshot> given, MatchEvent event, Supplier<Instant> at,
                MatchState expected, List<Class<? extends Effect>> effects) {}

    private static Case c(String rule, Supplier<MatchSnapshot> given, MatchEvent e, Supplier<Instant> at,
                          MatchState expected, List<Class<? extends Effect>> effects) {
        return new Case(rule, given, e, at, expected, effects);
    }

    static List<Case> allowedCases() {
        List<Case> cases = new ArrayList<>(List.of(
                c("publish", MatchFixtures::draft, new Publish(RESPOND_DUE), () -> MatchFixtures.PUBLISH_AT, MatchState.PUBLISHED, List.of()),
                c("decline", MatchFixtures::published, new Decline(A), () -> T_REVEALED, MatchState.DECLINED, List.of()),
                c("slots.first", MatchFixtures::published, new SubmitSlots(A, Set.of("slot_0")), () -> T_REVEALED, MatchState.PUBLISHED, List.of()),
                c("slots.second.overlap", () -> MatchFixtures.aAccepted("slot_0", "slot_1"), new SubmitSlots(B, Set.of("slot_1")), () -> T_SECOND, MatchState.CONFIRMED, List.of()),
                c("slots.second.noOverlap", () -> MatchFixtures.aAccepted("slot_0"), new SubmitSlots(B, Set.of("slot_2")), () -> T_SECOND, MatchState.NO_OVERLAP, List.of()),
                c("respond.expired", MatchFixtures::published, new RespondDeadline(), () -> RESPOND_DUE, MatchState.EXPIRED,
                        List.of(Effect.RecordNoResponse.class, Effect.RecordNoResponse.class)),
                c("reselect.overlap", MatchFixtures::noOverlap, new SubmitSlots(A, Set.of("slot_1")), () -> T_SECOND.plusSeconds(60), MatchState.CONFIRMED, List.of()),
                c("reselect.waiting", MatchFixtures::noOverlap, new SubmitSlots(A, Set.of("slot_2")), () -> T_SECOND.plusSeconds(60), MatchState.NO_OVERLAP, List.of()),
                c("reselect.bothFailed", () -> MatchFixtures.run(MatchFixtures.noOverlap(), new SubmitSlots(A, Set.of("slot_2")), T_SECOND.plusSeconds(60)),
                        new SubmitSlots(B, Set.of("slot_0")), () -> T_SECOND.plusSeconds(120), MatchState.UNSCHEDULED, List.of()),
                c("reselect.expired", MatchFixtures::noOverlap, new ReselectDeadline(), () -> MatchFixtures.noOverlap().reselectDue(), MatchState.UNSCHEDULED, List.of()),
                c("checkin", MatchFixtures::confirmed, new CheckIn(A), () -> MatchFixtures.confirmed().slotStart().minus(Duration.ofMinutes(30)), MatchState.CONFIRMED, List.of()),
                c("cancel.early", MatchFixtures::confirmed, new Cancel(A), () -> MatchFixtures.confirmed().slotStart().minus(Duration.ofHours(7)), MatchState.CANCELLED, List.of()),
                c("cancel.late", MatchFixtures::confirmed, new Cancel(A), () -> MatchFixtures.confirmed().slotStart().minus(Duration.ofHours(5)), MatchState.CANCELLED,
                        List.of(Effect.RecordLateCancel.class)),
                c("date.ended", MatchFixtures::confirmed, new DateEnded(), () -> MatchFixtures.confirmed().slotEnd(), MatchState.FEEDBACK_OPEN, List.of()),
                c("feedback.first", MatchFixtures::feedbackOpen, new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), () -> MatchFixtures.feedbackOpen().slotEnd().plusSeconds(60), MatchState.FEEDBACK_OPEN, List.of()),
                c("feedback.last", () -> MatchFixtures.run(MatchFixtures.feedbackOpen(), new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), MatchFixtures.feedbackOpen().slotEnd()),
                        new SubmitFeedback(B, FeedbackAnswer.met(Again.YES)), () -> MatchFixtures.feedbackOpen().slotEnd().plusSeconds(60), MatchState.CLOSED,
                        List.of(Effect.UnlockContacts.class)),
                c("feedback.expired", MatchFixtures::feedbackOpen, new FeedbackDeadline(), () -> MatchFixtures.feedbackOpen().feedbackDue(), MatchState.CLOSED, List.of()),
                c("report.afterClose", MatchFixtures::closedMutual, new Report(B, ReportCategory.FAKE), () -> MatchFixtures.closedMutual().closedAt().plus(Duration.ofDays(6)), MatchState.CLOSED,
                        List.of(Effect.CreateReport.class))));
        for (MatchState s : List.of(MatchState.DRAFT, MatchState.PUBLISHED, MatchState.NO_OVERLAP, MatchState.CONFIRMED, MatchState.FEEDBACK_OPEN)) {
            cases.add(c("report.halt", () -> MatchFixtures.inState(s), new Report(B, ReportCategory.HARASSMENT),
                    () -> MatchFixtures.calmTime(MatchFixtures.inState(s)), MatchState.HALTED,
                    List.of(Effect.CreateReport.class, Effect.SuspendPendingReview.class)));
            cases.add(c("hold", () -> MatchFixtures.inState(s), new Hold(),
                    () -> MatchFixtures.calmTime(MatchFixtures.inState(s)), MatchState.CANCELLED, List.of()));
        }
        return cases;
    }

    @TestFactory
    Stream<DynamicTest> everyAllowedTransition() {
        return allowedCases().stream().map(k -> DynamicTest.dynamicTest(k.rule() + " from " + k.given().get().state(), () -> {
            MatchSnapshot before = k.given().get();
            Transition t = MatchMachine.apply(before, k.event(), k.at().get());
            assertThat(t.changed()).isTrue();
            assertThat(t.rule()).isEqualTo(k.rule());
            assertThat(t.from()).isEqualTo(before.state());
            assertThat(t.to()).isEqualTo(k.expected());
            assertThat(t.effects()).extracting(Object::getClass).containsExactlyElementsOf(k.effects());
            assertThat(MatchMachine.successors(before.state())).contains(t.to());
        }));
    }

    @Test
    void casesCoverEveryRowOfTheTable() {
        Set<String> covered = new HashSet<>();
        Set<String> coveredRows = new HashSet<>();
        for (Case k : allowedCases()) {
            covered.add(k.rule());
            coveredRows.add(k.rule() + "@" + k.given().get().state());
        }
        Set<String> rows = MatchMachine.rules().stream().map(r -> r.name() + "@" + r.from()).collect(Collectors.toSet());
        assertThat(covered).containsExactlyInAnyOrderElementsOf(MatchMachine.rules().stream().map(MatchMachine.Rule::name).collect(Collectors.toSet()));
        assertThat(coveredRows).containsExactlyInAnyOrderElementsOf(rows);
    }

    // ── every forbidden (state, event) pair is rejected ─────────────────────────────────────────

    /** One sample per event type, acted by B whose recorded facts never equal these payloads. */
    static List<MatchEvent> samplesByB() {
        return List.of(
                new Decline(B),
                new SubmitSlots(B, Set.of("slot_0")),
                new CheckIn(B),
                new Cancel(B),
                new SubmitFeedback(B, FeedbackAnswer.of(Met.NO_SHOW)),
                new Report(B, ReportCategory.OTHER),
                new RespondDeadline(),
                new ReselectDeadline(),
                new DateEnded(),
                new FeedbackDeadline(),
                new Hold());
    }

    private static final Set<String> DEADLINE_REPLAYS = Set.of(
            "EXPIRED/RespondDeadline", "UNSCHEDULED/ReselectDeadline", "FEEDBACK_OPEN/DateEnded", "CLOSED/FeedbackDeadline");

    @TestFactory
    Stream<DynamicTest> everyForbiddenEventIsRejected() {
        List<DynamicTest> tests = new ArrayList<>();
        for (MatchState s : MatchState.values()) {
            MatchSnapshot m = MatchFixtures.inState(s);
            Instant now = Instant.parse("2027-06-01T00:00:00Z");
            for (MatchEvent e : samplesByB()) {
                if (MatchMachine.allowedEvents(s).contains(e.getClass())) continue;
                String key = s + "/" + e.getClass().getSimpleName();
                tests.add(DynamicTest.dynamicTest(key, () -> {
                    if (DEADLINE_REPLAYS.contains(key) || (e instanceof Hold && s.isTerminal())) {
                        assertThat(MatchMachine.apply(m, e, now).changed()).isFalse();
                    } else {
                        assertThatThrownBy(() -> MatchMachine.apply(m, e, now))
                                .isInstanceOfSatisfying(MatchRuleException.class, x -> assertThat(x.code()).isEqualTo(MatchError.MATCH_STATE));
                    }
                }));
            }
        }
        return tests.stream();
    }

    @Test
    void allowedEventSetsMatchTheSpecTable() {
        assertThat(MatchMachine.allowedEvents(MatchState.DRAFT)).containsExactlyInAnyOrder(Publish.class, Report.class, Hold.class);
        assertThat(MatchMachine.allowedEvents(MatchState.PUBLISHED))
                .containsExactlyInAnyOrder(Decline.class, SubmitSlots.class, RespondDeadline.class, Report.class, Hold.class);
        assertThat(MatchMachine.allowedEvents(MatchState.NO_OVERLAP))
                .containsExactlyInAnyOrder(SubmitSlots.class, ReselectDeadline.class, Report.class, Hold.class);
        assertThat(MatchMachine.allowedEvents(MatchState.CONFIRMED))
                .containsExactlyInAnyOrder(CheckIn.class, Cancel.class, DateEnded.class, Report.class, Hold.class);
        assertThat(MatchMachine.allowedEvents(MatchState.FEEDBACK_OPEN))
                .containsExactlyInAnyOrder(SubmitFeedback.class, FeedbackDeadline.class, Report.class, Hold.class);
        assertThat(MatchMachine.allowedEvents(MatchState.CLOSED)).containsExactly(Report.class);
        for (MatchState s : EnumSet.of(MatchState.DECLINED, MatchState.EXPIRED, MatchState.UNSCHEDULED, MatchState.CANCELLED, MatchState.HALTED)) {
            assertThat(MatchMachine.allowedEvents(s)).as(s.name()).isEmpty();
        }
    }

    @Test
    void holdCancelsADraftWithoutPublishingOrEffectsAndASecondHoldIsAReplay() {
        Transition held = MatchMachine.apply(MatchFixtures.draft(), new Hold(), MatchFixtures.PUBLISH_AT);
        assertThat(held.changed()).isTrue();
        assertThat(held.after().state()).isEqualTo(MatchState.CANCELLED);
        assertThat(held.after().publishedAt()).isNull();
        assertThat(held.after().outcome()).isEqualTo(Outcome.CANCELLED);
        assertThat(held.after().cancelledBy()).isNull();
        assertThat(held.effects()).isEmpty();
        assertThat(MatchMachine.apply(held.after(), new Hold(), MatchFixtures.PUBLISH_AT).changed()).isFalse();
    }

    @Test
    void publishingTwiceIsAReplay() {
        Transition once = MatchMachine.apply(MatchFixtures.draft(), new Publish(RESPOND_DUE), MatchFixtures.PUBLISH_AT);
        Transition twice = MatchMachine.apply(once.after(), new Publish(RESPOND_DUE), MatchFixtures.PUBLISH_AT.plusSeconds(60));
        assertThat(twice.changed()).isFalse();
        assertThat(twice.after()).isEqualTo(once.after());
    }

    @Test
    void terminalStatesNeverLeaveTerminal() {
        for (MatchMachine.Rule r : MatchMachine.rules()) {
            if (r.from().isTerminal()) assertThat(r.to()).as(r.name()).isEqualTo(r.from());
        }
    }

    @Test
    void outsiderGetsMatchNotFound() {
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new Decline(OUTSIDER), T_REVEALED), MatchError.MATCH_NOT_FOUND);
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new Report(OUTSIDER, ReportCategory.SCAM), T_REVEALED), MatchError.MATCH_NOT_FOUND);
    }

    // ── guards ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void slotSelectionMustBeNonEmptySubset() {
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new SubmitSlots(A, Set.of()), T_REVEALED), MatchError.SLOTS_EMPTY);
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new SubmitSlots(A, Set.of("slot_9")), T_REVEALED), MatchError.SLOT_UNKNOWN);
        assertCode(() -> MatchMachine.apply(MatchFixtures.noOverlap(), new SubmitSlots(A, Set.of("slot_0", "x")), T_SECOND), MatchError.SLOT_UNKNOWN);
    }

    @Test
    void responseChangesFromPendingOnlyOnce() {
        MatchSnapshot m = MatchFixtures.aAccepted("slot_0");
        assertCode(() -> MatchMachine.apply(m, new SubmitSlots(A, Set.of("slot_1")), T_SECOND), MatchError.MATCH_STATE);
        assertCode(() -> MatchMachine.apply(m, new Decline(A), T_SECOND), MatchError.MATCH_STATE);
        MatchSnapshot d = MatchFixtures.declined();
        assertCode(() -> MatchMachine.apply(d, new SubmitSlots(A, Set.of("slot_0")), T_SECOND), MatchError.MATCH_STATE);
    }

    @Test
    void confirmPicksEarliestCommonSlotAtLeastSixHoursAway() {
        MatchSnapshot m = MatchMachine.apply(MatchFixtures.aAccepted("slot_0", "slot_1", "slot_2"),
                new SubmitSlots(B, Set.of("slot_2", "slot_1")), T_SECOND).after();
        assertThat(m.confirmedSlotId()).isEqualTo("slot_1");
        assertThat(m.slotStart()).isEqualTo(MatchFixtures.slot(1).startsAt());
        assertThat(m.slotEnd()).isEqualTo(m.slotStart().plus(Deadlines.SLOT_LENGTH));
        assertThat(m.feedbackDue()).isEqualTo(m.slotEnd().plus(Duration.ofHours(72)));

        Instant tooLate = MatchFixtures.slot(0).startsAt().minus(Duration.ofHours(5));
        MatchSnapshot aOnly = MatchFixtures.run(MatchFixtures.published(), new SubmitSlots(A, Set.of("slot_0", "slot_1")), T_REVEALED);
        MatchSnapshot withDue = new MatchSnapshot(aOnly.state(), aOnly.low(), aOnly.high(), aOnly.slots(), aOnly.publishedAt(),
                tooLate.plus(Duration.ofDays(1)), null, null, null, null, null, null, null, null, null);
        MatchSnapshot late = MatchMachine.apply(withDue, new SubmitSlots(B, Set.of("slot_0", "slot_1")), tooLate).after();
        assertThat(late.confirmedSlotId()).as("slot_0 is under 6h away, so slot_1 wins").isEqualTo("slot_1");

        MatchSnapshot none = MatchMachine.apply(withDue, new SubmitSlots(B, Set.of("slot_0")), tooLate).after();
        assertThat(none.state()).as("an overlap under 6h away counts as no overlap").isEqualTo(MatchState.NO_OVERLAP);
    }

    @Test
    void noOverlapSetsReselectDueToFridayNoon() {
        MatchSnapshot m = MatchFixtures.noOverlap();
        assertThat(m.reselectDue()).isEqualTo(Instant.parse("2026-11-20T04:00:00Z"));
    }

    @Test
    void eachSideReselectsOnlyOnce() {
        MatchSnapshot once = MatchFixtures.run(MatchFixtures.noOverlap(), new SubmitSlots(A, Set.of("slot_2")), T_SECOND.plusSeconds(60));
        assertThat(once.side(A).reselected()).isTrue();
        assertCode(() -> MatchMachine.apply(once, new SubmitSlots(A, Set.of("slot_0", "slot_2")), T_SECOND.plusSeconds(90)), MatchError.MATCH_STATE);
        MatchSnapshot confirmed = MatchFixtures.run(once, new SubmitSlots(B, Set.of("slot_1", "slot_2")), T_SECOND.plusSeconds(120));
        assertThat(confirmed.state()).isEqualTo(MatchState.CONFIRMED);
        assertThat(confirmed.confirmedSlotId()).isEqualTo("slot_2");
    }

    @Test
    void userActionsAfterTheirDeadlineAreRejected() {
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new Decline(A), RESPOND_DUE), MatchError.MATCH_STATE);
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new SubmitSlots(A, Set.of("slot_0")), RESPOND_DUE), MatchError.MATCH_STATE);
        MatchSnapshot n = MatchFixtures.noOverlap();
        assertCode(() -> MatchMachine.apply(n, new SubmitSlots(A, Set.of("slot_1")), n.reselectDue()), MatchError.MATCH_STATE);
        MatchSnapshot f = MatchFixtures.feedbackOpen();
        assertCode(() -> MatchMachine.apply(f, new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), f.feedbackDue()), MatchError.MATCH_STATE);
        MatchSnapshot c = MatchFixtures.confirmed();
        assertCode(() -> MatchMachine.apply(c, new Cancel(A), c.slotEnd()), MatchError.MATCH_STATE);
        MatchSnapshot closed = MatchFixtures.closedMutual();
        assertCode(() -> MatchMachine.apply(closed, new Report(A, ReportCategory.SCAM), closed.closedAt().plus(Duration.ofDays(7))), MatchError.MATCH_STATE);
    }

    @Test
    void deadlinesBeforeDueAreRejected() {
        assertCode(() -> MatchMachine.apply(MatchFixtures.published(), new RespondDeadline(), RESPOND_DUE.minusMillis(1)), MatchError.DEADLINE_NOT_REACHED);
        MatchSnapshot n = MatchFixtures.noOverlap();
        assertCode(() -> MatchMachine.apply(n, new ReselectDeadline(), n.reselectDue().minusMillis(1)), MatchError.DEADLINE_NOT_REACHED);
        MatchSnapshot c = MatchFixtures.confirmed();
        assertCode(() -> MatchMachine.apply(c, new DateEnded(), c.slotEnd().minusMillis(1)), MatchError.DEADLINE_NOT_REACHED);
        MatchSnapshot f = MatchFixtures.feedbackOpen();
        assertCode(() -> MatchMachine.apply(f, new FeedbackDeadline(), f.feedbackDue().minusMillis(1)), MatchError.DEADLINE_NOT_REACHED);
    }

    @Test
    void checkInOnlyInsideWindow() {
        MatchSnapshot c = MatchFixtures.confirmed();
        assertCode(() -> MatchMachine.apply(c, new CheckIn(A), c.slotStart().minus(Duration.ofHours(2)).minusMillis(1)), MatchError.CHECKIN_WINDOW);
        assertThat(MatchMachine.apply(c, new CheckIn(A), c.slotStart().minus(Duration.ofHours(2))).changed()).isTrue();
        assertThat(MatchMachine.apply(c, new CheckIn(A), c.slotEnd()).changed()).isTrue();
    }

    @Test
    void secondDifferentFeedbackIsRejected() {
        MatchSnapshot f = MatchFixtures.run(MatchFixtures.feedbackOpen(), new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), MatchFixtures.feedbackOpen().slotEnd());
        assertCode(() -> MatchMachine.apply(f, new SubmitFeedback(A, FeedbackAnswer.met(Again.NO)), f.slotEnd().plusSeconds(5)), MatchError.FEEDBACK_EXISTS);
    }

    // ── idempotent repeats ──────────────────────────────────────────────────────────────────────

    @Test
    void exactReplaysAreNoOps() {
        assertNoOp(MatchFixtures.published(), new Publish(RESPOND_DUE), T_REVEALED);
        assertNoOp(MatchFixtures.declined(), new Decline(A), T_SECOND);
        assertNoOp(MatchFixtures.aAccepted("slot_0"), new SubmitSlots(A, Set.of("slot_0")), T_SECOND);
        assertNoOp(MatchFixtures.confirmed(), new SubmitSlots(B, Set.of("slot_1", "slot_2")), T_SECOND.plusSeconds(1));
        MatchSnapshot checked = MatchFixtures.run(MatchFixtures.confirmed(), new CheckIn(A), MatchFixtures.confirmed().slotStart());
        assertNoOp(checked, new CheckIn(A), checked.slotStart().plusSeconds(60));
        assertNoOp(MatchFixtures.cancelled(), new Cancel(A), T_SECOND.plus(Duration.ofHours(2)));
        MatchSnapshot closed = MatchFixtures.closedMutual();
        assertNoOp(closed, new SubmitFeedback(B, FeedbackAnswer.met(Again.YES)), closed.closedAt().plusSeconds(1));
        assertNoOp(MatchFixtures.halted(), new Report(A, ReportCategory.SCAM), T_SECOND);
        assertNoOp(MatchFixtures.expired(), new RespondDeadline(), RESPOND_DUE.plusSeconds(60));
        assertNoOp(MatchFixtures.unscheduled(), new ReselectDeadline(), RESPOND_DUE.plus(Duration.ofDays(1)));
        assertNoOp(MatchFixtures.feedbackOpen(), new DateEnded(), MatchFixtures.feedbackOpen().slotEnd().plusSeconds(1));
        assertNoOp(closed, new FeedbackDeadline(), closed.feedbackDue());
    }

    @Test
    void applyingTheSameEventTwiceEqualsApplyingItOnce() {
        for (Case k : allowedCases()) {
            MatchSnapshot once = MatchMachine.apply(k.given().get(), k.event(), k.at().get()).after();
            Transition again = MatchMachine.apply(once, k.event(), k.at().get());
            assertThat(again.changed()).as(k.rule() + " repeated").isFalse();
            assertThat(again.after()).as(k.rule()).isEqualTo(once);
            assertThat(again.effects()).isEmpty();
        }
    }

    // ── effects ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void expiryRecordsOnlyTheSilentSide() {
        Transition t = MatchMachine.apply(MatchFixtures.aAccepted("slot_0"), new RespondDeadline(), RESPOND_DUE);
        assertThat(t.effects()).containsExactly(new Effect.RecordNoResponse(B));
        assertThat(t.after().outcome()).isEqualTo(Outcome.EXPIRED);
        assertThat(t.after().closedAt()).isEqualTo(RESPOND_DUE);
    }

    @Test
    void feedbackExpiryJudgesMissingSideAsNoFeedback() {
        MatchSnapshot f = MatchFixtures.feedbackOpen();
        MatchSnapshot oneSaid = MatchFixtures.run(f, new SubmitFeedback(A, FeedbackAnswer.of(Met.NO_SHOW)), f.slotEnd());
        Transition t = MatchMachine.apply(oneSaid, new FeedbackDeadline(), f.feedbackDue());
        assertThat(t.after().outcome()).isEqualTo(Outcome.NO_SHOW);
        assertThat(t.effects()).containsExactly(new Effect.RecordNoShow(B));

        Transition silent = MatchMachine.apply(f, new FeedbackDeadline(), f.feedbackDue());
        assertThat(silent.after().outcome()).isEqualTo(Outcome.NO_FEEDBACK);
        assertThat(silent.effects()).isEmpty();
    }

    @Test
    void unsafeFeedbackFilesUrgentReportWithoutHaltingTheMatch() {
        MatchSnapshot f = MatchFixtures.feedbackOpen();
        FeedbackAnswer unsafe = new FeedbackAnswer(Met.MET, 2, Again.NO, false);
        Transition t = MatchMachine.apply(f, new SubmitFeedback(A, unsafe), f.slotEnd());
        assertThat(t.to()).isEqualTo(MatchState.FEEDBACK_OPEN);
        assertThat(t.effects()).containsExactly(
                new Effect.CreateReport(A, B, ReportCategory.OFFLINE, true), new Effect.SuspendPendingReview(B));
    }

    @Test
    void nonUrgentReportDoesNotSuspend() {
        Transition t = MatchMachine.apply(MatchFixtures.published(), new Report(A, ReportCategory.FAKE), T_REVEALED);
        assertThat(t.effects()).containsExactly(new Effect.CreateReport(A, B, ReportCategory.FAKE, false));
        assertThat(t.after().reporterId()).isEqualTo(A);
        assertThat(t.after().outcome()).isEqualTo(Outcome.HALTED);
    }

    @Test
    void disputedFeedbackIsRecordedWithoutPenalty() {
        MatchSnapshot f = MatchFixtures.feedbackOpen();
        MatchSnapshot a = MatchFixtures.run(f, new SubmitFeedback(A, FeedbackAnswer.of(Met.NO_SHOW)), f.slotEnd());
        Transition t = MatchMachine.apply(a, new SubmitFeedback(B, FeedbackAnswer.of(Met.NO_SHOW)), f.slotEnd().plusSeconds(1));
        assertThat(t.after().outcome()).isEqualTo(Outcome.DISPUTED);
        assertThat(t.effects()).containsExactly(new Effect.RecordDispute(A, B));
    }

    @Test
    void reportedCategoryIdsRoundTrip() {
        for (ReportCategory c : ReportCategory.values()) assertThat(ReportCategory.fromId(c.id())).isEqualTo(c);
        assertCode(() -> ReportCategory.fromId("spam"), MatchError.CATEGORY_UNKNOWN);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static void assertNoOp(MatchSnapshot m, MatchEvent e, Instant now) {
        Transition t = MatchMachine.apply(m, e, now);
        assertThat(t.changed()).as(e.toString()).isFalse();
        assertThat(t.after()).isEqualTo(m);
        assertThat(t.effects()).isEmpty();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, MatchError code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(MatchRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
