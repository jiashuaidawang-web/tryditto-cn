package cn.aiditto.domain.match;

import static cn.aiditto.domain.match.MatchState.CANCELLED;
import static cn.aiditto.domain.match.MatchState.CLOSED;
import static cn.aiditto.domain.match.MatchState.CONFIRMED;
import static cn.aiditto.domain.match.MatchState.DECLINED;
import static cn.aiditto.domain.match.MatchState.DRAFT;
import static cn.aiditto.domain.match.MatchState.EXPIRED;
import static cn.aiditto.domain.match.MatchState.FEEDBACK_OPEN;
import static cn.aiditto.domain.match.MatchState.HALTED;
import static cn.aiditto.domain.match.MatchState.NO_OVERLAP;
import static cn.aiditto.domain.match.MatchState.PUBLISHED;
import static cn.aiditto.domain.match.MatchState.UNSCHEDULED;

import cn.aiditto.domain.match.MatchEvent.Cancel;
import cn.aiditto.domain.match.MatchEvent.CheckIn;
import cn.aiditto.domain.match.MatchEvent.DateEnded;
import cn.aiditto.domain.match.MatchEvent.Decline;
import cn.aiditto.domain.match.MatchEvent.FeedbackDeadline;
import cn.aiditto.domain.match.MatchEvent.Publish;
import cn.aiditto.domain.match.MatchEvent.Report;
import cn.aiditto.domain.match.MatchEvent.ReselectDeadline;
import cn.aiditto.domain.match.MatchEvent.RespondDeadline;
import cn.aiditto.domain.match.MatchEvent.SubmitFeedback;
import cn.aiditto.domain.match.MatchEvent.SubmitSlots;
import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.slot.SlotRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The match state machine as a transition table (技术方案 §5.1). One row = current state, event,
 * guard, next state, effects. Rows are tried top to bottom; the first row whose guard holds wins.
 *
 * <p>Before the table runs, an exact replay of an already-recorded fact (same user, same payload; or a
 * deadline whose result is already in place) returns {@code changed=false} instead of an error, so
 * retries and repeated ticker runs are harmless.
 */
public final class MatchMachine {

    record Ctx(MatchSnapshot m, MatchEvent e, Instant now) {
        long user() {
            return ((MatchEvent.ByUser) e).userId();
        }

        SideSnapshot me() {
            return m.side(user());
        }

        SideSnapshot partner() {
            return m.partnerOf(user());
        }
    }

    @FunctionalInterface
    interface Guard {
        boolean test(Ctx c);
    }

    @FunctionalInterface
    interface Action {
        Step apply(Ctx c);
    }

    record Step(MatchSnapshot after, List<Effect> effects) {}

    /** One row of the table. {@code to} is declarative; the machine checks the action honours it. */
    public record Rule(String name, MatchState from, Class<? extends MatchEvent> on, MatchState to,
                       Guard guard, Action action) {}

    private static final Guard ALWAYS = c -> true;

    private static final List<Rule> RULES = buildRules();

    private MatchMachine() {}

    public static List<Rule> rules() {
        return RULES;
    }

    /** Event types with at least one row from {@code state}; everything else is always rejected. */
    public static Set<Class<? extends MatchEvent>> allowedEvents(MatchState state) {
        Set<Class<? extends MatchEvent>> out = new LinkedHashSet<>();
        for (Rule r : RULES) if (r.from() == state) out.add(r.on());
        return out;
    }

    /** Next states reachable from {@code state} by any row. */
    public static Set<MatchState> successors(MatchState state) {
        Set<MatchState> out = EnumSet.noneOf(MatchState.class);
        for (Rule r : RULES) if (r.from() == state) out.add(r.to());
        return out;
    }

