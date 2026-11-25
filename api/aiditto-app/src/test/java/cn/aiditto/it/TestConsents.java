package cn.aiditto.it;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Explicit grant for synthetic users in an end-to-end test. Registration and the loader do not grant.
 * V4 compliance mode publishes only rows written here (or by POST /api/me/matching-consent).
 */
public final class TestConsents {
    private TestConsents() {}

    public static void grantAll(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO matching_consent
                    (user_id, consent_status, consent_version, granted_at, revoked_at, policy_version, updated_at)
                SELECT id, 'granted', 1, CURRENT_TIMESTAMP(3), NULL, 'match-consent-v1', CURRENT_TIMESTAMP(3) FROM "user"
                ON CONFLICT (user_id) DO UPDATE SET
                    consent_status = 'granted',
                    consent_version = matching_consent.consent_version + 1,
                    granted_at = CURRENT_TIMESTAMP(3),
                    revoked_at = NULL,
                    policy_version = 'match-consent-v1',
                    updated_at = CURRENT_TIMESTAMP(3)
                """);
    }
}
