package cn.aiditto.match;

import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.crypto.Pii;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Synthetic users are fictional. Nothing — SMS, email, or a future provider — may be delivered to them.
 * Demo mode already has no provider; this is the hard stop that stays when one is added.
 */
@Component
public class OutboundGuard {
    private final JdbcTemplate jdbc;
    private final Crypto crypto;

    public OutboundGuard(JdbcTemplate jdbc, Crypto crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    /** {@code false} only when the phone belongs to a synthetic account. Unknown phones may be new signups. */
    public boolean mayText(String phone) {
        return !isSynthetic("SELECT cohort FROM `user` WHERE phone_hash = ?", crypto.hash(Pii.normalizePhone(phone)));
    }

    /** {@code false} when any verified or stored email row for this address belongs to a synthetic account. */
    public boolean mayEmail(String email) {
        return !isSynthetic(
                "SELECT u.cohort FROM email_verification v JOIN `user` u ON u.id = v.user_id WHERE v.email_hash = ?",
                crypto.hash(Pii.normalizeEmail(email)));
    }

    private boolean isSynthetic(String sql, byte[] hash) {
        return Boolean.TRUE.equals(jdbc.query(sql, rs -> rs.next() && "synthetic".equals(rs.getString(1)), hash));
    }
}
