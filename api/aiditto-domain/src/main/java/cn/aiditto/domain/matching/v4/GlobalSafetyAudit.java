package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent of the solver. A math fixture uses {@link #auditMath} only.
 * {@link #auditProfile} never treats {@code mathAdmitted} as a pass.
 */
public final class GlobalSafetyAudit {
    private GlobalSafetyAudit() {}

    public static Map<String, Integer> audit(GlobalMatchingGraph graph, List<PairKey> pairs) {
        return graph.mathFixture() ? auditMath(graph, pairs) : auditProfile(graph, pairs);
    }

    /** Structural checks for a solver-test graph. This is not a business clearance. */
    public static Map<String, Integer> auditMath(GlobalMatchingGraph graph, List<PairKey> pairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<PairKey, PreparedEdge> known = graph.solverByKey();
        Set<Long> seen = new java.util.HashSet<>();
        for (PairKey pair : pairs) {
            if (pair.low() == pair.high()) add(counts, "SELF");
            if (!seen.add(pair.low()) || !seen.add(pair.high())) add(counts, "DUPLICATE_USER");
            PreparedEdge edge = known.get(pair);
            if (edge == null || !edge.solverEligible() || edge.fitCenti() <= 0) add(counts, "NOT_IN_SOLVER_GRAPH");
        }
        return Map.copyOf(counts);
    }

    /**
     * Business clearance for a synthetic profile graph. Both people, their authorization,
     * pool snapshot, versions, generations, hard constraints, blocks, and eligibility are read again.
     */
    public static Map<String, Integer> auditProfile(GlobalMatchingGraph graph, List<PairKey> pairs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<PairKey, PreparedEdge> known = graph.solverByKey();
        Set<Long> seen = new java.util.HashSet<>();
        Map<Long, CompatibilitySubject> users = new LinkedHashMap<>();
        for (CompatibilitySubject user : graph.input().eligibleUsers()) users.put(user.userId(), user);
        for (PairKey pair : pairs) {
            if (pair.low() == pair.high()) add(counts, "SELF");
            if (!seen.add(pair.low()) || !seen.add(pair.high())) add(counts, "DUPLICATE_USER");
            PreparedEdge edge = known.get(pair);
            if (edge == null || !edge.solverEligible()) {
                add(counts, "NOT_IN_SOLVER_GRAPH");
                continue;
            }
            if (edge.mathAdmitted()) add(counts, "MATH_EDGE");
            checkPerson(graph, users.get(pair.low()), pair.low(), edge.versionLow(), edge.generationLow(), counts);
            checkPerson(graph, users.get(pair.high()), pair.high(), edge.versionHigh(), edge.generationHigh(), counts);
            if (graph.input().blockedPairs().contains(pair)) add(counts, "BLOCKED");
            if (graph.input().forbiddenPairs().contains(pair)) add(counts, "FORBIDDEN");
            if (graph.input().historicalRestrictions().contains(pair)) add(counts, "HISTORICAL");
            if (edge.decision() == null || !edge.decision().recommendationEligible()) add(counts, "NO_RECOMMENDATION");
            if (edge.decision() != null && edge.decision().pair().hardFilterResult().stream().anyMatch(code -> code.startsWith("HARD_"))) {
                add(counts, "HARD_CONSTRAINT");
            }
        }
        return Map.copyOf(counts);
    }

    public static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void checkPerson(GlobalMatchingGraph graph, CompatibilitySubject user, long id, long edgeVersion,
                                    long edgeGeneration, Map<String, Integer> counts) {
        if (user == null || user.candidate() == null || user.candidate().id() != id || user.userId() != id) {
            add(counts, "IDENTITY");
            return;
        }
        if (!"GRANTED".equals(graph.input().consentSnapshot().get(id))) add(counts, "NOT_AUTHORIZED");
        if (user.withdrawn() || !user.current()) add(counts, "NOT_IN_POOL");
        Long snapshotVersion = graph.input().profileSnapshotVersions().get(id);
        Long snapshotGeneration = graph.input().lifecycleGenerations().get(id);
        if (snapshotVersion == null || edgeVersion != snapshotVersion || edgeVersion != user.profileVersion()) add(counts, "VERSION");
        if (snapshotGeneration == null || edgeGeneration != snapshotGeneration || edgeGeneration != user.generation()) {
            add(counts, "GENERATION");
        }
    }

    private static void add(Map<String, Integer> counts, String code) {
        counts.merge(code, 1, Integer::sum);
    }
}
