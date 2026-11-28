package cn.aiditto.recall;

import cn.aiditto.domain.geo.IsoCellGrid;
import cn.aiditto.infra.clock.ClockConfig;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PG → Qdrant 单向同步（Step 5.2）。
 *
 * 消费 Step 4 产出的 user_taste(vector_sync_status='pending')，生成 taste/persona 双 named vector，
 * 连同可索引 payload 写入 Qdrant；成功后回填 embedding_id / embedding_version / synced，
 * 并写 vector_sync 审计账。注销/封禁用户必须真删向量（数据最小化），不是只靠 filter。
 *
 * 单个用户失败不中断整批，标记 failed 留待补偿；整批可重入（Qdrant point id = user_id，覆盖写）。
 */
@Service
public class VectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncService.class);

    public record Stats(int scanned, int synced, int failed, int deleted) {}

    private static final String PENDING_SQL = """
            SELECT t.user_id, t.version, t.persona_text, t.taste_text,
                   p.school_domain, p.gender, p.expected_genders_enc, p.birthday,
                   p.height_cm, p.place_id, p.travel_mode, p.travel_minutes,
                   pl.lat AS place_lat, pl.lng AS place_lng,
                   p.matching_style, p.education_importance,
                   p.budget, p.photo_count, p.hobbies, p.relationship_looking, p.hobby_details,
                   u.status, u.is_synthetic,
                   EXISTS (SELECT 1 FROM email_verification e
                           WHERE e.user_id = t.user_id AND e.verified_at IS NOT NULL) AS verified,
                   -- 真实饥饿周数：最近一次成约之后又空过了多少个已关闭周期（Step 5 偏离补全）
                   (SELECT COUNT(*) FROM week w
                    WHERE w.state = 'closed'
                      AND w.id > COALESCE((SELECT MAX(m.week_id) FROM "match" m
                                           WHERE (m.user_low = t.user_id OR m.user_high = t.user_id)
                                             AND m.state IN ('confirmed','feedback_open','closed')), 0)
                   ) AS starve_weeks
            FROM user_taste t
            JOIN profile p ON p.user_id = t.user_id
            JOIN "user" u ON u.id = t.user_id
            LEFT JOIN place pl ON pl.id = p.place_id
            WHERE t.is_active AND t.vector_sync_status = 'pending' AND u.status = 'active'
            ORDER BY t.user_id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final RecallService recall;
    private final EmbeddingService embedding;
    private final RecallProperties props;
    private final Crypto crypto;
    private final IdentifierGenerator ids;
    private final Clock clock;

    public VectorSyncService(JdbcTemplate jdbc, RecallService recall, EmbeddingService embedding,
                             RecallProperties props, Crypto crypto,
                             IdentifierGenerator ids, Clock clock) {
        this.jdbc = jdbc;
        this.recall = recall;
        this.embedding = embedding;
        this.props = props;
        this.crypto = crypto;
        this.ids = ids;
        this.clock = clock;
    }

    /** 同步 pending 画像 + 清理已注销用户。 */
    public Stats syncAll(int limit) {
        int capped = Math.max(1, Math.min(limit, 1000));
        List<Map<String, Object>> rows = jdbc.queryForList(PENDING_SQL, capped);
        recall.ensureCollection();
        int synced = 0, failed = 0;
        // 批量 embed：每个用户需要 taste + persona 两条文本。
        // 网关单请求文本条数有上限（text-embedding-v3 实测 10），必须按**文本数**切分，
        // 按用户数切会超限时 400 → 静默回退逐条 → 200 用户同步从 ~1 分钟劣化到 ~8 分钟。
        List<RecallModel.Point> batch = new ArrayList<>();
        List<Map<String, Object>> batchRows = new ArrayList<>();
        int maxItems = Math.max(2, props.embeddingMaxBatchItems());
        int usersPerGroup = props.usersPerEmbeddingBatch();
        long syncStart = System.currentTimeMillis();
        int batchCalls = 0, fallbackTexts = 0;
        for (int from = 0; from < rows.size(); from += usersPerGroup) {
            List<Map<String, Object>> group = new ArrayList<>();
            for (Map<String, Object> row : rows.subList(from, Math.min(from + usersPerGroup, rows.size()))) {
                if (openForVector(row)) group.add(row);
                else quarantine(row);
            }
            if (group.isEmpty()) continue;
            List<String> texts = new ArrayList<>(group.size() * 2);
            for (Map<String, Object> row : group) {
                texts.add(str(row.get("taste_text")));
                texts.add(str(row.get("persona_text")));
            }
            List<float[]> vectors;
            try {
                vectors = embedding.embedBatch(texts);
                if (vectors.size() != texts.size()) throw new IllegalStateException("batch size mismatch");
                batchCalls++;
            } catch (RuntimeException batchErr) {
                // 显式告警：批量降级会显著拉长同步时间，必须可观测，不能静默吞掉
                fallbackTexts += texts.size();
                log.warn("embedding batch failed (users={} texts={} maxItems={}) -> serial fallback: {}",
                        group.size(), texts.size(), maxItems, batchErr.getMessage());
                vectors = new ArrayList<>(texts.size());
                for (String t : texts) {
                    try {
                        vectors.add(embedding.embed(t));
                    } catch (RuntimeException e) {
                        vectors.add(null);
                    }
                }
            }
            for (int i = 0; i < group.size(); i++) {
                Map<String, Object> row = group.get(i);
                float[] taste = vectors.get(i * 2), persona = vectors.get(i * 2 + 1);
                if (taste == null || persona == null) {
                    markFailed(row, new IllegalStateException("embedding failed"));
                    failed++;
                    continue;
                }
                try {
                    batch.add(point(row, taste, persona));
                    batchRows.add(row);
                } catch (RuntimeException e) {
                    markFailed(row, e);
                    failed++;
                }
            }
        }
        log.info("embedding sync done: users={} batchCalls={} usersPerGroup={} maxItems={} "
                        + "fallbackTexts={} costMs={}",
                rows.size(), batchCalls, usersPerGroup, maxItems, fallbackTexts,
                System.currentTimeMillis() - syncStart);
        if (!batch.isEmpty()) {
            List<RecallModel.Point> live = new ArrayList<>();
            List<Map<String, Object>> liveRows = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                if (openForVector(batchRows.get(i))) {
                    live.add(batch.get(i));
                    liveRows.add(batchRows.get(i));
                } else {
                    quarantine(batchRows.get(i));
                }
            }
            batch = live;
            batchRows = liveRows;
        }
        if (!batch.isEmpty()) {
            try {
                recall.upsert(batch);
                for (Map<String, Object> row : batchRows) markSynced(row);
                synced = batch.size();
            } catch (RuntimeException e) {
                // 整批失败时逐个重试，定位到具体用户，避免一个坏点拖垮整批
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        recall.upsert(List.of(batch.get(i)));
                        markSynced(batchRows.get(i));
                        synced++;
                    } catch (RuntimeException ex) {
                        markFailed(batchRows.get(i), ex);
                        failed++;
                    }
                }
            }
        }
        int deleted = purgeDeactivated();
        return new Stats(rows.size(), synced, failed, deleted);
    }

    private RecallModel.Point point(Map<String, Object> row, float[] taste, float[] persona) {
        long userId = ((Number) row.get("user_id")).longValue();
        return new RecallModel.Point(userId, intVal(row.get("version"), 1),
                taste, persona, payload(userId, row));
    }

    private Map<String, Object> payload(long userId, Map<String, Object> row) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", userId);
        p.put("school_code", blankTo(str(row.get("school_domain")), "all"));
        p.put("gender", blankTo(GenderNorms.norm(str(row.get("gender"))), "unknown"));
        p.put("expected_gender", expectedGenders(row.get("expected_genders_enc")));
        p.put("age", age(row.get("birthday")));
        p.put("height_cm", intVal(row.get("height_cm"), 0));
        // 真实等时圈：由地点经纬度 + 交通方式 + 通勤分钟本地展开成格网编号集合。
        // 无坐标时回退到 place_id 单点（保持旧数据可用），但绝不伪造" anywhere 都可达"。
        String place = str(row.get("place_id"));
        List<String> cells = Boolean.TRUE.equals(props.isoEnabled())
                ? IsoCellGrid.cellsOrWildcard(dbl(row.get("place_lat")), dbl(row.get("place_lng")),
                        str(row.get("travel_mode")), intOrNull(row.get("travel_minutes")))
                : List.of();
        p.put("iso_cells", cells.isEmpty() ? (place.isEmpty() ? List.of(IsoCellGrid.WILDCARD) : List.of(place)) : cells);
        p.put("interest_tags", interestTags(row));
        p.put("value_tags", valueTags(row));
        p.put("embedding_version", embedding.version());
        p.put("bucket", "prod");
        p.put("is_active", "active".equals(str(row.get("status"))));
        p.put("verified", Boolean.TRUE.equals(row.get("verified")));
        p.put("photo_approved", intVal(row.get("photo_count"), 0) > 0);
        p.put("is_synthetic", Boolean.TRUE.equals(row.get("is_synthetic")));
        p.put("starve_weeks", Math.max(0, intVal(row.get("starve_weeks"), 0)));
        return p;
    }

    private List<String> expectedGenders(Object enc) {
        if (!(enc instanceof byte[] b) || b.length == 0) return GenderNorms.ALL;
        try {
            return GenderNorms.normList(Json.readStringList(crypto.decrypt(b)));
        } catch (RuntimeException e) {
            return GenderNorms.ALL;
        }
    }

    private List<String> interestTags(Map<String, Object> row) {
        List<String> out = new ArrayList<>();
        out.addAll(safeList(row.get("hobbies")));
        Object details = row.get("hobby_details");
        if (details != null) {
            try {
                Json.readMap(String.valueOf(details)).values().forEach(v -> {
                    if (v instanceof String s && !s.isBlank()) out.add(clip(s));
                    else if (v instanceof List<?> l) l.forEach(e -> {
                        if (e instanceof String s && !s.isBlank()) out.add(clip(s));
                    });
                });
            } catch (RuntimeException ignored) {
                // 历史脏数据不影响主流程
            }
        }
        return out.stream().distinct().limit(40).toList();
    }

    private List<String> valueTags(Map<String, Object> row) {
        List<String> out = new ArrayList<>(safeList(row.get("relationship_looking")));
        for (String key : List.of("matching_style", "education_importance", "budget")) {
            String v = str(row.get(key));
            if (!v.isEmpty()) out.add(v);
        }
        return out.stream().distinct().limit(20).toList();
    }

    private int age(Object birthday) {
        if (birthday instanceof java.sql.Date d) {
            return Math.max(18, Period.between(d.toLocalDate(),
                    ClockConfig.shanghaiDate(clock.instant())).getYears());
        }
        return 20;
    }

    private boolean openForVector(Map<String, Object> row) {
        long userId = ((Number) row.get("user_id")).longValue();
        List<String> status = jdbc.query("SELECT status FROM \"user\" WHERE id = ?",
                (rs, n) -> rs.getString(1), userId);
        return !status.isEmpty() && "active".equals(status.get(0));
    }

    /** A closed account does not get a new vector. The reason has no profile text. */
    private void quarantine(Map<String, Object> row) {
        long userId = ((Number) row.get("user_id")).longValue();
        jdbc.update("""
                UPDATE user_taste SET vector_sync_status = 'deleted'
                WHERE user_id = ? AND is_active AND vector_sync_status = 'pending'
                """, userId);
        jdbc.update("""
                UPDATE vector_sync SET status = 'cancelled', error_msg = 'account_not_active'
                WHERE user_id = ? AND status = 'pending' AND op = 'upsert'
                """, userId);
    }

    private void markSynced(Map<String, Object> row) {
        if (!openForVector(row)) {
            quarantine(row);
            return;
        }
        long userId = ((Number) row.get("user_id")).longValue();
        int version = intVal(row.get("version"), 1);
        String pointId = String.valueOf(userId);
        jdbc.update("""
                UPDATE user_taste SET embedding_id = ?, embedding_version = ?, vector_sync_status = 'synced'
                WHERE user_id = ? AND version = ? AND is_active
                """, pointId, embedding.version(), userId, version);
        audit(userId, version, "upsert", "synced", null);
    }

    private void markFailed(Map<String, Object> row, RuntimeException e) {
        long userId = ((Number) row.get("user_id")).longValue();
        int version = intVal(row.get("version"), 1);
        String msg = e.getClass().getSimpleName() + ": " + clip(String.valueOf(e.getMessage()));
        jdbc.update("""
                UPDATE user_taste SET vector_sync_status = 'failed'
                WHERE user_id = ? AND version = ? AND is_active
                """, userId, version);
        audit(userId, version, "upsert", "failed", msg);
    }

    /** 注销 / 封禁 / 删除中的用户必须真删，不能只靠 filter。 */
    private int purgeDeactivated() {
        List<Long> ids = jdbc.queryForList("""
                SELECT u.id FROM "user" u
                WHERE u.status IN ('deactivated','purged','banned','deleting','suspended')
                  AND EXISTS (SELECT 1 FROM user_taste t
                              WHERE t.user_id = u.id AND t.vector_sync_status <> 'deleted')
                """, Long.class);
        if (ids.isEmpty()) return 0;
        recall.delete(ids);
        for (Long id : ids) {
            jdbc.update("""
                    UPDATE user_taste SET vector_sync_status = 'deleted'
                    WHERE user_id = ? AND vector_sync_status <> 'deleted'
                    """, id);
        }
        Timestamp now = Timestamp.from(clock.instant());
        for (Long id : ids) {
            jdbc.update("""
                    INSERT INTO vector_sync (id, user_id, persona_version, collection, vector_kind,
                                             op, status, retry_cnt, error_msg, created_at, synced_at)
                    VALUES (?,?,?,?,?,'delete','synced',0,NULL,?,?)
                    """, newId(), id, 0, props.collection(), "taste", now, now);
        }
        return ids.size();
    }

    private void audit(long userId, int version, String op, String status, String error) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO vector_sync (id, user_id, persona_version, collection, vector_kind,
                                         op, status, retry_cnt, error_msg, created_at, synced_at)
                VALUES (?,?,?,?,?,?,?,0,?,?,?)
                """, newId(), userId, version, props.collection(), "taste", op, status, error, now,
                "synced".equals(status) ? now : null);
    }

    private long newId() {
        return ((Number) ids.nextId(new Object())).longValue();
    }

    private static List<String> safeList(Object o) {
        if (o == null) return List.of();
        try {
            return Json.readStringList(String.valueOf(o));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private static String clip(String s) {
        return s == null ? "" : (s.length() > 40 ? s.substring(0, 40) : s);
    }

    private static int intVal(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
