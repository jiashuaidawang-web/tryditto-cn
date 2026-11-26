package cn.aiditto.relationship;

import cn.aiditto.infra.web.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Deduplicates extraction attempts. Rows do not contain user text or model context. */
@Component
public class ExtractionTaskStore {
    public enum Attempt { STARTED, DONE, BUSY, EXHAUSTED }

    private final JdbcTemplate jdbc;

    public ExtractionTaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Attempt tryStart(long userId, String inputHash, String promptVersion, long generation) {
        Timestamp now = Timestamp.from(Times.millis(Instant.now()));
        int inserted = jdbc.update("""
                INSERT INTO relationship_profile_extraction
                    (user_id, input_hash, prompt_version, generation_epoch, status, attempts, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'running', 1, ?, ?)
                ON CONFLICT (user_id, input_hash, prompt_version, generation_epoch) DO NOTHING
                """, userId, inputHash, promptVersion, generation, now, now);
        if (inserted == 1) return Attempt.STARTED;
        int resumed = jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = 'running', attempts = attempts + 1, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                  AND status IN ('degraded', 'discarded') AND attempts < 3
                """, now, userId, inputHash, promptVersion, generation);
        if (resumed == 1) return Attempt.STARTED;
        String status = status(userId, inputHash, promptVersion, generation);
        if ("completed".equals(status)) return Attempt.DONE;
        if ("running".equals(status)) return Attempt.BUSY;
        return Attempt.EXHAUSTED;
    }

    @Transactional
    public void complete(long userId, String inputHash, String promptVersion, long generation, String resultHash) {
        jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = 'completed', result_hash = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ? AND status = 'running'
                """, resultHash, Timestamp.from(Times.millis(Instant.now())), userId, inputHash, promptVersion, generation);
    }

    @Transactional
    public void discard(long userId, String inputHash, String promptVersion, long generation) {
        mark(userId, inputHash, promptVersion, generation, "discarded");
    }

    @Transactional
    public void degrade(long userId, String inputHash, String promptVersion, long generation) {
        mark(userId, inputHash, promptVersion, generation, "degraded");
    }

    private void mark(long userId, String inputHash, String promptVersion, long generation, String status) {
        jdbc.update("""
                UPDATE relationship_profile_extraction
                SET status = ?, updated_at = ?
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ? AND status = 'running'
                """, status, Timestamp.from(Times.millis(Instant.now())), userId, inputHash, promptVersion, generation);
    }

    private String status(long userId, String inputHash, String promptVersion, long generation) {
        List<String> rows = jdbc.query("""
                SELECT status FROM relationship_profile_extraction
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                """, (rs, n) -> rs.getString("status"), userId, inputHash, promptVersion, generation);
        return rows.isEmpty() ? "" : rows.get(0);
    }
}
