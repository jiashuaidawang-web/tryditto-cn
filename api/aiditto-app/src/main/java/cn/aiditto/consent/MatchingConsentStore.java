package cn.aiditto.consent;

import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.infra.web.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Current matching-consent row. No row is {@link ConsentPolicy#unknown(long)}, not a grant. */
@Component
public class MatchingConsentStore {
    private final JdbcTemplate jdbc;

    public MatchingConsentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MatchingConsent get(long userId) {
        List<MatchingConsent> rows = jdbc.query("""
                SELECT user_id, consent_status, consent_version, granted_at, revoked_at, policy_version, updated_at
                FROM matching_consent WHERE user_id = ?
                """, (rs, n) -> new MatchingConsent(
                rs.getLong("user_id"),
                ConsentStatus.fromDb(rs.getString("consent_status")),
                rs.getInt("consent_version"),
                instant(rs.getTimestamp("granted_at")),
                instant(rs.getTimestamp("revoked_at")),
                rs.getString("policy_version"),
                instant(rs.getTimestamp("updated_at"))), userId);
        return rows.isEmpty() ? ConsentPolicy.unknown(userId) : rows.get(0);
    }

    public void save(MatchingConsent consent) {
        jdbc.update("""
                INSERT INTO matching_consent
                    (user_id, consent_status, consent_version, granted_at, revoked_at, policy_version, updated_at)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (user_id) DO UPDATE SET
                    consent_status = EXCLUDED.consent_status,
                    consent_version = EXCLUDED.consent_version,
                    granted_at = EXCLUDED.granted_at,
                    revoked_at = EXCLUDED.revoked_at,
                    policy_version = EXCLUDED.policy_version,
                    updated_at = EXCLUDED.updated_at
                """, consent.userId(), consent.consentStatus().dbValue(), consent.consentVersion(),
                ts(consent.grantedAt()), ts(consent.revokedAt()), consent.policyVersion(), ts(consent.updatedAt()));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(Times.millis(instant));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
