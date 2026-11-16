package cn.aiditto.recall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Qdrant 1.12.x REST 冒烟（默认跳过）。
 * 运行：AIDITTO_QDRANT_IT=1 QDRANT_URL=http://127.0.0.1:16333 mvn -Dtest=QdrantClientIT test
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_QDRANT_IT", matches = "1")
class QdrantClientIT {

    private static QdrantRestClient client() {
        String url = System.getenv().getOrDefault("QDRANT_URL", "http://127.0.0.1:16333");
        return new QdrantRestClient(url, 30_000L);
    }

    @Test
    void createUpsertFilteredSearchScrollAndDelete() {
        QdrantRestClient q = client();
        String collection = "it_recall_" + System.nanoTime();
        int dim = 8;
        try {
            assertThat(q.collectionExists(collection)).isFalse();
            q.createCollection(collection, dim);
            q.createPayloadIndexes(collection);
            assertThat(q.collectionExists(collection)).isTrue();

            HashingEmbeddingService emb = new HashingEmbeddingService(dim);
            List<RecallModel.Point> points = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                String bucket = i <= 3 ? "bucketA" : "bucketB";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("user_id", i);
                payload.put("school_code", "schoolA");
                payload.put("gender", i % 2 == 0 ? "man" : "woman");
                payload.put("expected_gender", List.of("man", "woman"));
                payload.put("age", 20 + i);
                payload.put("height_cm", 165 + i);
                payload.put("iso_cells", List.of("place-1"));
                payload.put("interest_tags", List.of("reading", "music"));
                payload.put("value_tags", List.of("serious"));
                payload.put("embedding_version", "v1");
                payload.put("bucket", bucket);
                payload.put("is_active", true);
                payload.put("verified", true);
                payload.put("photo_approved", true);
                payload.put("is_synthetic", true);
                points.add(new RecallModel.Point(i, 1,
                        emb.embed("口味文本-" + i), emb.embed("人格文本-" + i), payload));
            }
            q.upsert(collection, points, 50);

            RecallModel.Filter f = new RecallModel.Filter(0L, "schoolA",
                    List.of(), null, 18, 30, null, null, List.of(), List.of(),
                    "v1", true, "bucketA");
            List<RecallModel.Hit> hits = q.search(collection, "taste",
                    emb.embed("口味文本-1"), f, 10, 128, RecallModel.Source.R1_TASTE);
            assertThat(hits).hasSize(3);                       // 硬过滤在服务端生效，不是召回后再过滤
            assertThat(hits.stream().map(RecallModel.Hit::userId)).allMatch(id -> id >= 1 && id <= 3);
            assertThat(hits).allMatch(h -> h.source() == RecallModel.Source.R1_TASTE);

            List<QdrantRestClient.VectorPoint> all = q.scroll(collection, f, 100);
            assertThat(all).hasSize(3);
            assertThat(all.get(0).taste()).hasSize(dim);
            assertThat(all.get(0).persona()).hasSize(dim);

            // 同一 filter 换 persona 路也只返回 3 个
            assertThat(q.search(collection, "persona", emb.embed("人格文本-2"), f, 10, 128,
                    RecallModel.Source.R2_PERSONA)).hasSize(3);

            q.delete(collection, List.of(1L));
            assertThat(q.scroll(collection, f, 100)).hasSize(2);
        } finally {
            q.deleteCollection(collection);
            assertThat(q.collectionExists(collection)).isFalse();
        }
    }
}
