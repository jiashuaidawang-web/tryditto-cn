package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Independent of the solver. It reads the graph and the chosen keys again. */
public final class GlobalSafetyAudit {
    private GlobalSafetyAudit() {}

    public static Map<String, Integer> audit(GlobalMatchingGraph graph, List<PairKey> pairs) {
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
            if (graph.input().blockedPairs().contains(pair) || graph.input().forbiddenPairs().contains(pair)
                    || graph.input().historicalRestrictions().contains(pair)) {
                add(counts, "RESTRICTION");
            }
            if (edge.mathAdmitted()) continue;
            if (edge.decision() == null || !edge.decision().recommendationEligible()) add(counts, "NO_RECOMMENDATION");
            if (edge.decision() != null && edge.decision().pair().hardFilterResult().stream().anyMatch(code -> code.startsWith("HARD_"))) {
                add(counts, "HARD_CONSTRAINT");
            }
            if (badPerson(graph, users.get(pair.low())) || badPerson(graph, users.get(pair.high()))) add(counts, "NOT_AUTHORIZED");
            if (edge.versionLow() != graph.input().profileSnapshotVersions().get(pair.low())
                    || edge.versionHigh() != graph.input().profileSnapshotVersions().get(pair.high())) {
                add(counts, "VERSION");
            }
        }
        return Map.copyOf(counts);
    }

    public static int total(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static boolean badPerson(GlobalMatchingGraph graph, CompatibilitySubject user) {
        if (user == null) return true;
        if (user.withdrawn() || !user.current()) return true;
        return !"GRANTED".equals(graph.input().consentSnapshot().get(user.userId()));
    }

    private static void add(Map<String, Integer> counts, String code) {
        counts.merge(code, 1, Integer::sum);
    }
}
