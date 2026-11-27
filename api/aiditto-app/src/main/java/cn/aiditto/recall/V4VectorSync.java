package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.RecallVectorText;
import cn.aiditto.domain.recall.v4.VersionedVectorIndex;

/** Writes separate self and preference vectors. It does not open a production collection. */
public final class V4VectorSync {
    private V4VectorSync() {}

    public static VersionedVectorIndex.Put sync(VersionedVectorIndex index, RecallSubject subject, EmbeddingService embedding) {
        if (!subject.searchable()) return index.delete(subject.userId());
        String self = RecallVectorText.self(subject);
        String preference = RecallVectorText.preference(subject);
        VersionedVectorIndex.Put selfPut = index.put(point(subject, VersionedVectorIndex.Kind.SELF, self, embedding));
        VersionedVectorIndex.Put preferencePut = index.put(point(subject, VersionedVectorIndex.Kind.PREFERENCE, preference, embedding));
        if (selfPut == VersionedVectorIndex.Put.REJECT_STALE || preferencePut == VersionedVectorIndex.Put.REJECT_STALE) {
            return VersionedVectorIndex.Put.REJECT_STALE;
        }
        if (selfPut == VersionedVectorIndex.Put.REJECT_CONFLICT || preferencePut == VersionedVectorIndex.Put.REJECT_CONFLICT) {
            return VersionedVectorIndex.Put.REJECT_CONFLICT;
        }
        if (selfPut == VersionedVectorIndex.Put.STORED || preferencePut == VersionedVectorIndex.Put.STORED) {
            return VersionedVectorIndex.Put.STORED;
        }
        return VersionedVectorIndex.Put.IDEMPOTENT;
    }

    private static VersionedVectorIndex.Point point(RecallSubject subject, VersionedVectorIndex.Kind kind,
                                                    String text, EmbeddingService embedding) {
        return new VersionedVectorIndex.Point(subject.userId(), kind, subject.profileVersion(), embedding.version(),
                RecallVectorText.digest(text), embedding.embed(text));
    }
}
