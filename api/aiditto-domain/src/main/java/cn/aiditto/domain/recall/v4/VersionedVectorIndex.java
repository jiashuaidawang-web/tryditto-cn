package cn.aiditto.domain.recall.v4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory versioned index used by tests and the synthetic experiment.
 * A newer profile version replaces an older one. An older write cannot replace a newer one.
 * The same version and digest is idempotent. Delete removes both directions from search.
 */
public final class VersionedVectorIndex {
    public enum Kind { SELF, PREFERENCE }

    public enum Put { STORED, IDEMPOTENT, REJECT_STALE, REJECT_CONFLICT, DELETED }

    public record Point(long userId, Kind kind, long profileVersion, String modelVersion, String digest, float[] vector) {
        public Point {
            vector = vector.clone();
        }
    }

    public record Hit(long userId, double score, long profileVersion, String digest) {}

    private final ConcurrentHashMap<String, Point> points = new ConcurrentHashMap<>();

    public Put put(Point point) {
        String key = key(point.userId(), point.kind());
        synchronized (points) {
            Point current = points.get(key);
            if (current != null && current.profileVersion() > point.profileVersion()) return Put.REJECT_STALE;
            if (current != null && current.profileVersion() == point.profileVersion()) {
                if (current.digest().equals(point.digest()) && current.modelVersion().equals(point.modelVersion())) {
                    return Put.IDEMPOTENT;
                }
                return Put.REJECT_CONFLICT;
            }
            points.put(key, point);
            return Put.STORED;
        }
    }

    public Put delete(long userId) {
        synchronized (points) {
            points.remove(key(userId, Kind.SELF));
            points.remove(key(userId, Kind.PREFERENCE));
            return Put.DELETED;
        }
    }

    public List<Hit> search(Kind kind, float[] query, int limit, String modelVersion) {
        List<Hit> hits = new ArrayList<>();
        synchronized (points) {
            for (Point point : points.values()) {
                if (point.kind() != kind || !point.modelVersion().equals(modelVersion)) continue;
                hits.add(new Hit(point.userId(), cosine(query, point.vector()), point.profileVersion(), point.digest()));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparingLong(Hit::userId));
        if (hits.size() > limit) return List.copyOf(hits.subList(0, limit));
        return List.copyOf(hits);
    }

    public int size() {
        return points.size();
    }

    private static String key(long userId, Kind kind) {
        return userId + ":" + kind.name();
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

    public Map<String, Point> snapshot() {
        return Map.copyOf(points);
    }
}
