package cn.aiditto.domain.recall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 倒数排名融合（RRF）：score(d) = Σ_i w_i / (k + rank_i(d))，默认 k=60。
 * 纯函数，可单测；同一候选出现在多路时累加各路贡献，并保留 sources[] 供归因与单路占比监控。
 */
public final class RrfFusion {

    public record Ranked(long userId, int rank, String source) {}

    public record Fused(long userId, double score, List<String> sources) {
        public Fused {
            sources = List.copyOf(sources);
        }
    }

    private RrfFusion() {}

    public static List<Fused> merge(List<List<Ranked>> routes, Map<String, Double> weights, double k) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, Set<String>> sources = new LinkedHashMap<>();
        for (List<Ranked> route : routes) {
            for (Ranked r : route) {
                if (r == null || r.rank() <= 0) continue;
                double w = weights.getOrDefault(r.source(), 1.0);
                double contribution = w / (k + r.rank());
                scores.merge(r.userId(), contribution, Double::sum);
                sources.computeIfAbsent(r.userId(), ignored -> new LinkedHashSet<>()).add(r.source());
            }
        }
        List<Fused> out = new ArrayList<>();
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            out.add(new Fused(e.getKey(), e.getValue(), new ArrayList<>(sources.get(e.getKey()))));
        }
        out.sort(Comparator.comparingDouble(Fused::score).reversed()
                .thenComparingLong(Fused::userId));
        return out;
    }

    /** 单路占比 = 该路贡献的候选数 / 总候选数；用于「单路 ≤60%」告警。 */
    public static double share(List<Fused> fused, String source) {
        if (fused.isEmpty()) return 0;
        long n = fused.stream().filter(f -> f.sources().contains(source)).count();
        return (double) n / fused.size();
    }
}
