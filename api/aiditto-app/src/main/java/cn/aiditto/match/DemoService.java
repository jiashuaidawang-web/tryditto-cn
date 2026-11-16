package cn.aiditto.match;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.match.FeedbackAnswer;
import cn.aiditto.domain.match.MatchEvent;
import cn.aiditto.domain.match.MatchSnapshot;
import cn.aiditto.domain.match.ReportCategory;
import cn.aiditto.domain.matching.GenderPref;
import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.domain.slot.Slot;
import cn.aiditto.domain.slot.SlotRules;
import cn.aiditto.domain.week.WeekCalendar;
import cn.aiditto.domain.week.WeekState;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The demo panel. Every write is limited to demo accounts and synthetic users; a real user's rows are
 * never updated or deleted here.
 */
@Service
public class DemoService {
    private final MatchDb db;
    private final People people;
    private final MatchFlow flow;
    private final MatchCopyWriter copies;
    private final JdbcTemplate jdbc;

    public DemoService(MatchDb db, People people, MatchFlow flow, MatchCopyWriter copies, JdbcTemplate jdbc) {
        this.db = db;
        this.people = people;
        this.flow = flow;
        this.copies = copies;
        this.jdbc = jdbc;
    }

    @Transactional
    public void setStage(long userId, String stage, String partnerHandle, Instant now) {
        requirePlayable(userId);
        long partner = partner(userId, partnerHandle);
        requireNotReal(partner);
        clearPlayableMatches(userId);
        now = Times.millis(now);
        if ("waiting".equals(stage)) {
            ensureWeek(userId, WeekState.LOCKED, now);
            return;
        }
        if ("emptyWeek".equals(stage)) {
            ensureWeek(userId, WeekState.PUBLISHED, now);
            return;
        }
        long weekId = ensureWeek(userId, WeekState.PUBLISHED, now);
        long matchId = createMatch(weekId, userId, partner, now);
        switch (stage) {
            case "revealed" -> { /* published */ }
            case "declined" -> {
                db.saveDecline(matchId, userId, Json.write(List.of("noTime")), null);
                flow.apply(userId, matchId, new MatchEvent.Decline(userId), now, null);
            }
            case "awaitingPartner" -> flow.apply(userId, matchId, new MatchEvent.SubmitSlots(userId, Set.of("slot_0", "slot_1")), now, null);
            case "confirmed" -> confirm(matchId, userId, partner, now);
            case "dateDay" -> {
                confirm(matchId, userId, partner, now);
                flow.reschedule(matchId, now.plus(Duration.ofHours(1)), now);
            }
            case "checkedIn" -> {
                confirm(matchId, userId, partner, now);
                flow.reschedule(matchId, now.plus(Duration.ofHours(1)), now);
                flow.apply(userId, matchId, new MatchEvent.CheckIn(userId), now, null);
            }
            case "feedback" -> openFeedback(matchId, userId, partner, now);
            case "awaitingFeedback" -> {
                openFeedback(matchId, userId, partner, now);
                flow.apply(userId, matchId, new MatchEvent.SubmitFeedback(userId, yes()), now, null);
            }
            case "mutual" -> {
                openFeedback(matchId, userId, partner, now);
                flow.apply(userId, matchId, new MatchEvent.SubmitFeedback(userId, yes()), now, null);
                flow.apply(partner, matchId, new MatchEvent.SubmitFeedback(partner, yes()), now, null);
            }
            case "closed" -> {
                openFeedback(matchId, userId, partner, now);
                flow.apply(userId, matchId, new MatchEvent.SubmitFeedback(userId, yes()), now, null);
                flow.apply(partner, matchId, new MatchEvent.SubmitFeedback(partner, no()), now, null);
            }
            case "reported" -> flow.apply(userId, matchId, new MatchEvent.Report(userId, ReportCategory.OTHER), now, "demo");
            default -> throw DomainException.invalid("stage", "没有这个阶段");
        }
    }

    @Transactional
    public void setPartner(long userId, String handle, Instant now) {
        requirePlayable(userId);
        String stage = currentStage(userId, now);
        if (stage == null || "waiting".equals(stage) || "emptyWeek".equals(stage)) return;
        setStage(userId, stage, handle, now);
    }

    @Transactional
    public void setPartnerWantsAgain(long userId, boolean wants, Instant now) {
        long matchId = openMatch(userId);
        db.setWantsAgain(matchId, wants);
    }

