package cn.aiditto.account;

import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import cn.aiditto.infra.web.Times;
import cn.aiditto.relationship.ExtractionTaskStore;
import cn.aiditto.relationship.RelationshipProfileService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

/**
 * Runs after the account is already marked deleting. The user row lock is held by the caller.
 * PostgreSQL work commits together. Qdrant does not. A pending or failed external task means
 * erasure is not complete, and a later success does not put the profile rows back.
 */
@Service
public class AccountLifecycleCoordinator {
    public static final String QDRANT = "QDRANT";

    private final JdbcTemplate jdbc;
    private final RelationshipFeedbackStore feedback;
    private final RelationshipProfileService profiles;
    private final ExtractionTaskStore tasks;
    private final MatchingConsentService consents;
    private final Clock clock;

    public AccountLifecycleCoordinator(JdbcTemplate jdbc, RelationshipFeedbackStore feedback,
                                       RelationshipProfileService profiles, ExtractionTaskStore tasks,
                                       MatchingConsentService consents, Clock clock) {
        this.jdbc = jdbc;
        this.feedback = feedback;
        this.profiles = profiles;
        this.tasks = tasks;
        this.consents = consents;
        this.clock = clock;
    }

    @Transactional
    public void eraseIntegrated(long userId) {
        Instant now = Times.millis(clock.instant());
        feedback.eraseActor(userId, now);
        profiles.erase(userId);
        tasks.stopForDeletion(userId);
        jdbc.update("DELETE FROM profile_learning_confirmation WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile_learning_proposal WHERE user_id = ?", userId);
        jdbc.update("""
                UPDATE vector_sync
                SET status = 'cancelled', error_msg = 'account_deleting'
                WHERE user_id = ? AND status = 'pending' AND op = 'upsert'
                """, userId);
        consents.revoke(userId);
        jdbc.update("""
                INSERT INTO account_erasure_task (user_id, store_name, status, attempts, last_error, updated_at)
                VALUES (?, ?, 'PENDING', 0, NULL, ?)
                ON CONFLICT (user_id, store_name) DO NOTHING
                """, userId, QDRANT, Timestamp.from(now));
    }

    /** A finished task stays finished. Success does not restore deleted profile rows. */
    @Transactional
    public boolean recordExternal(long userId, String store, boolean succeeded, String error) {
        String status = succeeded ? "DONE" : "FAILED";
        String detail = error == null ? null : error.substring(0, Math.min(error.length(), 160));
        int updated = jdbc.update("""
                UPDATE account_erasure_task
                SET status = ?, attempts = attempts + 1, last_error = ?, updated_at = ?
                WHERE user_id = ? AND store_name = ? AND status <> 'DONE'
                """, status, detail, Timestamp.from(Times.millis(clock.instant())), userId, store);
        return updated == 1;
    }

    public boolean fullyErased(long userId) {
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM account_erasure_task
                WHERE user_id = ? AND status <> 'DONE'
                """, Integer.class, userId);
        Integer done = jdbc.queryForObject("""
                SELECT COUNT(*) FROM account_erasure_task
                WHERE user_id = ? AND status = 'DONE'
                """, Integer.class, userId);
        return open != null && open == 0 && done != null && done > 0;
    }
}
