package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.RecallProfileContract;
import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.RecallVectorText;
import cn.aiditto.domain.recall.v4.VersionedVectorIndex;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Writes one self vector and one preference vector from the same snapshot.
 * Both embeddings are taken before either side is published. A failure or a withdraw in between
 * leaves the previous snapshot untouched.
 */
public final class V4VectorSync {
    public static final LocalDate EXPERIMENT_WEEK = LocalDate.of(2026, 11, 25);
    public static final Instant EXPERIMENT_NOW = Instant.parse("2026-11-27T06:00:00Z");

    private V4VectorSync() {}

    public static VersionedVectorIndex.Put sync(VersionedVectorIndex index, RecallSubject subject, EmbeddingService embedding) {
        return sync(index, subject, embedding, EXPERIMENT_WEEK, EXPERIMENT_NOW);
    }

    public static VersionedVectorIndex.Put sync(VersionedVectorIndex index, RecallSubject subject, EmbeddingService embedding,
                                                LocalDate week, Instant now) {
        if (!RecallProfileContract.allowsVectorSync(subject, week, now)) {
            return index.withdraw(subject.userId(), subject.generation(), subject.profileVersion());
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
        return index.commit(new VersionedVectorIndex.Snapshot(
                subject.userId(), subject.generation(), subject.profileVersion(), embedding.version(),
                RecallVectorText.digest(self), selfVector, RecallVectorText.digest(preference), preferenceVector));
    }
}
