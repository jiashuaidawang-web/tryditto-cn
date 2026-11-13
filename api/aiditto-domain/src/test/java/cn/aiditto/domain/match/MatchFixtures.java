package cn.aiditto.domain.match;

import cn.aiditto.domain.match.FeedbackAnswer.Again;
import cn.aiditto.domain.match.MatchEvent.Cancel;
import cn.aiditto.domain.match.MatchEvent.DateEnded;
import cn.aiditto.domain.match.MatchEvent.Decline;
import cn.aiditto.domain.match.MatchEvent.Publish;
import cn.aiditto.domain.match.MatchEvent.Report;
import cn.aiditto.domain.match.MatchEvent.ReselectDeadline;
import cn.aiditto.domain.match.MatchEvent.RespondDeadline;
import cn.aiditto.domain.match.MatchEvent.SubmitFeedback;
import cn.aiditto.domain.match.MatchEvent.SubmitSlots;
import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.slot.SlotRules;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** A canonical week (drop Wed 2026-11-18 19:00 Shanghai) and one snapshot per match state, built by real transitions. */
final class MatchFixtures {
    static final long A = 1;
    static final long B = 2;
    static final long OUTSIDER = 99;

    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);
    static final Instant PUBLISH_AT = Instant.parse("2026-11-18T11:00:00Z");
    static final Instant RESPOND_DUE = Deadlines.respondDue(PUBLISH_AT);
    static final List<Slot> SLOTS = SlotRules.propose(RESPOND_DUE);
    static final Instant T_REVEALED = PUBLISH_AT.plus(Duration.ofHours(1));
    static final Instant T_SECOND = PUBLISH_AT.plus(Duration.ofHours(3));

    private MatchFixtures() {}

    static Slot slot(int i) {
        return SLOTS.get(i);
    }

    static MatchSnapshot draft() {
        return MatchSnapshot.draft(A, B, SLOTS);
    }

    static MatchSnapshot run(MatchSnapshot m, MatchEvent e, Instant now) {
        return MatchMachine.apply(m, e, now).after();
    }

    static MatchSnapshot published() {
        return run(draft(), new Publish(RESPOND_DUE), PUBLISH_AT);
    }

    static MatchSnapshot aAccepted(String... ids) {
        return run(published(), new SubmitSlots(A, Set.of(ids)), T_REVEALED);
    }

    static MatchSnapshot declined() {
        return run(published(), new Decline(A), T_REVEALED);
    }

    static MatchSnapshot noOverlap() {
        return run(aAccepted("slot_0"), new SubmitSlots(B, Set.of("slot_1")), T_SECOND);
    }

    static MatchSnapshot expired() {
        return run(published(), new RespondDeadline(), RESPOND_DUE);
    }

    static MatchSnapshot unscheduled() {
        MatchSnapshot m = noOverlap();
        return run(m, new ReselectDeadline(), m.reselectDue());
    }

    /** Both picked slot_1 (Saturday 15:30) among others. */
    static MatchSnapshot confirmed() {
        return run(aAccepted("slot_0", "slot_1"), new SubmitSlots(B, Set.of("slot_1", "slot_2")), T_SECOND);
    }

    static MatchSnapshot cancelled() {
        return run(confirmed(), new Cancel(A), T_SECOND.plus(Duration.ofHours(1)));
    }

    static MatchSnapshot feedbackOpen() {
        MatchSnapshot m = confirmed();
        return run(m, new DateEnded(), m.slotEnd());
    }

    static MatchSnapshot closedMutual() {
        MatchSnapshot m = feedbackOpen();
        Instant t = m.slotEnd().plus(Duration.ofHours(2));
        m = run(m, new SubmitFeedback(A, FeedbackAnswer.met(Again.YES)), t);
        return run(m, new SubmitFeedback(B, FeedbackAnswer.met(Again.YES)), t.plus(Duration.ofHours(1)));
    }

    static MatchSnapshot halted() {
        return run(published(), new Report(A, ReportCategory.SCAM), T_REVEALED);
    }

    /** One representative snapshot per state. */
    static MatchSnapshot inState(MatchState s) {
        return switch (s) {
            case DRAFT -> draft();
            case PUBLISHED -> published();
            case DECLINED -> declined();
            case NO_OVERLAP -> noOverlap();
            case EXPIRED -> expired();
            case UNSCHEDULED -> unscheduled();
            case CONFIRMED -> confirmed();
            case CANCELLED -> cancelled();
            case FEEDBACK_OPEN -> feedbackOpen();
            case CLOSED -> closedMutual();
            case HALTED -> halted();
        };
    }

    /** A time at which nothing in {@link #inState} is blocked by a deadline guard. */
    static Instant calmTime(MatchSnapshot m) {
        return switch (m.state()) {
            case DRAFT, PUBLISHED -> T_REVEALED;
            case NO_OVERLAP -> T_SECOND.plus(Duration.ofMinutes(5));
            case CONFIRMED -> m.slotStart().minus(Duration.ofDays(1));
            case FEEDBACK_OPEN -> m.slotEnd().plus(Duration.ofHours(1));
            default -> m.closedAt() == null ? T_REVEALED : m.closedAt().plus(Duration.ofHours(1));
        };
    }
}
