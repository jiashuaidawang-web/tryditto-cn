package cn.aiditto.match;

import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

/**
 * 配对永久记忆（v3 §4 / A04）：拒绝、举报、爽约等负向结果沉淀成双向 forbidden，
 * 硬过滤第一道就查它，永不复推同一对。pair_hash 无序唯一，score_history 只追加。
 */
@Service
public class PairMemoryService {

    private final JdbcTemplate jdbc;
    private final IdentifierGenerator ids;
    private final Clock clock;

    public PairMemoryService(JdbcTemplate jdbc, IdentifierGenerator ids, Clock clock) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.clock = clock;
    }

    /** result 必须是 V6 CHECK 里的枚举值；forbidden 一旦为 true 永不回退。 */
    public void record(long a, long b, Long weekId, String result, String reason,
                       Double score, boolean forbidden) {
        if (a == b) return;
        long lo = Math.min(a, b), hi = Math.max(a, b);
        String history = Json.write(score == null ? List.of() : List.of(score));
        jdbc.update("""
                INSERT INTO ai_pair_memory
                  (pair_hash, user_lo, user_hi, last_week_id, result, reject_reason,
                   score_history, forbidden, updated_at)
                VALUES (?,?,?,?,?,?,CAST(? AS JSONB),?,?)
                ON CONFLICT (pair_hash) DO UPDATE SET
                  last_week_id = EXCLUDED.last_week_id,
                  result = EXCLUDED.result,
                  reject_reason = COALESCE(EXCLUDED.reject_reason, ai_pair_memory.reject_reason),
                  score_history = ai_pair_memory.score_history || EXCLUDED.score_history,
                  forbidden = ai_pair_memory.forbidden OR EXCLUDED.forbidden,
                  updated_at = EXCLUDED.updated_at
                """, pairHash(lo, hi), lo, hi, weekId, result, reason, history, forbidden,
                Timestamp.from(Times.millis(clock.instant())));
    }

    /** 供 HardFilter 合并到 reportedPairs 之前，杜绝被双向拒绝的对再次召回。 */
    public List<long[]> forbiddenPairs() {
        return jdbc.query("SELECT user_lo, user_hi FROM ai_pair_memory WHERE forbidden",
                (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)});
    }

    public long countForbidden() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ai_pair_memory WHERE forbidden", Long.class);
        return n == null ? 0 : n;
    }

    public static String pairHash(long lo, long hi) {
        try {
            byte[] d = MessageDigest.getInstance("MD5")
                    .digest((lo + ":" + hi).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
