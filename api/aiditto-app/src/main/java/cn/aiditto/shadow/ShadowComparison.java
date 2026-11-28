package cn.aiditto.shadow;

import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.MatchingResult;
import cn.aiditto.domain.matching.MatchingSolver;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.Weights;
import cn.aiditto.domain.matching.v4.GlobalCandidateGraphBuilder;
import cn.aiditto.domain.matching.v4.GlobalMatchingGraph;
import cn.aiditto.domain.matching.v4.GlobalMatchingInput;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.matching.v4.V4GlobalMatchingEngine;
import cn.aiditto.match.People;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only V3 solver against the same city rows. This does not call {@code WeekRunner}
 * and does not pass AI or learned scores.
 */
final class ShadowComparison {
    private ShadowComparison() {}

    static String write(ProductMatchingPoolReader.PoolRead pool, List<CompatibilitySubject> v4Subjects,
                        GlobalMatchingResult v4, Set<PairKey> past, Set<PairKey> forbidden,
                        MatchingConsentService consents, LocalDate period, long seed, long deadlineNanos,
                        Map<String, Integer> exclusionCounts, String coverageJson) {
        Set<Long> v3Ids = new LinkedHashSet<>();
        List<Candidate> v3Candidates = new ArrayList<>();
        for (People.Member member : pool.city()) {
            if (member.candidate() == null || member.excluded() != null) continue;
            if (!consents.allowsPool(member.userId())) continue;
            v3Ids.add(member.userId());
            v3Candidates.add(member.candidate());
        }
        Set<Long> v4Ids = new LinkedHashSet<>();
        Map<Long, CompatibilitySubject> byId = new LinkedHashMap<>();
        for (CompatibilitySubject subject : v4Subjects) {
            v4Ids.add(subject.userId());
            byId.put(subject.userId(), subject);
        }
        MatchingResult v3 = solveV3(period, v3Candidates, past, forbidden);
        Set<Long> common = new LinkedHashSet<>(v3Ids);
        common.retainAll(v4Ids);
        List<CompatibilitySubject> commonSubjects = new ArrayList<>();
        List<Candidate> commonCandidates = new ArrayList<>();
        for (People.Member member : pool.city()) {
            if (!common.contains(member.userId()) || member.candidate() == null) continue;
            commonCandidates.add(member.candidate());
            CompatibilitySubject subject = byId.get(member.userId());
            if (subject != null) commonSubjects.add(subject);
        }
        MatchingResult v3Common = commonCandidates.size() >= 2 ? solveV3(period, commonCandidates, past, forbidden) : null;
        GlobalMatchingResult v4Common = null;
        if (commonSubjects.size() >= 2 && v4 != null && v4.optimalForFullEligibleGraph()) {
            v4Common = solveV4(v4.runId(), period, commonSubjects, past, forbidden, seed, deadlineNanos);
        }
        Set<String> frameABoth = overlap(keys(v3), keys(v4));
        Set<String> frameBBoth = overlap(keys(v3Common), keys(v4Common));
        StringBuilder out = new StringBuilder(1024);
        out.append("{\"note\":\"scores are different instruments and are not a quality ranking\"");
        out.append(",\"frameA\":{");
        out.append("\"rawCityUsers\":").append(pool.city().size());
        out.append(",\"v3Pool\":").append(v3Ids.size());
        out.append(",\"v4Pool\":").append(v4Ids.size());
        out.append(",\"v3Pairs\":").append(v3 == null ? 0 : v3.pairs().size());
        out.append(",\"v4Pairs\":").append(v4 == null ? 0 : v4.pairs().size());
        out.append(",\"v3Unmatched\":").append(v3 == null ? 0 : v3.unmatched().size());
        out.append(",\"v4Unmatched\":").append(v4 == null ? 0 : v4.unmatched().size());
        out.append(",\"v3Edges\":").append(v3 == null ? 0 : v3.stats().edges());
        out.append(",\"v3Solver\":").append(v3 == null ? "\"SKIPPED\"" : ShadowText.quote(v3.stats().solver().name()));
        out.append(",\"both\":").append(list(frameABoth));
        out.append(",\"onlyV3\":").append(list(subtract(keys(v3), keys(v4))));
        out.append(",\"onlyV4\":").append(list(subtract(keys(v4), keys(v3))));
        out.append(",\"exclusions\":").append(object(exclusionCounts));
        out.append("},\"frameB\":{");
        out.append("\"commonUsers\":").append(common.size());
        out.append(",\"v3Pairs\":").append(v3Common == null ? 0 : v3Common.pairs().size());
        out.append(",\"v4Pairs\":").append(v4Common == null ? 0 : v4Common.pairs().size());
        out.append(",\"both\":").append(list(frameBBoth));
        out.append(",\"onlyV3\":").append(list(subtract(keys(v3Common), keys(v4Common))));
        out.append(",\"onlyV4\":").append(list(subtract(keys(v4Common), keys(v3Common))));
        out.append("},\"coverage\":").append(coverageJson).append("}");
        return out.toString();
    }

