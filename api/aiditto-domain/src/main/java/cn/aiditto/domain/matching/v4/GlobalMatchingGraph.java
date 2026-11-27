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
        Coverage coverage,
        List<PreparedEdge> diagnostic,
        List<PreparedEdge> solver,
        GraphStats stats,
        String status,
        String statusDetail,
        Map<Long, List<String>> userDiagnostics) {

    public enum Coverage { FULL_ELIGIBLE, TRUNCATED, ABORTED, MATH }

    public static final String COMPLETE = "COMPLETE";
    public static final String CAP_REACHED = "CAP_REACHED";

    public GlobalMatchingGraph {
        diagnostic = List.copyOf(diagnostic);
        solver = List.copyOf(solver);
        if (input == null || strategy == null || scope == null || coverage == null || stats == null || status == null) {
            throw new IllegalArgumentException("graph");
        }
        Map<Long, List<String>> notes = new LinkedHashMap<>();
        if (userDiagnostics != null) {
            userDiagnostics.forEach((id, codes) -> notes.put(id, List.copyOf(codes)));
        }
        userDiagnostics = Map.copyOf(notes);
    }

    /** Every requested user stayed in the finished run. An aborted run is not a finished user pool. */
    public boolean fullUserPool() {
        return coverage == Coverage.FULL_ELIGIBLE || coverage == Coverage.TRUNCATED;
    }

    /** Every recommendation-eligible edge among those users is in the solver graph. */
    public boolean fullEligibleGraph() {
        return coverage == Coverage.FULL_ELIGIBLE;
    }

    public boolean truncatedGraph() {
        return coverage == Coverage.TRUNCATED;
    }

    public boolean abortedGraph() {
        return coverage == Coverage.ABORTED;
    }

    public boolean mathFixture() {
        return coverage == Coverage.MATH;
    }

    /** Kept for callers that mean the full eligible graph. A full user pool is not enough. */
    public boolean fullPopulation() {
        return fullEligibleGraph();
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
        return new GlobalMatchingGraph(input, "MATH", name, Coverage.MATH, diagnostic, solver,
                new GraphStats(ids.size(), edges.size(), diagnostic.size(), solver.size(), 0, 0, solver.size(), 0, 0, 0, 0,
                        0, 0, 0, 0), COMPLETE, "math fixture", Map.of());
    }
}