    @Transactional
    public void partnerAction(long userId, String action, Instant now) {
        long matchId = openMatch(userId);
        now = Times.millis(now);
        MatchSnapshot snap = db.lockMatch(matchId).snapshot();
        long partner = snap.partnerOf(userId).userId();
        requireNotReal(partner);
        switch (action) {
            case "confirm" -> {
                String slot = firstOverlap(snap, userId);
                if (slot == null) throw new DomainException(ErrorCode.MATCH_STATE, "还没有选时段");
                flow.apply(partner, matchId, new MatchEvent.SubmitSlots(partner, Set.of(slot)), now, null);
            }
            case "disjoint" -> {
                String slot = firstMissing(snap, userId);
                if (slot == null) throw new DomainException(ErrorCode.MATCH_STATE, "没有不重叠的时段");
                flow.apply(partner, matchId, new MatchEvent.SubmitSlots(partner, Set.of(slot)), now, null);
            }
            case "feedback" -> {
                FeedbackAnswer answer = db.wantsAgain(matchId) ? yes() : no();
                flow.apply(partner, matchId, new MatchEvent.SubmitFeedback(partner, answer), now, null);
            }
            default -> throw DomainException.invalid("action", "没有这个操作");
        }
    }

    @Transactional
    public void time(long userId, String to, Instant now) {
        long matchId = openMatch(userId);
        now = Times.millis(now);
        if ("dateDay".equals(to)) flow.reschedule(matchId, now.plus(Duration.ofHours(1)), now);
        else if ("ended".equals(to)) {
            flow.reschedule(matchId, now.minus(Duration.ofHours(2)), now);
            flow.apply(userId, matchId, new MatchEvent.DateEnded(), now, null);
        } else throw DomainException.invalid("to", "只能是 dateDay 或 ended");
    }

    @Transactional
    public void resetMe(long userId) {
        if ("real".equals(people.cohortOf(userId))) throw new DomainException(ErrorCode.FORBIDDEN, "不能重置真实用户");
        clearPlayableMatches(userId);
        MatchDb.WeekRow week = db.demoWeek(userId);
        if (week != null) db.setWeekState(week.id(), WeekState.LOCKED, Times.millis(Instant.now()));
    }

    /** Drops matches whose two people are both demo or synthetic. Real users' matches and answers stay. */
    @Transactional
    public int resetNonReal(Instant now) {
        List<Long> ids = jdbc.query("""
                SELECT m.id FROM "match" m
                JOIN "user" a ON a.id = m.user_low
                JOIN "user" b ON b.id = m.user_high
                WHERE a.cohort <> 'real' AND b.cohort <> 'real'
                """, (rs, n) -> rs.getLong(1));
        for (long id : ids) db.deleteMatch(id);
        jdbc.update("UPDATE week SET state = 'locked', updated_at = ? WHERE trigger_kind = 'demo'", Timestamp.from(Times.millis(now)));
        return ids.size();
    }

    private void confirm(long matchId, long userId, long partner, Instant now) {
        flow.apply(userId, matchId, new MatchEvent.SubmitSlots(userId, Set.of("slot_0")), now, null);
        flow.apply(partner, matchId, new MatchEvent.SubmitSlots(partner, Set.of("slot_0")), now, null);
    }

    private void openFeedback(long matchId, long userId, long partner, Instant now) {
        confirm(matchId, userId, partner, now);
        flow.reschedule(matchId, now.minus(Duration.ofHours(2)), now);
        flow.apply(userId, matchId, new MatchEvent.DateEnded(), now, null);
    }

    private long createMatch(long weekId, long userId, long partner, Instant now) {
        long low = Math.min(userId, partner);
        long high = Math.max(userId, partner);
        long id = db.newId();
        db.insertDraft(id, weekId, low, high, 80, "{}", now);
        db.insertSide(id, weekId, low);
        db.insertSide(id, weekId, high);
        Instant due = Deadlines.respondDue(now);
        List<Slot> slots = SlotRules.propose(due);
        List<java.util.Map<String, String>> json = new java.util.ArrayList<>();
        for (Slot s : slots) json.add(java.util.Map.of("id", s.id(), "startsAt", Times.iso(s.startsAt())));
        db.insertPlan(id, Json.write(json), "{}", null, null);
        copies.write(id, people.one(low), people.one(high));
        flow.apply(userId, id, new MatchEvent.Publish(due), now, null);
        db.setWantsAgain(id, true);
        return id;
    }

    private long ensureWeek(long userId, WeekState state, Instant now) {
        MatchDb.WeekRow week = db.demoWeek(userId);
        long id = week == null
                ? db.insertWeek("u" + userId, WeekCalendar.weekStartFor(now), "demo", 0, now, now)
                : week.id();
        db.setWeekState(id, state, now);
        return id;
    }

