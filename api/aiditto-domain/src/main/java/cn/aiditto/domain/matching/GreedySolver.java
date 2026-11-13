package cn.aiditto.domain.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fallback (技术方案 §7.5): heaviest edge first, skip edges touching an already matched person.
 * A 1/2-approximation of the maximum weight matching.
 */
public final class GreedySolver implements WeightedMatcher {

    @Override
    public List<Edge> match(List<Edge> edges) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingLong(Edge::weight).reversed()
                .thenComparingLong(Edge::low)
                .thenComparingLong(Edge::high));
        Set<Long> used = new HashSet<>();
        List<Edge> out = new ArrayList<>();
        for (Edge e : sorted) {
            if (used.contains(e.low()) || used.contains(e.high())) continue;
            used.add(e.low());
            used.add(e.high());
            out.add(e);
        }
        return out;
    }
}
