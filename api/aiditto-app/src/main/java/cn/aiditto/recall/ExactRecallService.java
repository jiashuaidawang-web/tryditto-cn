package cn.aiditto.recall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FLAT 精确对照（ground truth）。不做 ANN：对同一 filter 下的全部向量算全量余弦，
 * 按分排序取 topK。只用于离线评测与每日影子对照，不服务在线。
 */
public final class ExactRecallService {

    public record Scored(long id, double score) {}

    private ExactRecallService() {}

    public static List<Long> exactTop(List<QdrantRestClient.VectorPoint> points, float[] query,
                                      boolean tasteVector, int k, long excludeId) {
        List<Scored> scored = new ArrayList<>();
        for (QdrantRestClient.VectorPoint p : points) {
            if (p.id() == excludeId) continue;
            float[] v = tasteVector ? p.taste() : p.persona();
            scored.add(new Scored(p.id(), dot(query, v)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingLong(Scored::id));
        return scored.stream().limit(k).map(Scored::id).toList();
    }

    public static double recall(List<Long> ann, List<Long> exact) {
        if (exact.isEmpty()) return 1.0;
        long hit = ann.stream().filter(exact::contains).count();
        return (double) hit / exact.size();
    }

    private static double dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }
}
