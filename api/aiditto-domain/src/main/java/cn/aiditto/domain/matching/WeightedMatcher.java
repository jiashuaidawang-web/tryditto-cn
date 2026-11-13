package cn.aiditto.domain.matching;

import java.util.List;

/** Picks a set of vertex-disjoint edges with large total weight. */
public interface WeightedMatcher {
    /** @param edges sorted by {@code (low, high)}; the result is a subset of them */
    List<Edge> match(List<Edge> edges);
}
