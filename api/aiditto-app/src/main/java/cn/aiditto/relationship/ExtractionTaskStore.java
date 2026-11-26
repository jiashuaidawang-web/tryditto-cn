package cn.aiditto.relationship;

import cn.aiditto.infra.web.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row per input fingerprint and processing generation.
 * The lease lets a crashed runner be replaced. The fence stops the replaced runner from writing.
 * This transaction ends before any model call.
 */
@Component
public class ExtractionTaskStore {
    public enum Attempt { STARTED, DONE, BUSY, EXHAUSTED }

    public record Claim(Attempt attempt, String attemptId, long fence) {
        static Claim idle(Attempt attempt) {
            return new Claim(attempt, null, 0);
        }
    }

    static final int MAX_ATTEMPTS = 3;
    private static final long LEASE_SECONDS = 30;

    private final JdbcTemplate jdbc;

    public ExtractionTaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Claim tryStart(long userId, String inputHash, String promptVersion, long generation, long exportEpoch) {
        Timestamp now = Timestamp.from(Times.millis(Instant.now()));
        Timestamp lease = Timestamp.from(Times.millis(Instant.now().plusSeconds(LEASE_SECONDS)));
        String attemptId = UUID.randomUUID().toString();
        int inserted = jdbc.update("""
                INSERT INTO relationship_profile_extraction
                    (user_id, input_hash, prompt_version, generation_epoch, status, attempts,
                     attempt_id, fence, lease_until, model_export_epoch, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'running', 1, ?, 1, ?, ?, ?, ?)
                ON CONFLICT (user_id, input_hash, prompt_version, generation_epoch) DO NOTHING
                """, userId, inputHash, promptVersion, generation, attemptId, lease, exportEpoch, now, now);
        if (inserted == 1) return new Claim(Attempt.STARTED, attemptId, 1);
        Claim reclaimed = reclaimExpired(userId, inputHash, promptVersion, generation, now);
        if (reclaimed != null) return reclaimed;
        int exhausted = jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = 'discarded', updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status = 'running' AND lease_until < ? AND attempts >= ?
                """, now, userId, inputHash, promptVersion, generation, now, MAX_ATTEMPTS);
        if (exhausted == 1) return Claim.idle(Attempt.EXHAUSTED);
        Claim resumed = resumeFailed(userId, inputHash, promptVersion, generation, now);
        if (resumed != null) return resumed;
        String status = status(userId, inputHash, promptVersion, generation);
        if ("completed".equals(status)) return Claim.idle(Attempt.DONE);
        if ("running".equals(status)) return Claim.idle(Attempt.BUSY);
        return Claim.idle(Attempt.EXHAUSTED);
    }

    @Transactional
    public int complete(long userId, String inputHash, String promptVersion, long generation,
                        String resultHash, long fence, long exportEpoch) {
        return jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = 'completed', result_hash = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status = 'running' AND fence = ? AND model_export_epoch = ?
                """, resultHash, Timestamp.from(Times.millis(Instant.now())),
                userId, inputHash, promptVersion, generation, fence, exportEpoch);
    }

    @Transactional
    public int discard(long userId, String inputHash, String promptVersion, long generation, long fence) {
        return mark(userId, inputHash, promptVersion, generation, fence, "discarded");
    }

    @Transactional
    public int degrade(long userId, String inputHash, String promptVersion, long generation, long fence) {
        return mark(userId, inputHash, promptVersion, generation, fence, "degraded");
    }

    private Claim reclaimExpired(long userId, String inputHash, String promptVersion, long generation, Timestamp now) {
        return claim("""
                UPDATE relationship_profile_extraction
                SET status = 'running', attempts = attempts + 1, attempt_id = ?, fence = fence + 1,
                    lease_until = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status = 'running' AND lease_until < ? AND attempts < ?
                RETURNING attempt_id, fence
                """, userId, inputHash, promptVersion, generation, now, true);
    }

    private Claim resumeFailed(long userId, String inputHash, String promptVersion, long generation, Timestamp now) {
        return claim("""
                UPDATE relationship_profile_extraction
                SET status = 'running', attempts = attempts + 1, attempt_id = ?, fence = fence + 1,
                    lease_until = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status IN ('degraded', 'discarded') AND attempts < ?
                RETURNING attempt_id, fence
                """, userId, inputHash, promptVersion, generation, now, false);
    }

    private Claim claim(String sql, long userId, String inputHash, String promptVersion, long generation,
                        Timestamp now, boolean expiredOnly) {
        Timestamp lease = Timestamp.from(Times.millis(Instant.now().plusSeconds(LEASE_SECONDS)));
        String attemptId = UUID.randomUUID().toString();
        Object[] args = expiredOnly
                ? new Object[] {attemptId, lease, now, userId, inputHash, promptVersion, generation, now, MAX_ATTEMPTS}
                : new Object[] {attemptId, lease, now, userId, inputHash, promptVersion, generation, MAX_ATTEMPTS};
        List<Claim> rows = jdbc.query(sql,
                (rs, n) -> new Claim(Attempt.STARTED, rs.getString("attempt_id"), rs.getLong("fence")), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int mark(long userId, String inputHash, String promptVersion, long generation, long fence, String status) {
        return jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status = 'running' AND fence = ?
                """, status, Timestamp.from(Times.millis(Instant.now())),
                userId, inputHash, promptVersion, generation, fence);
    }

    private String status(long userId, String inputHash, String promptVersion, long generation) {
        List<String> rows = jdbc.query("""
                SELECT status FROM relationship_profile_extraction
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                """, (rs, n) -> rs.getString("status"), userId, inputHash, promptVersion, generation);
        return rows.isEmpty() ? "" : rows.get(0);
    }
}
