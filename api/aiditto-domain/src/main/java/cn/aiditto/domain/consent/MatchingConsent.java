package cn.aiditto.domain.consent;

import java.time.Instant;

/**
 * One person's current matching permission. Registration, the service agreement and AI taste
 * extraction are different decisions and are not stored here.
 *
 * @param consentVersion {@code 0} when no row exists; each real change increments it
 * @param policyVersion the text the person agreed to, or null when status is {@link ConsentStatus#UNKNOWN}
 */
public record MatchingConsent(
        long userId,
        ConsentStatus consentStatus,
        int consentVersion,
        Instant grantedAt,
        Instant revokedAt,
        String policyVersion,
        Instant updatedAt) {

    public MatchingConsent {
        if (consentStatus == null) throw new IllegalArgumentException("consentStatus");
        if (consentVersion < 0) throw new IllegalArgumentException("consentVersion");
    }
}
