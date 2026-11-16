package cn.aiditto.it;

import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.match.SyntheticLoader;
import cn.aiditto.recall.EmbeddingService;
import cn.aiditto.recall.QdrantRestClient;
import cn.aiditto.recall.RecallModel;
import cn.aiditto.recall.RecallPipeline;
import cn.aiditto.recall.RecallProperties;
import cn.aiditto.recall.RecallService;
import cn.aiditto.recall.StructuredRecallService;
import cn.aiditto.recall.VectorSyncService;
import cn.aiditto.taste.TasteBatchService;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5.2 闸门：200 合成画像全量同步真实 Qdrant、PG 回填、可重入、注销真删。
 * 默认跳过；运行需 AIDITTO_QDRANT_IT=1 + QDRANT_URL 指向可用实例（本机走 SSH 隧道）。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_QDRANT_IT", matches = "1")
class VectorSyncIT extends ApiIT {

    // collection 可配：真实语义向量必须用独立 collection，禁止与 hash 向量混算
    private static final String COLLECTION = envDefault("QDRANT_COLLECTION", "it_vector_sync");

    private static String envDefault(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    @DynamicPropertySource
    static void qdrant(DynamicPropertyRegistry r) {
        r.add("aiditto.recall.qdrant-url", () -> envDefault("QDRANT_URL", "http://127.0.0.1:16333"));
        r.add("aiditto.recall.collection", () -> COLLECTION);
        // 默认仍是确定性 hash（26 个既有 IT 行为不变）；
        // 设 EMBEDDING_MODE=openai + 相关环境变量即切换真实语义向量，供一键验收脚本使用。
        r.add("aiditto.recall.embedding-mode", () -> envDefault("EMBEDDING_MODE", "hash"));
        r.add("aiditto.recall.embedding-base-url", () -> envDefault("EMBEDDING_BASE_URL", ""));
        r.add("aiditto.recall.embedding-api-key", () -> envDefault("EMBEDDING_API_KEY", ""));
        r.add("aiditto.recall.embedding-model", () -> envDefault("EMBEDDING_MODEL", "text-embedding-v3"));
        r.add("aiditto.recall.dimension", () -> envDefault("EMBEDDING_DIM", "1024"));
        r.add("aiditto.recall.embedding-send-dimensions",
                () -> envDefault("EMBEDDING_SEND_DIMENSIONS", "true"));
        r.add("aiditto.recall.embedding-batch-size", () -> envDefault("EMBEDDING_BATCH_SIZE", "5"));
        r.add("aiditto.recall.embedding-max-batch-items",
                () -> envDefault("EMBEDDING_MAX_BATCH_ITEMS", "10"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired SyntheticLoader synthetic;
    @Autowired TasteBatchService tastes;
    @Autowired VectorSyncService sync;
    @Autowired QdrantRestClient qdrant;
    @Autowired EmbeddingService embedding;
    @Autowired RecallPipeline pipeline;
    @Autowired StructuredRecallService structured;
    @Autowired RecallProperties recallProps;
    @Autowired Crypto crypto;
    @Autowired IdentifierGenerator identifierGenerator;
    @Autowired Clock clock;

    @Test
    void syncsAllPendingTastesIdempotentlyAndPurgesDeactivatedUsers() {
        try {
            qdrant.deleteCollection(COLLECTION);
        } catch (RuntimeException ignored) {
            // 首次运行不存在
        }
        jdbc.update("DELETE FROM vector_sync");
        jdbc.update("DELETE FROM taste_log");
        jdbc.update("DELETE FROM user_taste");

        synthetic.load();
        tastes.refreshMissing(1000);
        long active = count("SELECT COUNT(*) FROM user_taste WHERE is_active AND vector_sync_status = 'pending'");
        assertThat(active).isGreaterThanOrEqualTo(200);

        VectorSyncService.Stats first = sync.syncAll(1000);
        assertThat(first.scanned()).isEqualTo((int) active);
        assertThat(first.synced()).isEqualTo((int) active);
        assertThat(first.failed()).isZero();

        // PG 回填：embedding_id / 真实 embedding_version / synced
        assertThat(count("SELECT COUNT(*) FROM user_taste WHERE is_active AND vector_sync_status = 'synced'"))
                .isEqualTo(active);
        assertThat(count("SELECT COUNT(*) FROM user_taste WHERE is_active AND embedding_id IS NULL")).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_taste WHERE is_active AND embedding_version = 'v0-placeholder'"))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT DISTINCT embedding_version FROM user_taste WHERE is_active LIMIT 1", String.class))
                .isEqualTo(embedding.version());
        assertThat(count("SELECT COUNT(*) FROM vector_sync WHERE status = 'synced' AND op = 'upsert'"))
                .isGreaterThanOrEqualTo(active);

        // Qdrant 真实点数与 payload 无 PII
        RecallModel.Filter all = new RecallModel.Filter(0L, null, List.of(), null,
                null, null, null, null, List.of(), List.of(), embedding.version(), true, null);
        long points = qdrant.count(COLLECTION, all);
        assertThat(points).isEqualTo(active);
        List<QdrantRestClient.VectorPoint> sample = qdrant.scroll(COLLECTION, all, 3);
        assertThat(sample).isNotEmpty();
        assertThat(sample.get(0).taste()).hasSize(embedding.dimension());
        assertThat(sample.get(0).persona()).hasSize(embedding.dimension());
        assertThat(sample.get(0).payload()).containsKeys("school_code", "gender", "age",
                "embedding_version", "is_active", "verified", "photo_approved", "is_synthetic")
                .doesNotContainKeys("phone", "email", "name", "school_email", "wechat");

        // 可重入：无 pending 时再跑不产生重复写入
        VectorSyncService.Stats second = sync.syncAll(1000);
        assertThat(second.scanned()).isZero();
        assertThat(qdrant.count(COLLECTION, all)).isEqualTo(points);
        assertThat(count("SELECT COUNT(*) FROM user_taste")).isEqualTo(active);

        // 注销用户必须真删，且 replay 不得召回
        Long uid = jdbc.queryForObject(
                "SELECT MIN(user_id) FROM user_taste WHERE is_active", Long.class);
        assertThat(uid).isNotNull();
        jdbc.update("UPDATE \"user\" SET status = 'deactivated' WHERE id = ?", uid);
        VectorSyncService.Stats third = sync.syncAll(1000);
        assertThat(third.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(qdrant.count(COLLECTION, all)).isEqualTo(points - 1);
        assertThat(jdbc.queryForObject("""
                SELECT vector_sync_status FROM user_taste WHERE user_id = ? AND is_active
                """, String.class, uid)).isEqualTo("deleted");

        // ---- 5.3 五路召回 + RRF + 粗排 + 留痕 ----
        Long live = jdbc.queryForObject("""
                SELECT MIN(user_id) FROM user_taste WHERE is_active AND vector_sync_status = 'synced'
                """, Long.class);
        assertThat(live).isNotNull();
        RecallPipeline.Outcome o = pipeline.recall(live, 9001L);
        assertThat(o.engine()).isNotEqualTo("no-taste").isNotEqualTo("structured-only");
        assertThat(o.fusedCount()).isPositive();
        assertThat(o.coarse()).isNotEmpty();
        assertThat(o.shares()).containsKeys("R1_TASTE", "R2_PERSONA", "R3_TAGS",
                "R4_PREFERENCE", "R5_EXPLORE");
        long contributing = o.shares().values().stream().filter(v -> v > 0).count();
        assertThat(contributing).as("五路中至少三路有非零贡献").isGreaterThanOrEqualTo(3);
        // 闸门实测：把单路占比与候选覆盖率打进报告，不拿"待 Step 9"含糊带过
        double maxShare = o.shares().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double exclusive = cn.aiditto.domain.recall.RecallMatrix.maxExclusiveRouteShare(
                o.coarse().stream().map(c -> new cn.aiditto.domain.recall.RrfFusion.Fused(
                        c.userId(), c.rrfScore(), c.sources().stream().map(Enum::name).toList())).toList());
        System.out.printf("[RECALL-GATE] user=%d fused=%d coarse=%d consensusShare=%.4f exclusiveShare=%.4f shares=%s%n",
                live, o.fusedCount(), o.coarse().size(), maxShare, exclusive, o.shares());
        assertThat(maxShare).as("五路共识占比应在 [0,1]").isBetween(0.0, 1.0);
        assertThat(exclusive).as("独占单路占比应在 [0,1]").isBetween(0.0, 1.0);
        assertThat(count("SELECT COUNT(*) FROM recall_candidate WHERE week_id = 9001 AND user_id = " + live))
                .isEqualTo(o.coarse().size());
        assertThat(count("SELECT COUNT(*) FROM recall_candidate WHERE week_id = 9001 AND cardinality(sources) = 0"))
                .isZero();
        pipeline.recall(live, 9001L); // 幂等：同一 week/用户/候选不重复
        assertThat(count("SELECT COUNT(*) FROM recall_candidate WHERE week_id = 9001 AND user_id = " + live))
                .isEqualTo(o.coarse().size());

        // Qdrant 不可用演练：降级到结构化召回，仍必须有候选可交付
        RecallService dead = new RecallService() {
            public String engine() { return "dead"; }
            public void ensureCollection() { throw new IllegalStateException("qdrant down"); }
            public void upsert(List<RecallModel.Point> points) { throw new IllegalStateException("qdrant down"); }
            public List<RecallModel.Hit> taste(float[] v, RecallModel.Filter f, int limit, int ef) {
                throw new IllegalStateException("qdrant down");
            }
            public List<RecallModel.Hit> persona(float[] v, RecallModel.Filter f, int limit, int ef) {
                throw new IllegalStateException("qdrant down");
            }
            public void delete(List<Long> ids) { throw new IllegalStateException("qdrant down"); }
        };
        RecallPipeline fallback = new RecallPipeline(jdbc, dead, embedding, structured,
                recallProps, crypto, identifierGenerator, clock);
        RecallPipeline.Outcome d = fallback.recall(live, 9002L);
        assertThat(d.engine()).isEqualTo("structured-only");
        assertThat(d.degraded()).isTrue();
        assertThat(d.coarse()).isNotEmpty();
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }
}
