package cn.aiditto.domain.match;

import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.domain.slot.Slot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything the state machine reads about one match: the {@code match} row, both {@code match_side}
 * rows, both feedback rows and the proposed slots from {@code date_plan}. Immutable; every transition
 * returns a new snapshot that the service writes back.
 */
public record MatchSnapshot(
        MatchState state,
        SideSnapshot low,
        SideSnapshot high,
        List<Slot> slots,
        Instant publishedAt,
        Instant respondDue,
        Instant reselectDue,
        String confirmedSlotId,
        Instant slotStart,
        Instant slotEnd,
        Instant feedbackDue,
        Outcome outcome,
        Long reporterId,
        Long cancelledBy,
        Instant closedAt) {

    public MatchSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(high, "high");
        if (low.userId() >= high.userId()) {
            throw new IllegalArgumentException("low.userId must be < high.userId");
        }
        slots = List.copyOf(slots);
    }

    /** A freshly solved pair: both sides pending, nothing scheduled. */
    public static MatchSnapshot draft(long userA, long userB, List<Slot> slots) {
        long lo = Math.min(userA, userB);
        long hi = Math.max(userA, userB);
        return new MatchSnapshot(MatchState.DRAFT, SideSnapshot.pending(lo), SideSnapshot.pending(hi), slots,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    public boolean involves(long userId) {
        return low.userId() == userId || high.userId() == userId;
    }

    public SideSnapshot side(long userId) {
        if (low.userId() == userId) return low;
        if (high.userId() == userId) return high;
        throw new MatchRuleException(MatchError.MATCH_NOT_FOUND, "user " + userId + " is not in this match");
    }

    public SideSnapshot partnerOf(long userId) {
        return side(userId) == low ? high : low;
    }

    public MatchSnapshot withSide(SideSnapshot s) {
        if (s.userId() == low.userId()) return with(state, s, high);
        if (s.userId() == high.userId()) return with(state, low, s);
        throw new IllegalArgumentException("side does not belong to this match");
    }

    public MatchSnapshot withState(MatchState next) {
        return with(next, low, high);
    }

    private MatchSnapshot with(MatchState st, SideSnapshot lo, SideSnapshot hi) {
        return new MatchSnapshot(st, lo, hi, slots, publishedAt, respondDue, reselectDue, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, outcome, reporterId, cancelledBy, closedAt);
    }

    public MatchSnapshot published(Instant at, Instant due) {
        return new MatchSnapshot(state, low, high, slots, at, due, reselectDue, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, outcome, reporterId, cancelledBy, closedAt);
    }

    public MatchSnapshot withReselectDue(Instant due) {
        return new MatchSnapshot(state, low, high, slots, publishedAt, respondDue, due, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, outcome, reporterId, cancelledBy, closedAt);
    }

    public MatchSnapshot scheduled(Slot slot) {
        return new MatchSnapshot(state, low, high, slots, publishedAt, respondDue, reselectDue, slot.id(),
                slot.startsAt(), slot.endsAt(), Deadlines.feedbackDue(slot.endsAt()),
                outcome, reporterId, cancelledBy, closedAt);
    }

    /** Demo helper: moves the confirmed date in time without touching anything else (技术方案 §6.10). */
    public MatchSnapshot rescheduledTo(Instant start) {
        return scheduled(new Slot(confirmedSlotId == null ? "demo" : confirmedSlotId, start));
    }

    public MatchSnapshot closing(Instant at, Outcome o) {
        return new MatchSnapshot(state, low, high, slots, publishedAt, respondDue, reselectDue, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, o, reporterId, cancelledBy, at);
    }

    public MatchSnapshot reportedBy(long userId) {
        return new MatchSnapshot(state, low, high, slots, publishedAt, respondDue, reselectDue, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, outcome, userId, cancelledBy, closedAt);
    }

    public MatchSnapshot cancelledBy(long userId) {
        return new MatchSnapshot(state, low, high, slots, publishedAt, respondDue, reselectDue, confirmedSlotId,
                slotStart, slotEnd, feedbackDue, outcome, reporterId, userId, closedAt);
    }
}
