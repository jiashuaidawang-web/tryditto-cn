package cn.aiditto.ai;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

/** 生产日志实现：写 V6 的 llm_call_log。日志失败绝不能影响主流程。 */
public final class JdbcLlmCallLogger implements LlmCallLogger {

    private final JdbcTemplate jdbc;

    public JdbcLlmCallLogger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void log(Entry e) {
        try {
            jdbc.update("""
                    INSERT INTO llm_call_log
                      (id, week_id, call_point, model, prompt_version, input_tokens, output_tokens,
                       latency_ms, status, error_code, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, e.id(), e.weekId(), e.callPoint(), e.model(), e.promptVersion(),
                    e.inputTokens(), e.outputTokens(), e.latencyMs(), e.status(), e.errorCode(),
                    Timestamp.from(Instant.now()));
        } catch (RuntimeException ignored) {
            // observability must never break matching
        }
    }
}
