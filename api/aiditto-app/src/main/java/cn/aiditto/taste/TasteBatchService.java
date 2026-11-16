package cn.aiditto.taste;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 画像补齐与可观测性：任何异步丢失、队列拒绝、历史用户、新上线数据都由这里兜住。
 * 也是 Step 4 验收（200 合成用户全量生成）与后续调度任务的入口。
 */
@Service
public class TasteBatchService {

    public record Stats(long active, long versions, long templateFallback, long pendingSync) {}

    private final JdbcTemplate jdbc;
    private final TasteExtractor extractor;

    public TasteBatchService(JdbcTemplate jdbc, TasteExtractor extractor) {
        this.jdbc = jdbc;
        this.extractor = extractor;
    }

    /** 补齐所有「有 profile 但没有 active 画像」的用户；返回本轮成功处理数。 */
    public int refreshMissing(int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        List<Long> ids = jdbc.queryForList("""
                SELECT p.user_id FROM profile p
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_taste t WHERE t.user_id = p.user_id AND t.is_active
                )
                ORDER BY p.user_id
                LIMIT ?
                """, Long.class, capped);
        int ok = 0;
        for (Long id : ids) {
            if (extractor.ensure(id, "batch").outcome() != TasteExtractor.Outcome.FAILED) ok++;
        }
        return ok;
    }

    public Stats stats() {
        return new Stats(
                count("SELECT COUNT(*) FROM user_taste WHERE is_active"),
                count("SELECT COUNT(*) FROM user_taste"),
                count("SELECT COUNT(*) FROM user_taste WHERE is_active AND updated_reason LIKE '%template%'"),
                count("SELECT COUNT(*) FROM user_taste WHERE is_active AND vector_sync_status = 'pending'"));
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }
}
