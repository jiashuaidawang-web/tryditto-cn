package cn.aiditto.match;

import cn.aiditto.account.UserLock;
import cn.aiditto.consent.MatchConsentProperties;
import cn.aiditto.consent.MatchingConsentStore;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.match.MatchEvent;
import cn.aiditto.domain.match.MatchMachine;
import cn.aiditto.domain.match.MatchSnapshot;
import cn.aiditto.domain.match.MatchState;
import cn.aiditto.domain.match.Transition;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.EligibilityGate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.Weights;
import cn.aiditto.domain.slot.Deadlines;
import cn.aiditto.infra.web.Times;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Publish and post-publish safety. The check runs after the user rows and the match row are locked,
 * in that order, inside the caller's transaction. A query outside that transaction is not this check.
 *
 * <p>Lock order everywhere this class takes locks: lower user id, higher user id, then the match.
 * {@link #releaseUser} is called by someone who already holds that one user lock; it then locks that
 * person's open matches by id and does not lock the other person (so it cannot invert the user order).
 */
@Service
public class PublishGuard {
    private static final Logger log = LoggerFactory.getLogger(PublishGuard.class);

    public record Outcome(boolean published, List<String> reasonCodes) {}

    private final MatchDb db;
    private final People people;
    private final MatchingConsentStore consents;
    private final UserLock userLock;
    private final MatchConsentProperties props;

    public PublishGuard(MatchDb db, People people, MatchingConsentStore consents, UserLock userLock,
                        MatchConsentProperties props) {
        this.db = db;
        this.people = people;
        this.consents = consents;
        this.userLock = userLock;
        this.props = props;
    }

    /** The only user-facing failure. It names no person and no private reason. */
    public static EligibilityWithheld withheld() {
        return new EligibilityWithheld();
    }

    /**
     * Publish one draft, or cancel it when it no longer qualifies. Already published is a no-op success.
     * Already terminal for any other reason is a no-op failure. Caller must be inside a transaction.
     */
    @Transactional
    public Outcome publishDraft(long matchId, Instant now) {
        long[] pair = db.pairUsers(matchId);
        if (pair == null) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        userLock.lock(Math.min(pair[0], pair[1]));
        userLock.lock(Math.max(pair[0], pair[1]));
        MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
        if (loaded == null) throw new DomainException(ErrorCode.MATCH_NOT_FOUND);
        return finish(loaded, now);
    }

    /**
     * After a consent revoke, block, deactivation, deletion, or hard-constraint edit: cancel this
     * person's open matches that no longer qualify. The caller already holds {@code userId}'s row lock.
     */
    public void releaseUser(long userId, Instant now) {
        for (long matchId : db.openMatchIds(userId)) {
            MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
            if (loaded == null || loaded.snapshot().state().isTerminal()) continue;
            EligibilityGate.Result result = evaluate(loaded, now);
            if (!result.eligible()) hold(loaded, result, now);
        }
    }

    /**
     * WeChat is returned only when the pair still qualifies. A failure hides the contact and does not
     * say why. Closed matches are not moved; the read itself is refused.
     */
    @Transactional
    public boolean contactAllowed(long matchId, Instant now) {
        long[] pair = db.pairUsers(matchId);
        if (pair == null) return false;
        userLock.lock(Math.min(pair[0], pair[1]));
        userLock.lock(Math.max(pair[0], pair[1]));
        MatchDb.LoadedMatch loaded = db.lockMatch(matchId);
        if (loaded == null) return false;
        EligibilityGate.Result result = evaluate(loaded, now);
        if (result.eligible()) return true;
        if (!loaded.snapshot().state().isTerminal()) hold(loaded, result, now);
        else log.info("match contact withheld matchId={}", matchId);
        return false;
    }

    /** Users and the match are already locked. Used by {@link MatchFlow} for later transitions. */
    public EligibilityGate.Result evaluateLocked(MatchDb.LoadedMatch loaded, Instant now) {
        return evaluate(loaded, now);
    }

    public void holdLocked(MatchDb.LoadedMatch loaded, EligibilityGate.Result result, Instant now) {
        hold(loaded, result, now);
    }

    private Outcome finish(MatchDb.LoadedMatch loaded, Instant now) {
        MatchSnapshot snap = loaded.snapshot();
        if (snap.state() == MatchState.PUBLISHED) return new Outcome(true, List.of());
        if (snap.state() != MatchState.DRAFT) return new Outcome(false, List.of());
        EligibilityGate.Result result = evaluate(loaded, now);
        if (!result.eligible()) {
            hold(loaded, result, now);
            return new Outcome(false, result.reasonCodes());
        }
        Instant due = Deadlines.respondDue(Times.millis(now));
        Transition transition = MatchMachine.apply(snap, new MatchEvent.Publish(due), Times.millis(now));
        if (transition.changed()) db.saveSnapshot(loaded.id(), transition.after(), now);
        return new Outcome(true, List.of());
    }

    private void hold(MatchDb.LoadedMatch loaded, EligibilityGate.Result result, Instant now) {
        Transition transition = MatchMachine.apply(loaded.snapshot(), new MatchEvent.Hold(), Times.millis(now));
        if (transition.changed()) db.saveSnapshot(loaded.id(), transition.after(), now);
        String reason = String.join(",", result.reasonCodes());
        if (reason.length() > 200) reason = reason.substring(0, 200);
        if (reason.isEmpty()) reason = EligibilityGate.PUBLIC_CODE;
        db.setHoldReason(loaded.id(), reason);
        log.info("match held matchId={}", loaded.id());
    }

    private EligibilityGate.Result evaluate(MatchDb.LoadedMatch loaded, Instant now) {
        MatchSnapshot snap = loaded.snapshot();
        long low = snap.low().userId();
        long high = snap.high().userId();
        MatchDb.WeekRow week = db.weekOf(loaded.weekId());
        LocalDate weekStart = week == null ? LocalDate.now() : week.weekStart();
        boolean manual = week != null && "manual".equals(week.trigger());
        Instant checkedAt = Times.millis(now);
        People.Member lowMember = people.snapshot(low, weekStart, checkedAt, loaded.id());
        People.Member highMember = people.snapshot(high, weekStart, checkedAt, loaded.id());
        Candidate lowCandidate = lowMember == null ? null : lowMember.candidate();
        Candidate highCandidate = highMember == null ? null : highMember.candidate();
        MatchingInput input = null;
        if (lowCandidate != null && highCandidate != null) {
            input = new MatchingInput(weekStart, List.of(lowCandidate, highCandidate),
                    pairs(db.pastPairsExcept(loaded.id())),
                    db.reportedEitherWay(low, high) ? Set.of(PairKey.of(low, high)) : Set.of(),
                    Weights.W1, weekStart.toEpochDay(), manual);
        }
        MatchingConsent lowConsent = consents.get(low);
        MatchingConsent highConsent = consents.get(high);
        return EligibilityGate.evaluate(new EligibilityGate.Facts(
                weekStart, lowCandidate, highCandidate, input, db.forbidden(low, high),
                lowMember == null ? null : lowMember.applicant(),
                highMember == null ? null : highMember.applicant(),
                lowConsent, highConsent, props.legacyAllowUnknown(), checkedAt));
    }

    private static Set<PairKey> pairs(List<long[]> rows) {
        Set<PairKey> out = new LinkedHashSet<>();
        for (long[] row : rows) out.add(PairKey.of(row[0], row[1]));
        return out;
    }
}
