package cn.aiditto.consent;

import cn.aiditto.account.UserLock;
import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.infra.web.Times;
import cn.aiditto.match.PublishGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Grant and revoke matching permission. This is not account signup, the service terms, or AI processing. */
@Service
public class MatchingConsentService {
    private final MatchingConsentStore store;
    private final UserLock userLock;
    private final PublishGuard guard;
    private final MatchConsentProperties props;
    private final Clock clock;

    public MatchingConsentService(MatchingConsentStore store, UserLock userLock, PublishGuard guard,
                                  MatchConsentProperties props, Clock clock) {
        this.store = store;
        this.userLock = userLock;
        this.guard = guard;
        this.props = props;
        this.clock = clock;
    }

    public MatchingConsent current(long userId) {
        return store.get(userId);
    }

    public boolean allowsPool(long userId) {
        return ConsentPolicy.allows(store.get(userId), props.legacyAllowUnknown());
    }

    /** True only for the migration escape hatch. A true value is not V4 compliance mode. */
    public boolean legacyAllowUnknown() {
        return props.legacyAllowUnknown();
    }

    @Transactional
    public MatchingConsent grant(long userId) {
        userLock.lock(userId);
        MatchingConsent current = store.get(userId);
        MatchingConsent next = ConsentPolicy.grant(current, now());
        if (!current.equals(next)) store.save(next);
        return next;
    }

    @Transactional
    public MatchingConsent revoke(long userId) {
        userLock.lock(userId);
        Instant now = now();
        MatchingConsent current = store.get(userId);
        MatchingConsent next = ConsentPolicy.revoke(current, now);
        if (!current.equals(next)) store.save(next);
        guard.releaseUser(userId, now);
        return next;
    }

    private Instant now() {
        return Times.millis(clock.instant());
    }
}
