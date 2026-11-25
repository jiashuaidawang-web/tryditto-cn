package cn.aiditto.match;

import cn.aiditto.account.AccountService;
import cn.aiditto.account.User;
import cn.aiditto.account.UserLock;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.match.Effect;
import cn.aiditto.domain.match.FeedbackAnswer;
import cn.aiditto.domain.match.MatchEvent;
import cn.aiditto.domain.match.MatchMachine;
import cn.aiditto.domain.match.MatchRuleException;
import cn.aiditto.domain.match.MatchSnapshot;
import cn.aiditto.domain.match.Penalties;
import cn.aiditto.domain.match.ReportCategory;
import cn.aiditto.domain.match.Transition;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** User actions and the deadline ticker. Every event goes through {@link MatchMachine}. */
@Service
public class MatchFlow {
    static final Set<String> DECLINE_REASONS = Set.of("notMyType", "reasonsOff", "tooFar", "noTime", "knowThem", "other");
    private static final Instant REVIEW_UNTIL = Instant.parse("2099-01-01T00:00:00Z");

    private final MatchDb db;
    private final AccountService accounts;
    private final Crypto crypto;
    private final TransactionTemplate tx;
    private final UserLock userLock;
    private final PublishGuard guard;

    public MatchFlow(MatchDb db, AccountService accounts, Crypto crypto, UserLock userLock, PublishGuard guard,
                     PlatformTransactionManager txm) {
        this.db = db;
        this.accounts = accounts;
        this.crypto = crypto;
        this.userLock = userLock;
        this.guard = guard;
        this.tx = new TransactionTemplate(txm);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void accept(long userId, long matchId, Instant now) {
        requireOnboarded(userId);
        requireMine(userId, matchId);
        db.markAccepted(matchId, userId, now);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void confirmSlots(long userId, long matchId, List<String> slotIds, Instant now) {
        requireOnboarded(userId);
        apply(userId, matchId, new MatchEvent.SubmitSlots(userId, new LinkedHashSet<>(slotIds == null ? List.of() : slotIds)), now, null);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void decline(long userId, long matchId, List<String> reasons, String note, Instant now) {
        requireOnboarded(userId);
        if (reasons == null || reasons.isEmpty()) throw new DomainException(ErrorCode.REASON_UNKNOWN, "至少选一个理由");
        for (String r : reasons) if (!DECLINE_REASONS.contains(r)) throw new DomainException(ErrorCode.REASON_UNKNOWN, r);
        requireMine(userId, matchId);
        String trimmed = note == null ? null : note.strip();
        if (trimmed != null && trimmed.length() > 500) trimmed = trimmed.substring(0, 500);
        db.saveDecline(matchId, userId, Json.write(reasons), trimmed);
        apply(userId, matchId, new MatchEvent.Decline(userId), now, null);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void flag(long userId, long matchId, int index) {
        requireOnboarded(userId);
        requireMine(userId, matchId);
        String json = db.jdbc().queryForObject(
                "SELECT reasons FROM match_copy WHERE match_id = ? AND viewer_id = ?", String.class, matchId, userId);
        List<?> reasons = json == null ? List.of() : Json.read(json, List.class);
        if (index < 0 || index >= reasons.size()) throw new DomainException(ErrorCode.REASON_INDEX);
        List<Integer> flags = new ArrayList<>(db.lockMatch(matchId).flagged().getOrDefault(userId, List.of()));
        if (!flags.remove(Integer.valueOf(index))) flags.add(index);
        db.saveFlags(matchId, userId, Json.write(flags));
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void checkIn(long userId, long matchId, Instant now) {
        requireOnboarded(userId);
        apply(userId, matchId, new MatchEvent.CheckIn(userId), now, null);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void cancel(long userId, long matchId, Instant now) {
        requireOnboarded(userId);
        apply(userId, matchId, new MatchEvent.Cancel(userId), now, null);
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void feedback(long userId, long matchId, FeedbackAnswer answer, String comment, Instant now) {
        requireOnboarded(userId);
        apply(userId, matchId, new MatchEvent.SubmitFeedback(userId, answer), now, null);
        if (comment != null && !comment.isBlank()) {
            String text = comment.strip();
            if (text.length() > 500) text = text.substring(0, 500);
            db.jdbc().update("UPDATE feedback SET comment_enc = ? WHERE match_id = ? AND user_id = ?",
                    crypto.encrypt(text), matchId, userId);
        }
    }

    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void report(long userId, long matchId, String category, String note, Instant now) {
        requireOnboarded(userId);
        ReportCategory cat;
        try {
            cat = ReportCategory.fromId(category);
        } catch (MatchRuleException e) {
            throw MatchErrors.translate(e);
        }
        apply(userId, matchId, new MatchEvent.Report(userId, cat), now, note);
    }

    /** Demo and the deadline ticker call this with an event the viewer did not type. */
    @Transactional(noRollbackFor = EligibilityWithheld.class)
    public void apply(long actorId, long matchId, MatchEvent event, Instant now, String note) {
        if (event instanceof MatchEvent.Publish) {
            PublishGuard.Outcome outcome = guard.publishDraft(matchId, now);
            if (!outcome.published()) throw PublishGuard.withheld();
            return;
        }
        long[] pair = db.pairUsers(matchId);
        if (pair == null) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        userLock.lock(Math.min(pair[0], pair[1]));
        userLock.lock(Math.max(pair[0], pair[1]));
        MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
        if (loaded == null) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        if (!loaded.snapshot().state().isTerminal()) {
            var result = guard.evaluateLocked(loaded, now);
            if (!result.eligible()) {
                guard.holdLocked(loaded, result, now);
                if (event instanceof MatchEvent.ByUser) throw PublishGuard.withheld();
                return;
            }
        }
        Transition t;
        try {
            t = MatchMachine.apply(loaded.snapshot(), event, Times.millis(now));
        } catch (MatchRuleException e) {
            throw MatchErrors.translate(e);
        }
        if (!t.changed()) return;
        db.saveSnapshot(matchId, t.after(), now);
        for (Effect effect : t.effects()) applyEffect(effect, matchId, note, now);
    }

    public int tickDeadlines(Instant now) {
        int n = 0;
        n += due("respond_due", "published", new MatchEvent.RespondDeadline(), now);
        n += due("reselect_due", "no_overlap", new MatchEvent.ReselectDeadline(), now);
        n += due("slot_end", "confirmed", new MatchEvent.DateEnded(), now);
        n += due("feedback_due", "feedback_open", new MatchEvent.FeedbackDeadline(), now);
        return n;
    }

    public void reschedule(long matchId, Instant start, Instant now) {
        tx.executeWithoutResult(s -> {
            MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
            if (loaded == null) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
            MatchSnapshot shifted = loaded.snapshot().rescheduledTo(Times.millis(start));
            db.saveSnapshot(matchId, shifted, now);
        });
    }

    private int due(String column, String state, MatchEvent event, Instant now) {
        int n = 0;
        for (long id : db.due(column, state, now)) {
            try {
                tx.executeWithoutResult(s -> {
                    try {
                        apply(0, id, event, now, null);
                    } catch (EligibilityWithheld ignored) {
                        // the hold is the result; committing it must not roll back with the signal
                    }
                });
                n++;
            } catch (RuntimeException ignored) {
                // one stuck match must not stop the others; the next tick retries it
            }
        }
        return n;
    }

    private void applyEffect(Effect effect, long matchId, String note, Instant now) {
        if (effect instanceof Effect.RecordNoResponse r) {
            db.addNoResponse(r.userId());
            if (Penalties.shouldAutoPause(db.penaltyCounts(r.userId())[2])) db.pause(r.userId(), now);
        } else if (effect instanceof Effect.RecordLateCancel r) {
            db.addLateCancel(r.userId());
            penalise(r.userId(), now);
        } else if (effect instanceof Effect.RecordNoShow r) {
            db.addNoShow(r.userId());
            penalise(r.userId(), now);
        } else if (effect instanceof Effect.RecordDispute r) {
            db.addDispute(r.userA());
            db.addDispute(r.userB());
            if (db.penaltyCounts(r.userA())[3] >= 3) db.suspendUser(r.userA(), REVIEW_UNTIL, now);
            if (db.penaltyCounts(r.userB())[3] >= 3) db.suspendUser(r.userB(), REVIEW_UNTIL, now);
        } else if (effect instanceof Effect.CreateReport r) {
            byte[] enc = note == null || note.isBlank() ? null : crypto.encrypt(note.strip());
            Instant sla = now.plus(r.urgent() ? Duration.ofHours(24) : Duration.ofHours(72));
            db.insertReport(db.newId(), r.reporterId(), r.reportedId(), matchId, r.category().id(), enc, r.urgent(), sla, now);
        } else if (effect instanceof Effect.SuspendPendingReview r) {
            db.suspendUser(r.userId(), REVIEW_UNTIL, now);
        }
    }

    private void penalise(long userId, Instant now) {
        int[] c = db.penaltyCounts(userId);
        Penalties.Penalty p = Penalties.forNoShows(c[0], c[1], now);
        if (p.stopped()) db.suspendUser(userId, REVIEW_UNTIL, now);
        else if (p.suspendedUntil() != null) db.suspendUser(userId, p.suspendedUntil(), now);
    }

    private void requireOnboarded(long userId) {
        User u = accounts.require(userId);
        if (!u.onboarded()) throw new DomainException(ErrorCode.ONBOARDING_REQUIRED);
    }

    private void requireMine(long userId, long matchId) {
        long[] pair = db.pairUsers(matchId);
        if (pair == null || (pair[0] != userId && pair[1] != userId)) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        userLock.lock(Math.min(pair[0], pair[1]));
        userLock.lock(Math.max(pair[0], pair[1]));
        MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
        if (loaded == null || !loaded.snapshot().involves(userId)) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        if (!loaded.snapshot().state().isTerminal()) {
            var result = guard.evaluateLocked(loaded, java.time.Instant.now());
            if (!result.eligible()) {
                guard.holdLocked(loaded, result, java.time.Instant.now());
                throw PublishGuard.withheld();
            }
        }
    }
}
