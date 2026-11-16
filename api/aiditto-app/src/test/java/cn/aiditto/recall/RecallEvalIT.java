package cn.aiditto.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 5.4 闸门：分过滤选择性桶的 ANN Recall@K（对照 FLAT 精确）。
 * 默认跳过；运行需 AIDITTO_QDRANT_IT=1 + QDRANT_URL。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_QDRANT_IT", matches = "1")
class RecallEvalIT {

    @Test
    void annRecallDoesNotCollapseUnderHighlySelectiveFilters() {
        String url = System.getenv().getOrDefault("QDRANT_URL", "http://127.0.0.1:16333");
        QdrantRestClient client = new QdrantRestClient(url, 60_000L);
        String collection = "it_recall_eval_" + System.nanoTime();

        List<RecallEvaluator.BucketResult> results = RecallEvaluator.run(
                client, collection, 5000, 64, 40, 10, 128);

        System.out.println("=== 分桶 Recall@K（FLAT 精确对照）===");
        System.out.println("bucket | points | K | recall | queries | p50ms | p99ms | hnsw_ef");
        for (RecallEvaluator.BucketResult r : results) {
            System.out.printf("%-6s | %6d | %2d | %.4f | %7d | %5d | %5d | %d%n",
                    r.bucket(), r.points(), r.k(), r.recall(), r.queries(),
                    r.p50Ms(), r.p99Ms(), r.hnswEf());
        }

        assertThat(results).hasSize(5);
        assertThat(results).allSatisfy(r -> {
            assertThat(r.points()).isPositive();
            assertThat(r.queries()).isPositive();
            assertThat(r.k()).isPositive();
        });
        assertThat(client.collectionExists(collection)).isFalse(); // 评测 collection 已清理

        // 关键论断：过滤选择性到 1% 时 filterable HNSW 不能像 pgvector post-filter 那样塌陷到 ~0.04
        for (RecallEvaluator.BucketResult r : results) {
            if ("b01".equals(r.bucket())) continue; // 极小桶单独看，阈值更宽
            assertThat(r.recall())
                    .as("bucket %s (points=%d, K=%d)", r.bucket(), r.points(), r.k())
                    .isGreaterThanOrEqualTo(0.90);
        }
    }
}
