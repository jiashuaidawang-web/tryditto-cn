package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.RecallBench;
import cn.aiditto.domain.recall.v4.RecallIngress;
import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.RecallVectorText;
import cn.aiditto.domain.recall.v4.V4IndexNames;
import cn.aiditto.domain.recall.v4.VersionedVectorIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Isolated Qdrant. Hash vectors only prove the write, filter, and withdraw path.
 * They are not a semantic recall measurement. The production collection is refused.
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_QDRANT_IT", matches = "1")
class V4QdrantIsolationIT {
    @Test
    void isolatedSelfAndPreferenceStayOnOneSnapshotAndADelayedTaskCannotRestoreThem() {
        assertThatThrownBy(() -> V4IndexNames.refuseUnlessIsolated("match_user_v1"))
                .isInstanceOf(IllegalArgumentException.class);
        String collection = "v4_it_" + System.nanoTime();
        QdrantRestClient client = new QdrantRestClient(System.getenv().getOrDefault("QDRANT_URL", "http://127.0.0.1:6333"), 30_000L);
        EmbeddingService embedding = new HashingEmbeddingService(8);
        assertThat(embedding.version()).startsWith("hash-");
        RecallSubject current = RecallBench.load().subjects().stream().filter(subject -> subject.userId() == 3).findFirst().orElseThrow();
        try {
            V4QdrantStore store = new V4QdrantStore(client, collection, embedding.dimension());
            assertThat(store.sync(current, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.STORED);
            assertThat(store.sync(current, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.IDEMPOTENT);
            float[] self = embedding.embed(RecallVectorText.self(current));
            float[] preference = embedding.embed(RecallVectorText.preference(current));
            assertThat(store.search("self", self, embedding.version())).contains(current.userId());
            assertThat(store.search("preference", preference, embedding.version())).contains(current.userId());
            EmbeddingService failing = new EmbeddingService() {
                private int calls;
                public String version() { return embedding.version(); }
                public int dimension() { return embedding.dimension(); }
                public float[] embed(String text) {
                    if (++calls >= 2) throw new IllegalStateException("timeout");
                    return embedding.embed(text);
                }
            };
            assertThat(store.sync(current, failing, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.REJECT_PARTIAL);
            assertThat(store.search("self", self, embedding.version())).contains(current.userId());
            RecallSubject withdrawn = new RecallSubject(current.userId(), current.split(), current.profileVersion(), true, true,
                    current.fixtureSource(), current.candidate(), current.applicant(), current.consent(),
                    current.selfEnums(), current.hobbies(), current.preferenceEnums(), current.generation(), current.ingress());
            assertThat(store.sync(withdrawn, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.DELETED);
            assertThat(store.search("self", self, embedding.version())).doesNotContain(current.userId());
            assertThat(store.sync(current, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.REJECT_WITHDRAWN);
            assertThat(store.search("preference", preference, embedding.version())).doesNotContain(current.userId());
            assertThat(store.authorize(current.userId(), 2)).isEqualTo(VersionedVectorIndex.Put.STORED);
            RecallSubject renewed = new RecallSubject(current.userId(), current.split(), 3, true, false, current.fixtureSource(),
                    current.candidate(), current.applicant(), current.consent(), current.selfEnums(), current.hobbies(),
                    current.preferenceEnums(), 2, RecallIngress.SYNTHETIC_FIXTURE);
            assertThat(store.sync(renewed, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.STORED);
            assertThat(store.sync(withdrawn, embedding, V4VectorSync.EXPERIMENT_WEEK, V4VectorSync.EXPERIMENT_NOW))
                    .isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
            assertThat(store.search("self", embedding.embed(RecallVectorText.self(renewed)), embedding.version()))
                    .contains(current.userId());
        } finally {
            client.deleteCollection(collection);
        }
    }
}