    /**
     * Applies {@code event} at time {@code now}.
     *
     * @throws MatchRuleException when the event is not allowed now; {@link MatchRuleException#code()}
     *     says why ({@code MATCH_STATE}, {@code SLOTS_EMPTY}, {@code CHECKIN_WINDOW}, …)
     */
    public static Transition apply(MatchSnapshot m, MatchEvent event, Instant now) {
        Ctx c = new Ctx(m, event, now);
        if (event instanceof MatchEvent.ByUser u && !m.involves(u.userId())) {
            throw new MatchRuleException(MatchError.MATCH_NOT_FOUND, "user " + u.userId() + " is not in this match");
        }
        if (isReplay(c)) {
            return Transition.unchanged(m);
        }
        List<Rule> candidates = new ArrayList<>();
        for (Rule r : RULES) {
            if (r.from() == m.state() && r.on().isInstance(event)) candidates.add(r);
        }
        if (candidates.isEmpty()) {
            throw new MatchRuleException(MatchError.MATCH_STATE,
                    event.getClass().getSimpleName() + " is not allowed in " + m.state().dbValue());
        }
        if (event instanceof SubmitSlots s) {
            Optional<SlotRules.SelectionProblem> problem = SlotRules.checkSelection(m.slots(), s.slotIds());
            if (problem.isPresent()) {
                throw new MatchRuleException(
                        problem.get() == SlotRules.SelectionProblem.EMPTY ? MatchError.SLOTS_EMPTY : MatchError.SLOT_UNKNOWN,
                        "slots " + s.slotIds() + " are not a non-empty subset of the proposed slots");
            }
        }
        for (Rule r : candidates) {
            if (r.guard().test(c)) {
                Step step = r.action().apply(c);
                if (step.after().state() != r.to()) {
                    throw new IllegalStateException("rule " + r.name() + " produced " + step.after().state());
                }
                return new Transition(m.state(), step.after(), step.effects(), true, r.name());
            }
        }
        throw rejection(c);
    }

    // ── replay detection ────────────────────────────────────────────────────────────────────────

    private static boolean isReplay(Ctx c) {
        MatchSnapshot m = c.m();
        MatchEvent e = c.e();
        if (e instanceof Publish) return m.publishedAt() != null;
        if (e instanceof Decline) return c.me().response() == Response.DECLINE;
        if (e instanceof SubmitSlots s) {
            return c.me().response() == Response.ACCEPT && c.me().slotIds().equals(s.slotIds());
        }
        if (e instanceof CheckIn) return c.me().checkedInAt() != null;
        if (e instanceof Cancel) return m.state() == CANCELLED && Long.valueOf(c.user()).equals(m.cancelledBy());
        if (e instanceof SubmitFeedback f) return f.feedback().equals(c.me().feedback());
        if (e instanceof Report) return Long.valueOf(c.user()).equals(m.reporterId());
        if (e instanceof RespondDeadline) return m.state() == EXPIRED;
        if (e instanceof ReselectDeadline) return m.state() == UNSCHEDULED;
        if (e instanceof DateEnded) return m.state() == FEEDBACK_OPEN;
        if (e instanceof FeedbackDeadline) return m.state() == CLOSED;
        return false;
    }

    private static MatchRuleException rejection(Ctx c) {
        MatchEvent e = c.e();
        String where = " (state " + c.m().state().dbValue() + ")";
        if (e instanceof MatchEvent.Deadline) {
            return new MatchRuleException(MatchError.DEADLINE_NOT_REACHED, e.getClass().getSimpleName() + " is not due yet" + where);
        }
        if (e instanceof CheckIn) {
            return new MatchRuleException(MatchError.CHECKIN_WINDOW, "check-in opens 2h before the start and closes at the end");
        }
        if (e instanceof SubmitFeedback && c.me().feedback() != null) {
            return new MatchRuleException(MatchError.FEEDBACK_EXISTS, "feedback already submitted");
        }
        return new MatchRuleException(MatchError.MATCH_STATE, e.getClass().getSimpleName() + " is not allowed now" + where);
    }

    // ── the table ───────────────────────────────────────────────────────────────────────────────

