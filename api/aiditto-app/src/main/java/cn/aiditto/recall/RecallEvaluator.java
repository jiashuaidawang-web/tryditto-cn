package cn.aiditto.recall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分选择性桶的 ANN Recall 评测（召回策略规格 §4.1 / E9 / E14）。
 *
 * 生成确定性合成向量池，把点按 50/10/5/1/0.1% 比例分到互斥桶，强制走 HNSW 索引
 * （full_scan_threshold 取 Qdrant 允许的最小值 10，避免小数据集自动精确扫描掩盖
 * post-filter 塌陷），用同一 filter 分别跑 ANN 与 FLAT 精确，输出各桶 Recall@K 与延迟。
 *
 * Step 5 偏离补全：新增 sweepEf() 支持 hnsw_ef 网格扫描，并支持生产规模参数
 * （万级点 / 1024 维 / 每桶 ≥200 query），由 RecallExperimentIT 真跑；
 * 纯函数侧的逐路消融 / 权重网格在 domain 层 RecallMatrix。
 */
public final class RecallEvaluator {

    public record BucketResult(String bucket, int points, int k, double recall,
                               int queries, long p50Ms, long p99Ms, int hnswEf) {}

    /** 一次完整实验的汇总，可直接喂 Go/No-Go 报告。 */
    public record Experiment(int n, int dimension, int queriesPerBucket, int k,
                             List<BucketResult> buckets, double minRecall,
                             long totalMs, boolean gatePassed) {}

    private static final List<String> BUCKETS = List.of("b50", "b10", "b5", "b1", "b01");

    private RecallEvaluator() {}

    /** 兼容既有 RecallEvalIT 的单 ef 入口。 */
    public static List<BucketResult> run(QdrantRestClient client, String collection,
                                         int n, int dimension, int queriesPerBucket,
                                         int k, int hnswEf) {
        return runWithEfGrid(client, collection, n, dimension, queriesPerBucket, k,
                List.of(hnswEf)).buckets();
    }

    /**
     * hnsw_ef 网格扫描：只建一次库，对每个 ef 值跑全部选择性桶，输出每个 ef 的最小 Recall。
     * 用于 E9/E14 参数调优（ Recall 与延迟的权衡点）。
     */
    public static List<Experiment> sweepEf(QdrantRestClient client, String collection,
                                           int n, int dimension, int queriesPerBucket,
                                           int k, List<Integer> efGrid) {
        List<Experiment> out = new ArrayList<>();
        for (int ef : efGrid) {
            Experiment e = runWithEfGrid(client, collection + "_ef" + ef,
                    n, dimension, queriesPerBucket, k, List.of(ef));
            out.add(e);
        }
        return out;
    }

    private static Experiment runWithEfGrid(QdrantRestClient client, String collection,
                                            int n, int dimension, int queriesPerBucket,
                                            int k, List<Integer> efGrid) {
        long t0 = System.currentTimeMillis();
        int ef = efGrid.get(0);
        List<BucketResult> results = new ArrayList<>();
        try {
            client.createCollection(collection, dimension, 10);
            client.createPayloadIndexes(collection);
            HashingEmbeddingService emb = new HashingEmbeddingService(dimension);
            List<RecallModel.Point> points = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("user_id", i);
                payload.put("embedding_version", "eval-v1");
                payload.put("bucket", bucketOf(i));
                payload.put("is_active", true);
                payload.put("verified", true);
                payload.put("photo_approved", true);
                payload.put("is_synthetic", true);
                payload.put("school_code", "eval");
                payload.put("age", 20 + (i % 10));
                payload.put("height_cm", 160 + (i % 30));
                points.add(new RecallModel.Point(i, 1,
                        emb.embed("eval-taste-" + i + "-alpha-" + (i * 37)),
                        emb.embed("eval-persona-" + i + "-beta-" + (i * 17)), payload));
            }
            client.upsert(collection, points, 100);

            for (String bucket : BUCKETS) {
                RecallModel.Filter f = filter(0L, bucket);
                List<QdrantRestClient.VectorPoint> pts = client.scroll(collection, f, 2000);
                int effectiveK = Math.min(k, Math.max(1, pts.size() - 1));
                if (pts.size() < 2 || effectiveK < 1) {
                    results.add(new BucketResult(bucket, pts.size(), 0, 0, 0, 0, 0, ef));
                    continue;
                }
                List<Long> latencies = new ArrayList<>();
                double sumRecall = 0;
                int queries = Math.min(queriesPerBucket, pts.size());
                for (int q = 0; q < queries; q++) {
                    QdrantRestClient.VectorPoint probe = pts.get((q * 7 + 3) % pts.size());
                    List<Long> exact = ExactRecallService.exactTop(pts, probe.taste(), true,
                            effectiveK, probe.id());
                    long s = System.currentTimeMillis();
                    List<RecallModel.Hit> ann = client.search(collection, "taste", probe.taste(),
                            filter(probe.id(), bucket), effectiveK, ef,
                            RecallModel.Source.R1_TASTE);
                    latencies.add(System.currentTimeMillis() - s);
                    sumRecall += ExactRecallService.recall(
                            ann.stream().map(RecallModel.Hit::userId).toList(), exact);
                }
                List<Long> sorted = latencies.stream().sorted().toList();
                long p50 = sorted.get(sorted.size() / 2);
                long p99 = sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99)));
                results.add(new BucketResult(bucket, pts.size(), effectiveK,
                        queries == 0 ? 0 : sumRecall / queries, queries, p50, p99, ef));
            }
        } finally {
            client.deleteCollection(collection);
        }
        double minRecall = results.stream().mapToDouble(BucketResult::recall).min().orElse(0);
        return new Experiment(n, dimension, queriesPerBucket, k, results, minRecall,
                System.currentTimeMillis() - t0, minRecall >= 0.90);
    }

    /** 互斥分桶：每 1000 个点里 500/100/50/10/1 个落入目标选择性桶。 */
    static String bucketOf(int i) {
        int r = i % 1000;
        if (r < 500) return "b50";
        if (r < 600) return "b10";
        if (r < 650) return "b5";
        if (r < 660) return "b1";
        if (r == 660) return "b01";
        return "rest";
    }

    private static RecallModel.Filter filter(long selfId, String bucket) {
        return new RecallModel.Filter(selfId, null, List.of(), null, null, null, null, null,
                List.of(), List.of(), "eval-v1", true, bucket);
    }
}
