package cn.aiditto.domain.consent;

import java.time.Instant;
import java.util.Objects;

/**
 * Grant and revoke. A missing row stays {@link ConsentStatus#UNKNOWN}; nothing in account creation
 * or onboarding promotes it to {@link ConsentStatus#GRANTED}.
 */
public final class ConsentPolicy {
    /** Text version shown when someone agrees to be matched. */
    public static final String POLICY_VERSION = "match-consent-v1";

    private ConsentPolicy() {}

    public static MatchingConsent unknown(long userId) {
        return new MatchingConsent(userId, ConsentStatus.UNKNOWN, 0, null, null, null, null);
    }

    /**
     * V4 compliance mode. Only an explicit grant passes. A missing row and a revoke both fail.
     */
    public static boolean allows(MatchingConsent consent) {
        return allows(consent, false);
    }

    /**
     * @param legacyAllowUnknown migration escape hatch, not V4 compliance mode. When true, a
     *                            historical user with no row may enter. {@link ConsentStatus#REVOKED}
     *                            never passes, whatever this flag is.
     */
    public static boolean allows(MatchingConsent consent, boolean legacyAllowUnknown) {
        Objects.requireNonNull(consent, "consent");
        return switch (consent.consentStatus()) {
            case GRANTED -> true;
            case REVOKED -> false;
            case UNKNOWN -> legacyAllowUnknown;
        };
    }

    /** Idempotent when the same policy is already granted. */
    public static MatchingConsent grant(MatchingConsent current, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        if (current.consentStatus() == ConsentStatus.GRANTED && POLICY_VERSION.equals(current.policyVersion())) {
            return current;
        }
        return new MatchingConsent(current.userId(), ConsentStatus.GRANTED, current.consentVersion() + 1,
                now, null, POLICY_VERSION, now);
    }

    /** Idempotent when already revoked. Withdrawing without a prior grant is still an explicit revoke. */
    public static MatchingConsent revoke(MatchingConsent current, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        if (current.consentStatus() == ConsentStatus.REVOKED && POLICY_VERSION.equals(current.policyVersion())) {
            return current;
        }
        return new MatchingConsent(current.userId(), ConsentStatus.REVOKED, current.consentVersion() + 1,
                current.grantedAt(), now, POLICY_VERSION, now);
    }
}
