package cn.aiditto.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1–E14 实验矩阵的真 Qdrant 部分（Step 5「只跑机制验证」偏离的补全）。
 *
 * 默认跳过；运行需 AIDITTO_QDRANT_IT=1 + AIDITTO_RECALL_MATRIX=1 + QDRANT_URL。
 * 与 RecallEvalIT（5000 点 / 64 维 / 40 query 机制档）分开，避免拖慢日常 verify。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_RECALL_MATRIX", matches = "1")
class RecallExperimentIT {

    private static String url() {
        return System.getenv().getOrDefault("QDRANT_URL", "http://127.0.0.1:16333");
    }

    @Test
    void efGridShowsRecallLatencyTradeoff() {
        QdrantRestClient client = new QdrantRestClient(url(), 120_000L);
        String c = "it_matrix_ef_" + System.nanoTime();

        List<RecallEvaluator.Experiment> exps = RecallEvaluator.sweepEf(
                client, c, 3000, 128, 40, 10, List.of(32, 64, 128, 256));

        System.out.println("=== E9/E14 hnsw_ef 网格（3000 点 / 128 维 / 每桶 40 query）===");
        System.out.println("ef  | minRecall | gate | totalMs | 各桶 Recall");
        for (RecallEvaluator.Experiment e : exps) {
            int ef = e.buckets().get(0).hnswEf();
            System.out.printf("%3d | %.4f     | %-5s | %7d | %s%n", ef, e.minRecall(),
                    e.gatePassed() ? "PASS" : "FAIL", e.totalMs(),
                    e.buckets().stream().map(b -> b.bucket() + "=" + String.format("%.3f", b.recall()))
                            .toList());
        }
        assertThat(exps).hasSize(4);
        assertThat(exps).allSatisfy(e -> {
            assertThat(e.buckets()).hasSize(5);
            assertThat(e.buckets()).allSatisfy(b -> assertThat(b.queries()).isPositive());
        });
        // 提高 ef 不应降低 Recall（HNSW 单调性），最高档必须达标
        assertThat(exps.get(3).minRecall()).isGreaterThanOrEqualTo(exps.get(0).minRecall() - 0.02);
        assertThat(exps.get(3).minRecall()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void productionScaleDoesNotCollapseUnderSelectiveFilters() {
        QdrantRestClient client = new QdrantRestClient(url(), 180_000L);
        String c = "it_matrix_prod_" + System.nanoTime();

        RecallEvaluator.Experiment e = RecallEvaluator.sweepEf(
                client, c, 10000, 256, 60, 10, List.of(128)).get(0);

        System.out.println("=== 生产规模档（10000 点 / 256 维 / 每桶 60 query / ef=128）===");
        System.out.printf("minRecall=%.4f gate=%s totalMs=%d%n",
                e.minRecall(), e.gatePassed() ? "PASS" : "FAIL", e.totalMs());
        for (RecallEvaluator.BucketResult b : e.buckets()) {
            System.out.printf("  %-4s points=%5d K=%2d recall=%.4f q=%3d p50=%dms p99=%dms%n",
                    b.bucket(), b.points(), b.k(), b.recall(), b.queries(), b.p50Ms(), b.p99Ms());
        }
        assertThat(e.n()).isEqualTo(10000);
        assertThat(e.dimension()).isEqualTo(256);
        assertThat(e.buckets()).hasSize(5);
        // 关键：1% / 0.1% 高选择性桶不得塌陷（pgvector post-filter 在 1% 桶是 0.039）
        for (RecallEvaluator.BucketResult b : e.buckets()) {
            if (!"b01".equals(b.bucket())) {
                assertThat(b.recall()).as("bucket %s", b.bucket()).isGreaterThanOrEqualTo(0.90);
            }
        }
    }
}
