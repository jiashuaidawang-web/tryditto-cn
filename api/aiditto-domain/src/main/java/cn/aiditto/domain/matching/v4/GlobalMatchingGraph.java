package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.matching.PairKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GlobalMatchingGraph(
        GlobalMatchingInput input,
        String strategy,
        String scope,
        boolean fullPopulation,
        List<PreparedEdge> diagnostic,
        List<PreparedEdge> solver,
        GraphStats stats,
        String status,
        String statusDetail) {

    public static final String COMPLETE = "COMPLETE";
    public static final String CAP_REACHED = "CAP_REACHED";

    public GlobalMatchingGraph {
        diagnostic = List.copyOf(diagnostic);
        solver = List.copyOf(solver);
        if (input == null || strategy == null || scope == null || stats == null || status == null) {
            throw new IllegalArgumentException("graph");
        }
    }

    public boolean complete() {
        return COMPLETE.equals(status);
    }

    public Map<PairKey, PreparedEdge> solverByKey() {
        Map<PairKey, PreparedEdge> out = new LinkedHashMap<>();
        for (PreparedEdge edge : solver) {
            if (out.put(edge.key(), edge) != null) throw new IllegalArgumentException("duplicate solver edge " + edge.key());
        }
        return out;
    }

    public static GlobalMatchingGraph math(String name, List<Long> ids, List<PreparedEdge> edges) {
        return math(name, ids, edges, Map.of());
    }

    public static GlobalMatchingGraph math(String name, List<Long> ids, List<PreparedEdge> edges, Map<Long, Integer> unmatchedWeeks) {
        List<cn.aiditto.domain.compatibility.v4.CompatibilitySubject> users = new ArrayList<>();
        for (Long id : ids) users.add(GlobalMatchPeople.placeholder(id));
        GlobalMatchingInput input = GlobalMatchingInput.of("math-" + name, "math", users, java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), unmatchedWeeks, 1L);
        List<PreparedEdge> solver = new ArrayList<>();
        List<PreparedEdge> diagnostic = new ArrayList<>();
        for (PreparedEdge edge : edges) {
            diagnostic.add(edge);
            if (edge.solverEligible()) solver.add(edge);
        }
        return new GlobalMatchingGraph(input, "MATH", name, true, diagnostic, solver,
                new GraphStats(ids.size(), edges.size(), diagnostic.size(), solver.size(), 0, 0, solver.size(), 0, 0, 0, 0,
                        0, 0, 0, 0), COMPLETE, "");
    }
}
