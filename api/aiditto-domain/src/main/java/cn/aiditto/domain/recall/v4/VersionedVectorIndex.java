package cn.aiditto.domain.recall.v4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index. Both directions of one snapshot become visible together.
 * Withdraw keeps a generation and a profile watermark, so a late task cannot restore the vectors.
 */
public final class VersionedVectorIndex {
    public enum Kind { SELF, PREFERENCE }

    public enum Put {
        STORED, IDEMPOTENT, REJECT_STALE, REJECT_CONFLICT, DELETED, REJECT_WITHDRAWN, REJECT_PARTIAL
    }

    public record Point(long userId, Kind kind, long profileVersion, String modelVersion, String digest, float[] vector) {
        public Point {
            vector = vector.clone();
        }

        @Override
        public float[] vector() {
            return vector.clone();
        }
    }

    public record Snapshot(long userId, long generation, long profileVersion, String modelVersion,
                           String selfDigest, float[] selfVector, String preferenceDigest, float[] preferenceVector) {
        public Snapshot {
            selfVector = selfVector.clone();
            preferenceVector = preferenceVector.clone();
        }

        @Override
        public float[] selfVector() {
            return selfVector.clone();
        }

        @Override
        public float[] preferenceVector() {
            return preferenceVector.clone();
        }
    }

    public record Hit(long userId, double score, long profileVersion, String digest) {}

    private static final class Slot {
        private VectorLifecycle.State state = VectorLifecycle.State.none();
        private Point self;
        private Point preference;
    }

    private final ConcurrentHashMap<Long, Slot> slots = new ConcurrentHashMap<>();

    public Put commit(Snapshot snapshot) {
        synchronized (slots) {
            Slot slot = slots.computeIfAbsent(snapshot.userId(), ignored -> new Slot());
            VectorLifecycle.Decision decision = VectorLifecycle.publish(slot.state, new VectorLifecycle.Publish(
                    snapshot.generation(), snapshot.profileVersion(), snapshot.modelVersion(),
                    snapshot.selfDigest(), snapshot.preferenceDigest()));
            if (decision.outcome() != Put.STORED) return decision.outcome();
            slot.state = decision.state();
            slot.self = point(snapshot, Kind.SELF, snapshot.selfDigest(), snapshot.selfVector());
            slot.preference = point(snapshot, Kind.PREFERENCE, snapshot.preferenceDigest(), snapshot.preferenceVector());
            return Put.STORED;
        }
    }

    public Put withdraw(long userId, long generation, long profileVersion) {
        synchronized (slots) {
            Slot slot = slots.computeIfAbsent(userId, ignored -> new Slot());
            VectorLifecycle.Decision decision = VectorLifecycle.withdraw(slot.state,
                    new VectorLifecycle.Withdraw(generation, profileVersion));
            if (decision.outcome() != Put.DELETED) return decision.outcome();
            slot.state = decision.state();
            slot.self = null;
            slot.preference = null;
            return Put.DELETED;
        }
    }

    public Put authorize(long userId, long generation) {
        synchronized (slots) {
            Slot slot = slots.computeIfAbsent(userId, ignored -> new Slot());
            VectorLifecycle.Decision decision = VectorLifecycle.authorize(slot.state, new VectorLifecycle.Authorize(generation));
            if (decision.outcome() != Put.STORED) return decision.outcome();
            slot.state = decision.state();
            slot.self = null;
            slot.preference = null;
            return Put.STORED;
        }
    }

    public Put delete(long userId) {
        synchronized (slots) {
            Slot slot = slots.get(userId);
            if (slot == null || !slot.state.opened()) return withdraw(userId, 1, 0);
            return withdraw(userId, slot.state.generation(), Math.max(slot.state.highWater(), 0));
        }
    }

    public List<Hit> search(Kind kind, float[] query, int limit, String modelVersion) {
        List<Hit> hits = new ArrayList<>();
        synchronized (slots) {
            for (Slot slot : slots.values()) {
                if (!slot.state.published() || !slot.state.modelVersion().equals(modelVersion)) continue;
                Point point = kind == Kind.SELF ? slot.self : slot.preference;
                if (point == null) continue;
                hits.add(new Hit(point.userId(), cosine(query, point.vector()), point.profileVersion(), point.digest()));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparingLong(Hit::userId));
        if (hits.size() > limit) return List.copyOf(hits.subList(0, limit));
        return List.copyOf(hits);
    }

    public VectorLifecycle.State state(long userId) {
        synchronized (slots) {
            Slot slot = slots.get(userId);
            return slot == null ? VectorLifecycle.State.none() : slot.state;
        }
    }

    public int size() {
        int count = 0;
        synchronized (slots) {
            for (Slot slot : slots.values()) {
                if (slot.state.published() && slot.self != null && slot.preference != null) count += 2;
            }
        }
        return count;
    }

    private static Point point(Snapshot snapshot, Kind kind, String digest, float[] vector) {
        return new Point(snapshot.userId(), kind, snapshot.profileVersion(), snapshot.modelVersion(), digest, vector);
    }

    static double cosine(float[] left, float[] right) {
        if (left.length != right.length) throw new IllegalArgumentException("dimension");
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
