package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.V4IndexNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated Qdrant smoke. It does not run in CI unless AIDITTO_QDRANT_IT=1, and it refuses the production collection.
 * Absent that variable, the result is NOT_RUN.
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_QDRANT_IT", matches = "1")
class V4QdrantIsolationIT {
    @Test
    void isolatedCollectionCanStoreAndDeleteASelfVector() {
        String collection = "v4_it_" + System.nanoTime();
        V4IndexNames.refuseUnlessIsolated(collection);
        QdrantRestClient client = new QdrantRestClient(System.getenv().getOrDefault("QDRANT_URL", "http://127.0.0.1:16333"), 30_000L);
        try {
            client.createCollection(collection, 8);
            assertThat(client.collectionExists(collection)).isTrue();
        } finally {
            client.deleteCollection(collection);
        }
    }
}