    private static List<Rule> buildRules() {
        List<Rule> t = new ArrayList<>();

        t.add(new Rule("publish", DRAFT, Publish.class, PUBLISHED, ALWAYS,
                c -> step(c.m().published(c.now(), ((Publish) c.e()).respondDue()).withState(PUBLISHED))));

        // published
        t.add(new Rule("decline", PUBLISHED, Decline.class, DECLINED,
                c -> c.me().response() == Response.PENDING && beforeRespondDue(c),
                c -> step(c.m().withSide(c.me().withResponse(Response.DECLINE)).withState(DECLINED)
                        .closing(c.now(), Outcome.DECLINED))));
        t.add(new Rule("slots.first", PUBLISHED, SubmitSlots.class, PUBLISHED,
                c -> c.me().response() == Response.PENDING && c.partner().response() == Response.PENDING && beforeRespondDue(c),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), false)))));
        t.add(new Rule("slots.second.overlap", PUBLISHED, SubmitSlots.class, CONFIRMED,
                c -> c.me().response() == Response.PENDING && c.partner().response() == Response.ACCEPT
                        && beforeRespondDue(c) && pick(c, c.partner().slotIds()).isPresent(),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), false))
                        .scheduled(pick(c, c.partner().slotIds()).orElseThrow()).withState(CONFIRMED))));
        t.add(new Rule("slots.second.noOverlap", PUBLISHED, SubmitSlots.class, NO_OVERLAP,
                c -> c.me().response() == Response.PENDING && c.partner().response() == Response.ACCEPT
                        && beforeRespondDue(c) && pick(c, c.partner().slotIds()).isEmpty(),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), false))
                        .withReselectDue(Deadlines.reselectDue(c.m().respondDue(), c.m().slots()))
                        .withState(NO_OVERLAP))));
        t.add(new Rule("respond.expired", PUBLISHED, RespondDeadline.class, EXPIRED,
                c -> !c.now().isBefore(c.m().respondDue()),
                c -> {
                    List<Effect> fx = new ArrayList<>();
                    for (SideSnapshot s : List.of(c.m().low(), c.m().high())) {
                        if (s.response() == Response.PENDING) fx.add(new Effect.RecordNoResponse(s.userId()));
                    }
                    return new Step(c.m().withState(EXPIRED).closing(c.now(), Outcome.EXPIRED), fx);
                }));

        // no_overlap: each side may reselect once
        t.add(new Rule("reselect.overlap", NO_OVERLAP, SubmitSlots.class, CONFIRMED,
                c -> !c.me().reselected() && beforeReselectDue(c) && pick(c, c.partner().slotIds()).isPresent(),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), true))
                        .scheduled(pick(c, c.partner().slotIds()).orElseThrow()).withState(CONFIRMED))));
        t.add(new Rule("reselect.bothFailed", NO_OVERLAP, SubmitSlots.class, UNSCHEDULED,
                c -> !c.me().reselected() && beforeReselectDue(c) && pick(c, c.partner().slotIds()).isEmpty()
                        && c.partner().reselected(),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), true)).withState(UNSCHEDULED)
                        .closing(c.now(), Outcome.UNSCHEDULED))));
        t.add(new Rule("reselect.waiting", NO_OVERLAP, SubmitSlots.class, NO_OVERLAP,
                c -> !c.me().reselected() && beforeReselectDue(c) && pick(c, c.partner().slotIds()).isEmpty()
                        && !c.partner().reselected(),
                c -> step(c.m().withSide(c.me().withSlots(slotsOf(c), true)))));
        t.add(new Rule("reselect.expired", NO_OVERLAP, ReselectDeadline.class, UNSCHEDULED,
                c -> !c.now().isBefore(c.m().reselectDue()),
                c -> step(c.m().withState(UNSCHEDULED).closing(c.now(), Outcome.UNSCHEDULED))));

        // confirmed
        t.add(new Rule("checkin", CONFIRMED, CheckIn.class, CONFIRMED,
                c -> Deadlines.inCheckInWindow(c.m().slotStart(), c.m().slotEnd(), c.now()),
                c -> step(c.m().withSide(c.me().withCheckIn(c.now())))));
        t.add(new Rule("cancel.early", CONFIRMED, Cancel.class, CANCELLED,
                c -> c.now().isBefore(c.m().slotEnd()) && !Deadlines.isLateCancel(c.m().slotStart(), c.now()),
                c -> step(c.m().cancelledBy(c.user()).withState(CANCELLED).closing(c.now(), Outcome.CANCELLED))));
        t.add(new Rule("cancel.late", CONFIRMED, Cancel.class, CANCELLED,
                c -> c.now().isBefore(c.m().slotEnd()) && Deadlines.isLateCancel(c.m().slotStart(), c.now()),
                c -> new Step(c.m().cancelledBy(c.user()).withState(CANCELLED).closing(c.now(), Outcome.CANCELLED),
                        List.of(new Effect.RecordLateCancel(c.user())))));
        t.add(new Rule("date.ended", CONFIRMED, DateEnded.class, FEEDBACK_OPEN,
                c -> !c.now().isBefore(c.m().slotEnd()),
                c -> step(c.m().withState(FEEDBACK_OPEN))));

        // feedback_open
        t.add(new Rule("feedback.first", FEEDBACK_OPEN, SubmitFeedback.class, FEEDBACK_OPEN,
                c -> c.me().feedback() == null && c.partner().feedback() == null && beforeFeedbackDue(c),
                c -> new Step(c.m().withSide(c.me().withFeedback(fb(c))), unsafeReport(c))));
        t.add(new Rule("feedback.last", FEEDBACK_OPEN, SubmitFeedback.class, CLOSED,
                c -> c.me().feedback() == null && c.partner().feedback() != null && beforeFeedbackDue(c),
                c -> {
                    MatchSnapshot withMine = c.m().withSide(c.me().withFeedback(fb(c)));
                    List<Effect> fx = new ArrayList<>(unsafeReport(c));
                    return close(withMine, c.now(), fx);
                }));
        t.add(new Rule("feedback.expired", FEEDBACK_OPEN, FeedbackDeadline.class, CLOSED,
                c -> !c.now().isBefore(c.m().feedbackDue()),
                c -> close(c.m(), c.now(), new ArrayList<>())));

        // reports: any non-terminal state halts; closed matches accept reports for 7 more days
        for (MatchState s : List.of(DRAFT, PUBLISHED, NO_OVERLAP, CONFIRMED, FEEDBACK_OPEN)) {
            t.add(new Rule("report.halt", s, Report.class, HALTED, ALWAYS,
                    c -> new Step(c.m().reportedBy(c.user()).withState(HALTED).closing(c.now(), Outcome.HALTED),
                            reportEffects(c))));
        }
        t.add(new Rule("report.afterClose", CLOSED, Report.class, CLOSED,
                c -> c.m().reporterId() == null && c.now().isBefore(c.m().closedAt().plus(Deadlines.REPORT_AFTER_CLOSE)),
                c -> new Step(c.m().reportedBy(c.user()), reportEffects(c))));

        return List.copyOf(t);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static Step step(MatchSnapshot after) {
        return new Step(after, List.of());
    }

    private static Set<String> slotsOf(Ctx c) {
        return ((SubmitSlots) c.e()).slotIds();
    }

    private static FeedbackAnswer fb(Ctx c) {
        return ((SubmitFeedback) c.e()).feedback();
    }

    private static Optional<Slot> pick(Ctx c, Set<String> partnerSlots) {
        return SlotRules.pickConfirmed(c.m().slots(), slotsOf(c), partnerSlots, c.now());
    }

    private static boolean beforeRespondDue(Ctx c) {
        return c.m().respondDue() == null || c.now().isBefore(c.m().respondDue());
    }

    private static boolean beforeReselectDue(Ctx c) {
        return c.m().reselectDue() == null || c.now().isBefore(c.m().reselectDue());
    }

    private static boolean beforeFeedbackDue(Ctx c) {
        return c.m().feedbackDue() == null || c.now().isBefore(c.m().feedbackDue());
    }

    private static List<Effect> unsafeReport(Ctx c) {
        if (!fb(c).reportsUnsafe()) return List.of();
        long reported = c.partner().userId();
        return List.of(new Effect.CreateReport(c.user(), reported, ReportCategory.OFFLINE, true),
                new Effect.SuspendPendingReview(reported));
    }

    private static List<Effect> reportEffects(Ctx c) {
        ReportCategory cat = ((Report) c.e()).category();
        long reported = c.partner().userId();
        List<Effect> fx = new ArrayList<>();
        fx.add(new Effect.CreateReport(c.user(), reported, cat, false));
        if (cat.urgent()) fx.add(new Effect.SuspendPendingReview(reported));
        return fx;
    }

    private static Step close(MatchSnapshot m, Instant now, List<Effect> fx) {
        Outcome.Judgement j = Outcome.judge(m.low().feedback(), m.high().feedback());
        long a = m.low().userId();
        long b = m.high().userId();
        switch (j.outcome()) {
            case MUTUAL -> fx.add(new Effect.UnlockContacts(a, b));
            case DISPUTED -> fx.add(new Effect.RecordDispute(a, b));
            default -> { }
        }
        if (j.noShowA()) fx.add(new Effect.RecordNoShow(a));
        if (j.noShowB()) fx.add(new Effect.RecordNoShow(b));
        return new Step(m.withState(CLOSED).closing(now, j.outcome()), fx);
    }
}
