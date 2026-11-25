package cn.aiditto.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConsentPolicyTest {
    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void missingRowIsUnknownAndIsNotAnImplicitGrant() {
        MatchingConsent missing = ConsentPolicy.unknown(42);
        assertThat(missing.consentStatus()).isEqualTo(ConsentStatus.UNKNOWN);
        assertThat(missing.consentVersion()).isZero();
        assertThat(missing.grantedAt()).isNull();
        assertThat(ConsentPolicy.allows(missing)).isFalse();
        assertThat(ConsentPolicy.allows(missing, false)).isFalse();
        assertThat(ConsentPolicy.allows(missing, true)).as("legacy hatch, not V4 compliance").isTrue();
    }

    @Test
    void grantAndRevokeAreExplicitAndIdempotent() {
        MatchingConsent granted = ConsentPolicy.grant(ConsentPolicy.unknown(7), T0);
        assertThat(granted.consentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(granted.consentVersion()).isEqualTo(1);
        assertThat(granted.policyVersion()).isEqualTo(ConsentPolicy.POLICY_VERSION);
        assertThat(granted.grantedAt()).isEqualTo(T0);
        assertThat(ConsentPolicy.grant(granted, T1)).isEqualTo(granted);

        MatchingConsent revoked = ConsentPolicy.revoke(granted, T1);
        assertThat(revoked.consentStatus()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(revoked.consentVersion()).isEqualTo(2);
        assertThat(revoked.grantedAt()).isEqualTo(T0);
        assertThat(revoked.revokedAt()).isEqualTo(T1);
        assertThat(ConsentPolicy.allows(revoked, true)).isFalse();
        assertThat(ConsentPolicy.revoke(revoked, T1.plusSeconds(60))).isEqualTo(revoked);
    }

    @Test
    void revokeWithoutAPriorGrantIsStillRevoked() {
        MatchingConsent revoked = ConsentPolicy.revoke(ConsentPolicy.unknown(9), T0);
        assertThat(revoked.consentStatus()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(revoked.grantedAt()).isNull();
        assertThat(ConsentPolicy.allows(revoked, true)).isFalse();
    }

    @Test
    void grantAfterRevokeStartsANewVersion() {
        MatchingConsent again = ConsentPolicy.grant(ConsentPolicy.revoke(ConsentPolicy.unknown(3), T0), T1);
        assertThat(again.consentStatus()).isEqualTo(ConsentStatus.GRANTED);
        assertThat(again.consentVersion()).isEqualTo(2);
        assertThat(again.revokedAt()).isNull();
        assertThat(ConsentPolicy.allows(again, true)).isTrue();
    }
}