    private static MatchingResult solveV3(LocalDate period, List<Candidate> candidates, Set<PairKey> past, Set<PairKey> forbidden) {
        if (candidates.size() < 2) return null;
        MatchingInput input = new MatchingInput(period, candidates, past, forbidden, Weights.W1, period.toEpochDay(), false);
        return new MatchingSolver().solve(input);
    }

    private static GlobalMatchingResult solveV4(String runId, LocalDate period, List<CompatibilitySubject> subjects,
                                                 Set<PairKey> past, Set<PairKey> forbidden, long seed, long deadlineNanos) {
        Map<Long, Long> versions = new LinkedHashMap<>();
        Map<Long, Long> generations = new LinkedHashMap<>();
        Map<Long, String> consent = new LinkedHashMap<>();
        for (CompatibilitySubject subject : subjects) {
            versions.put(subject.userId(), subject.profileVersion());
            generations.put(subject.userId(), subject.generation());
            consent.put(subject.userId(), "GRANTED");
        }
        GlobalMatchingInput input = new GlobalMatchingInput(runId + ".b", period, "intersection", subjects,
                versions, generations, consent, Set.of(), forbidden, past, "recall-rank-1",
                CompatibilityPolicies.VERSION, Objective.SELECTED_VERSION, Map.of(), seed);
        GlobalMatchingGraph graph = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL,
                deadlineNanos, cn.aiditto.domain.matching.v4.GlobalBudgets.MAX_SOLVER_EDGES);
        if (!graph.fullEligibleGraph()) return null;
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, Objective.SELECTED, deadlineNanos);
        return result.optimalForFullEligibleGraph() ? result : null;
    }

    private static Set<String> keys(MatchingResult result) {
        Set<String> keys = new TreeSet<>();
        if (result == null) return keys;
        for (MatchingResult.MatchedPair pair : result.pairs()) keys.add(pair.low() + "-" + pair.high());
        return keys;
    }

    private static Set<String> keys(GlobalMatchingResult result) {
        Set<String> keys = new TreeSet<>();
        if (result == null) return keys;
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) keys.add(pair.lowId() + "-" + pair.highId());
        return keys;
    }

    private static Set<String> overlap(Set<String> left, Set<String> right) {
        Set<String> out = new TreeSet<>(left);
        out.retainAll(right);
        return out;
    }

    private static Set<String> subtract(Set<String> left, Set<String> right) {
        Set<String> out = new TreeSet<>(left);
        out.removeAll(right);
        return out;
    }

    private static String list(Set<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) out.append(',');
            first = false;
            out.append(ShadowText.quote(value));
        }
        return out.append(']').toString();
    }

    private static String object(Map<String, Integer> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(ShadowText.quote(entry.getKey())).append(':').append(entry.getValue());
        }
        return out.append('}').toString();
    }
}