    private void clearPlayableMatches(long userId) {
        List<Long> ids = jdbc.query("SELECT match_id FROM match_side WHERE user_id = ?", (rs, n) -> rs.getLong(1), userId);
        for (long id : ids) {
            MatchDb.LoadedMatch loaded = db.lockMatch(id);
            if (loaded == null) continue;
            long other = loaded.snapshot().partnerOf(userId).userId();
            boolean real = "real".equals(people.cohortOf(other));
            if (real && !loaded.snapshot().state().isTerminal()) {
                throw new DomainException(ErrorCode.FORBIDDEN, "不能改动真实用户的匹配");
            }
            if (!real) db.deleteMatch(id);
        }
    }

    private long openMatch(long userId) {
        requirePlayable(userId);
        MatchDb.LoadedMatch loaded = db.latestFor(userId);
        if (loaded == null || loaded.snapshot().state().isTerminal()) {
            throw new DomainException(ErrorCode.MATCH_STATE, "现在没有进行中的匹配");
        }
        requireNotReal(loaded.snapshot().partnerOf(userId).userId());
        return loaded.id();
    }

    private String currentStage(long userId, Instant now) {
        MatchDb.LoadedMatch loaded = db.latestFor(userId);
        if (loaded == null || loaded.snapshot().state().isTerminal() && loaded.snapshot().reporterId() == null) {
            MatchDb.WeekRow week = db.demoWeek(userId);
            if (week == null) return "waiting";
            return week.status().state() == WeekState.PUBLISHED ? "emptyWeek" : "waiting";
        }
        // Rebuild with the wire id the panel sends back. legacy noOverlap is "closed", which is close enough to replay.
        return switch (loaded.snapshot().state()) {
            case PUBLISHED -> switch (loaded.snapshot().side(userId).response()) {
                case PENDING -> "revealed";
                case DECLINE -> "declined";
                case ACCEPT -> "awaitingPartner";
            };
            case DECLINED -> "declined";
            case CONFIRMED -> loaded.snapshot().side(userId).checkedInAt() != null ? "checkedIn" : "confirmed";
            case FEEDBACK_OPEN -> loaded.snapshot().side(userId).feedback() == null ? "feedback" : "awaitingFeedback";
            case CLOSED -> loaded.snapshot().outcome() == cn.aiditto.domain.match.Outcome.MUTUAL ? "mutual" : "closed";
            case HALTED -> "reported";
            default -> "revealed";
        };
    }

    private long partner(long userId, String handle) {
        if (handle == null || handle.isBlank()) handle = defaultHandle(userId);
        List<Long> ids = jdbc.query("SELECT id FROM \"user\" WHERE handle = ?", (rs, n) -> rs.getLong(1), handle);
        if (ids.isEmpty()) throw new DomainException(ErrorCode.NOT_FOUND, "没有这个演示对象");
        return ids.get(0);
    }

    private String defaultHandle(long userId) {
        People.Member me = people.one(userId);
        if (me != null && me.candidate() != null) {
            Set<GenderPref> wants = me.candidate().wants();
            if (wants.equals(Set.of(GenderPref.WOMEN))) return "linxia";
            if (wants.equals(Set.of(GenderPref.MEN))) return "zhouyu";
        }
        return "xumu";
    }

    private void requirePlayable(long userId) {
        String cohort = people.cohortOf(userId);
        if (cohort == null) throw new DomainException(ErrorCode.NOT_FOUND);
        if ("real".equals(cohort)) throw new DomainException(ErrorCode.FORBIDDEN, "演示操作只作用于演示账号");
    }

    private void requireNotReal(long userId) {
        if ("real".equals(people.cohortOf(userId))) throw new DomainException(ErrorCode.FORBIDDEN, "对方是真实用户");
    }

    private static String firstOverlap(MatchSnapshot snap, long userId) {
        Set<String> mine = snap.side(userId).slotIds();
        for (Slot s : snap.slots()) if (mine.contains(s.id())) return s.id();
        return null;
    }

    private static String firstMissing(MatchSnapshot snap, long userId) {
        Set<String> mine = snap.side(userId).slotIds();
        for (Slot s : snap.slots()) if (!mine.contains(s.id())) return s.id();
        return null;
    }

    private static FeedbackAnswer yes() {
        return new FeedbackAnswer(FeedbackAnswer.Met.MET, 5, FeedbackAnswer.Again.YES, true);
    }

    private static FeedbackAnswer no() {
        return new FeedbackAnswer(FeedbackAnswer.Met.MET, 5, FeedbackAnswer.Again.NO, true);
    }
}
