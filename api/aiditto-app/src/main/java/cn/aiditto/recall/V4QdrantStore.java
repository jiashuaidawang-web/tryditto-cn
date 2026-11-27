package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.RecallProfileContract;
import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.RecallVectorText;
import cn.aiditto.domain.recall.v4.V4IndexNames;
import cn.aiditto.domain.recall.v4.VectorLifecycle;
import cn.aiditto.domain.recall.v4.VersionedVectorIndex;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One isolated Qdrant collection. A point is written only after both vectors exist.
 * The payload keeps the generation and profile watermark. This client never opens {@code match_user_v1}.
 * Hash embeddings used here are an engineering stand-in, not a semantic model.
 */
public final class V4QdrantStore {
    private final QdrantRestClient client;
    private final String collection;
    private final int dimension;
    private final Object lock = new Object();

    public V4QdrantStore(QdrantRestClient client, String collection, int dimension) {
        V4IndexNames.refuseUnlessIsolated(collection);
        this.client = client;
        this.collection = collection;
        this.dimension = dimension;
        client.createV4Collection(collection, dimension);
    }

    public VersionedVectorIndex.Put sync(RecallSubject subject, EmbeddingService embedding, LocalDate week, Instant now) {
        synchronized (lock) {
            VectorLifecycle.State state = read(subject.userId());
            if (!RecallProfileContract.allowsVectorSync(subject, week, now)) {
                return apply(subject.userId(), VectorLifecycle.withdraw(state,
                        new VectorLifecycle.Withdraw(subject.generation(), subject.profileVersion())), null, null);
            }
            String self = RecallVectorText.self(subject);
            String preference = RecallVectorText.preference(subject);
            float[] selfVector;
            float[] preferenceVector;
            try {
                selfVector = embedding.embed(self);
                preferenceVector = embedding.embed(preference);
            } catch (RuntimeException ex) {
                return VersionedVectorIndex.Put.REJECT_PARTIAL;
            }
            VectorLifecycle.Decision decision = VectorLifecycle.publish(state, new VectorLifecycle.Publish(
                    subject.generation(), subject.profileVersion(), embedding.version(),
                    RecallVectorText.digest(self), RecallVectorText.digest(preference)));
            return apply(subject.userId(), decision, selfVector, preferenceVector);
        }
    }

    public VersionedVectorIndex.Put authorize(long userId, long generation) {
        synchronized (lock) {
            return apply(userId, VectorLifecycle.authorize(read(userId), new VectorLifecycle.Authorize(generation)), null, null);
        }
    }

    public java.util.List<Long> search(String kind, float[] vector, String modelVersion) {
        V4IndexNames.refuseUnlessIsolated(collection);
        return client.searchV4(collection, kind, vector, modelVersion, 10);
    }

    private VersionedVectorIndex.Put apply(long userId, VectorLifecycle.Decision decision, float[] self, float[] preference) {
        if (decision.outcome() != VersionedVectorIndex.Put.STORED && decision.outcome() != VersionedVectorIndex.Put.DELETED) {
            return decision.outcome();
        }
        float[] left = self == null ? placeholder() : self;
        float[] right = preference == null ? placeholder() : preference;
        client.upsertV4(collection, userId, left, right, payload(userId, decision.state()));
        return decision.outcome();
    }

    private VectorLifecycle.State read(long userId) {
        JsonNode point = client.retrieveV4(collection, userId);
        if (point == null || point.get("payload") == null || point.get("payload").isNull()) return VectorLifecycle.State.none();
        JsonNode payload = point.get("payload");
        return new VectorLifecycle.State(
                payload.path("generation").asLong(),
                payload.path("profile_version").asLong(),
                payload.path("opened").asBoolean(),
                payload.path("authorized").asBoolean(),
                payload.path("withdrawn").asBoolean(),
                payload.path("visible").asBoolean(),
                payload.path("model_version").asText(""),
                payload.path("self_digest").asText(""),
                payload.path("preference_digest").asText(""));
    }

    private float[] placeholder() {
        float[] vector = new float[dimension];
        vector[0] = 1f;
        return vector;
    }

    private static Map<String, Object> payload(long userId, VectorLifecycle.State state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("generation", state.generation());
        payload.put("profile_version", state.highWater());
        payload.put("opened", state.opened());
        payload.put("authorized", state.authorized());
        payload.put("withdrawn", state.withdrawn());
        payload.put("visible", state.published());
        payload.put("model_version", state.modelVersion());
        payload.put("self_digest", state.selfDigest());
        payload.put("preference_digest", state.preferenceDigest());
        return payload;
    }
}
