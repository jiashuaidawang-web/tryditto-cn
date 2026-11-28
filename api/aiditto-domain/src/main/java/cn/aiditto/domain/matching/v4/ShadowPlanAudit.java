package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the frozen subjects, the live recheck, and {@link CompatibilityContext#blockReasons} again.
 * It does not treat a graph or solver flag as clearance.
 */
public final class ShadowPlanAudit {
    private ShadowPlanAudit() {}

    public record Live(long userId, boolean active, String consent, long generation, long profileVersion, String inputHash) {}

    public static Map<String, Integer> audit(GlobalMatchingGraph graph, List<GlobalMatchingResult.MatchedPair> pairs,
                                              Map<Long, Live> live, Map<Long, String> frozenHash) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (pairs == null || pairs.isEmpty()) return Map.of();
        if (graph == null || graph.mathFixture() || !graph.fullEligibleGraph()) {
            add(counts, "GRAPH");
            return Map.copyOf(counts);
        }
        Map<Long, CompatibilitySubject> users = new LinkedHashMap<>();
        for (CompatibilitySubject user : graph.input().eligibleUsers()) users.put(user.userId(), user);
        CompatibilityContext context = CompatibilityContext.experiment(graph.input().matchingPeriod(), Instant.EPOCH,
                graph.input().eligibleUsers(), graph.input().historicalRestrictions(), graph.input().forbiddenPairs());
        Map<PairKey, PreparedEdge> edges = graph.solverByKey();
        Set<Long> seen = new LinkedHashSet<>();
        for (GlobalMatchingResult.MatchedPair pair : pairs) {
            if (pair.lowId() >= pair.highId()) add(counts, "ORDER");
            if (!seen.add(pair.lowId()) || !seen.add(pair.highId())) add(counts, "DUPLICATE_USER");
            PairKey key = pair.key();
            if (graph.input().blockedPairs().contains(key)) add(counts, "BLOCKED");
            if (graph.input().forbiddenPairs().contains(key)) add(counts, "FORBIDDEN");
            if (graph.input().historicalRestrictions().contains(key)) add(counts, "HISTORICAL");
            PreparedEdge edge = edges.get(key);
            if (edge == null || !edge.solverEligible() || edge.mathAdmitted() || edge.decision() == null
                    || !edge.decision().recommendationEligible()) {
                add(counts, "NOT_RECOMMENDED");
            }
            check(graph, users.get(pair.lowId()), pair.lowId(), live, frozenHash, counts);
            check(graph, users.get(pair.highId()), pair.highId(), live, frozenHash, counts);
            CompatibilitySubject left = users.get(pair.lowId());
            CompatibilitySubject right = users.get(pair.highId());
            if (left != null && right != null) {
                for (String code : context.blockReasons(left, right)) {
                    if (code.startsWith("HARD_") || code.equals("NOT_SYNTHETIC")) add(counts, code);
                }
            }
        }
        return Map.copyOf(counts);
    }

    public static int total(Map<String, Integer> counts) {
        int sum = 0;
        for (int value : counts.values()) sum += value;
        return sum;
    }

    private static void check(GlobalMatchingGraph graph, CompatibilitySubject user, long id, Map<Long, Live> live,
                              Map<Long, String> frozenHash, Map<String, Integer> counts) {
        if (user == null || user.userId() != id || user.candidate() == null || user.candidate().id() != id
                || !user.current() || user.withdrawn()) {
            add(counts, "IDENTITY");
            return;
        }
        if (!"GRANTED".equals(graph.input().consentSnapshot().get(id))) add(counts, "NOT_AUTHORIZED");
        Live row = live.get(id);
        if (row == null || row.userId() != id || !row.active() || !"GRANTED".equals(row.consent())) {
            add(counts, "LIVE_REJECTED");
            return;
        }
        if (row.generation() != user.generation() || row.profileVersion() != user.profileVersion()) add(counts, "LIVE_STALE");
        String hash = frozenHash.get(id);
        if (hash == null || !hash.equals(row.inputHash())) add(counts, "LIVE_STALE");
    }

    private static void add(Map<String, Integer> counts, String code) {
        counts.merge(code, 1, Integer::sum);
    }
}
