package cn.aiditto.domain.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jgrapht.alg.interfaces.MatchingAlgorithm;
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching;
import org.jgrapht.alg.matching.blossom.v5.ObjectiveSense;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

/**
 * Exact maximum weight matching on a general graph (same-sex and "everyone" preferences make the
 * graph non-bipartite), via JGraphT's Blossom V.
 *
 * <p>Blossom V solves maximum weight <em>perfect</em> matching, so the graph is doubled first: every
 * vertex {@code v} gets a copy {@code v'}, every edge {@code (u,v)} also appears as {@code (u',v')}, and
 * {@code (v,v')} has weight 0. A maximum weight perfect matching of the doubled graph restricted to the
 * original vertices is a maximum weight matching of the original graph (技术方案 §7.4).
 */
public final class BlossomMatcher implements WeightedMatcher {

    @Override
    public List<Edge> match(List<Edge> edges) {
        if (edges.isEmpty()) return List.of();
        TreeSet<Long> ids = new TreeSet<>();
        for (Edge e : edges) {
            ids.add(e.low());
            ids.add(e.high());
        }
        int n = ids.size();
        Map<Long, Integer> index = new HashMap<>();
        for (long id : ids) index.put(id, index.size());

        SimpleWeightedGraph<Integer, DefaultWeightedEdge> g = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (int v = 0; v < 2 * n; v++) g.addVertex(v);
        Map<DefaultWeightedEdge, Edge> original = new HashMap<>();
        for (Edge e : edges) {
            int u = index.get(e.low());
            int v = index.get(e.high());
            DefaultWeightedEdge ge = g.addEdge(u, v);
            g.setEdgeWeight(ge, e.weight());
            original.put(ge, e);
            g.setEdgeWeight(g.addEdge(u + n, v + n), e.weight());
        }
        for (int v = 0; v < n; v++) g.setEdgeWeight(g.addEdge(v, v + n), 0);

        MatchingAlgorithm.Matching<Integer, DefaultWeightedEdge> m =
                new KolmogorovWeightedPerfectMatching<>(g, ObjectiveSense.MAXIMIZE).getMatching();
        List<Edge> out = new ArrayList<>();
        for (DefaultWeightedEdge ge : m.getEdges()) {
            Edge e = original.get(ge);
            if (e != null) out.add(e);
        }
        return out;
    }
}
