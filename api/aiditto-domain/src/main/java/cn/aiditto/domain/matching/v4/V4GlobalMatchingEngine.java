package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline global matching. It does not call {@code MatchingSolver} or the production week runner.
 * Blossom and greedy are reached only through {@link V4MatchingAdapter}.
 */
public final class V4GlobalMatchingEngine {
    private V4GlobalMatchingEngine() {}

    public static final String SAFETY_REJECTED = "SAFETY_REJECTED";
    public static final String MATH_ONLY = "MATH_ONLY";

    public static GlobalMatchingResult solve(GlobalMatchingGraph graph, Objective objective) {
        return solve(graph, objective, System.nanoTime() + GlobalBudgets.WALL_MS * 1_000_000L);
    }

    /**
     * {@code deadlineNanos} is the shared build-and-solve budget.
     * Blossom cannot be cancelled by interrupting the thread. The solver starts only when the
     * graph is complete and the budget is still open, and only inside the frozen edge cap.
     * If it returns after the deadline, the pairs are discarded.
     */
    public static GlobalMatchingResult solve(GlobalMatchingGraph graph, Objective objective, long deadlineNanos) {
        long wallStart = System.nanoTime();
        if (!graph.complete() || graph.abortedGraph()) return aborted(graph, objective, wallStart, "graph " + graph.statusDetail());
        if (System.nanoTime() > deadlineNanos) return aborted(graph, objective, wallStart, "solve not started: wall budget exhausted");
        if (graph.solver().size() > GlobalBudgets.MAX_SOLVER_EDGES) {
            return aborted(graph, objective, wallStart, "solver edge cap " + GlobalBudgets.MAX_SOLVER_EDGES);
        }
        long solveStart = System.nanoTime();
        List<PreparedEdge> pool = new ArrayList<>();
        for (PreparedEdge edge : graph.solver()) {
            if (!edge.solverEligible() || edge.fitCenti() <= 0) continue;
            if (objective == Objective.G3_QUALITY_TIER && !ExhaustiveMatcher.admitsQuality(edge)) continue;
            pool.add(edge);
        }
        List<PairKey> keys = V4MatchingAdapter.match(graph.input(), pool, objective, objective == Objective.G0_GREEDY);
        long solveMs = (System.nanoTime() - solveStart) / 1_000_000L;
        if (System.nanoTime() > deadlineNanos) {
            return aborted(graph, objective, wallStart, "solver returned after the wall budget; pairs discarded");
        }
        long planStart = System.nanoTime();
        Map<PairKey, PreparedEdge> byKey = new LinkedHashMap<>();
        for (PreparedEdge edge : pool) byKey.put(edge.key(), edge);
        List<GlobalMatchingResult.MatchedPair> pairs = new ArrayList<>();
        long fit = 0;
        long weight = 0;
        Set<Long> matched = new LinkedHashSet<>();
        for (PairKey key : keys) {
            PreparedEdge edge = byKey.get(key);
            if (edge == null) return aborted(graph, objective, wallStart, "solver key missing " + key);
            int fairness = objective == Objective.G4_FAIRNESS
                    ? OptimizationWeight.fairnessBonus(graph.input().unmatchedWeeks(edge.lowId()), graph.input().unmatchedWeeks(edge.highId()))
                    : 0;
            long edgeWeight = OptimizationWeight.edgeWeight(objective, graph.input().eligibleUsers().size(), edge.fitCenti(), fairness);
            pairs.add(new GlobalMatchingResult.MatchedPair(edge.lowId(), edge.highId(),
                    edge.fitScore() == null ? edge.fitCenti() / 100.0 : edge.fitScore(), edge.fitCenti(),
                    edge.scoreLowToHigh(), edge.scoreHighToLow(), edge.coverageLowToHigh(), edge.coverageHighToLow(),
                    edge.minDirection(), edgeWeight, OptimizationWeight.components(objective, edge.fitCenti(), fairness, edgeWeight),
                    !edge.mathAdmitted() && edge.decision() != null && edge.decision().recommendationEligible()));
            fit += edge.fitCenti();
            weight += edgeWeight;
            matched.add(edge.lowId());
            matched.add(edge.highId());
        }
        pairs.sort(Comparator.comparingLong(GlobalMatchingResult.MatchedPair::lowId).thenComparingLong(GlobalMatchingResult.MatchedPair::highId));
        List<GlobalMatchingResult.UnmatchedPerson> unmatched = explain(graph, pool, matched);
        long planMs = (System.nanoTime() - planStart) / 1_000_000L;
        long auditStart = System.nanoTime();
        Map<String, Integer> safety = graph.mathFixture()
                ? GlobalSafetyAudit.auditMath(graph, keys)
                : GlobalSafetyAudit.auditProfile(graph, keys);
        long auditMs = (System.nanoTime() - auditStart) / 1_000_000L;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (GlobalMatchingResult.UnmatchedPerson person : unmatched) reasons.merge(person.finalReason(), 1, Integer::sum);
        long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        int violations = GlobalSafetyAudit.total(safety);
        if (violations > 0 || graph.mathFixture()) {
            boolean keepMathPairs = violations == 0 && graph.mathFixture();
            String detail = graph.statusDetail() + " safety=" + safety;
            return new GlobalMatchingResult(graph.input().matchingRunId(), objective, graph.scope(),
                    keepMathPairs, false, false,
                    keepMathPairs ? pairs : List.of(), unmatched, keepMathPairs ? fit : 0, keepMathPairs ? weight : 0,
                    violations, reasons, safety,
                    violations > 0 ? SAFETY_REJECTED : MATH_ONLY, detail, solveMs, planMs, auditMs,
                    (System.nanoTime() - wallStart) / 1_000_000L, heap);
        }
        String status = graph.fullEligibleGraph() ? GlobalMatchingGraph.COMPLETE : "TRUNCATED";
        return new GlobalMatchingResult(graph.input().matchingRunId(), objective, graph.scope(),
                true, graph.fullEligibleGraph(), true,
                pairs, unmatched, fit, weight, 0, reasons, safety,
                status, graph.statusDetail(), solveMs, planMs, auditMs,
                (System.nanoTime() - wallStart) / 1_000_000L, heap);
    }

    private static List<GlobalMatchingResult.UnmatchedPerson> explain(GlobalMatchingGraph graph, List<PreparedEdge> pool, Set<Long> matched) {
        Map<Long, List<PreparedEdge>> usable = new LinkedHashMap<>();
        Map<Long, List<PreparedEdge>> admitted = new LinkedHashMap<>();
        for (PreparedEdge edge : pool) {
            usable.computeIfAbsent(edge.lowId(), ignored -> new ArrayList<>()).add(edge);
            usable.computeIfAbsent(edge.highId(), ignored -> new ArrayList<>()).add(edge);
        }
        for (PreparedEdge edge : graph.solver()) {
            admitted.computeIfAbsent(edge.lowId(), ignored -> new ArrayList<>()).add(edge);
            admitted.computeIfAbsent(edge.highId(), ignored -> new ArrayList<>()).add(edge);
        }
        Set<PairKey> solverKeys = new LinkedHashSet<>();
        for (PreparedEdge edge : graph.solver()) solverKeys.add(edge.key());
        List<GlobalMatchingResult.UnmatchedPerson> out = new ArrayList<>();
        for (CompatibilitySubject user : graph.input().eligibleUsers()) {
            if (matched.contains(user.userId())) continue;
            List<String> diagnostics = new ArrayList<>();
            diagnostics.addAll(graph.userDiagnostics().getOrDefault(user.userId(), List.of()));
            if (user.withdrawn()) diagnostics.add("PROFILE_WITHDRAWN");
            if (!"GRANTED".equals(graph.input().consentSnapshot().get(user.userId()))) diagnostics.add("CONSENT_REVOKED");
            if (!user.current()) diagnostics.add("PROFILE_STALE");
            if (!graph.input().profileSnapshotVersions().get(user.userId()).equals(user.profileVersion())) diagnostics.add("VERSION_MISMATCH");
            if (!graph.input().lifecycleGenerations().get(user.userId()).equals(user.generation())) diagnostics.add("GENERATION_MISMATCH");
            boolean droppedByScope = false;
            boolean ineligibleScore = false;
            boolean insufficient = false;
            for (PreparedEdge edge : graph.diagnostic()) {
                if (edge.lowId() != user.userId() && edge.highId() != user.userId()) continue;
                if (edge.solverEligible() && !solverKeys.contains(edge.key())) {
                    droppedByScope = true;
                    diagnostics.add("RECALL_TRUNCATED");
                }
                if ("NOT_ELIGIBLE".equals(edge.exclusion()) || "SCORE_OUT_OF_RANGE".equals(edge.exclusion())) {
                    ineligibleScore = true;
                    diagnostics.add("NO_RECOMMENDATION_ELIGIBLE_EDGE");
                }
                if (edge.diagnostics().stream().anyMatch(code -> code.contains("COVERAGE") || code.contains("UNEVALUABLE")
                        || code.contains("SCORE_UNDEFINED") || "INSUFFICIENT_EVIDENCE".equals(code))) {
                    insufficient = true;
                    diagnostics.add("INSUFFICIENT_EVIDENCE");
                }
            }
            List<PreparedEdge> mine = usable.getOrDefault(user.userId(), List.of());
            boolean neighborMatched = false;
            for (PreparedEdge edge : mine) {
                long other = edge.lowId() == user.userId() ? edge.highId() : edge.lowId();
                if (matched.contains(other)) neighborMatched = true;
            }
            if (!mine.isEmpty() && neighborMatched) diagnostics.add("GLOBAL_COMPETITION");
            if (!mine.isEmpty() && !neighborMatched) diagnostics.add("OPTIMIZATION_NOT_SELECTED");
            if (mine.isEmpty() && !admitted.getOrDefault(user.userId(), List.of()).isEmpty()) diagnostics.add("OPTIMIZATION_NOT_SELECTED");
            String finals = finalReason(diagnostics, droppedByScope, ineligibleScore, insufficient, mine.isEmpty());
            out.add(new GlobalMatchingResult.UnmatchedPerson(user.userId(), finals, distinct(diagnostics)));
        }
        out.sort(Comparator.comparingLong(GlobalMatchingResult.UnmatchedPerson::userId));
        return out;
    }

    private static String finalReason(List<String> diagnostics, boolean droppedByScope, boolean ineligible, boolean insufficient, boolean noUsable) {
        if (diagnostics.contains("PROFILE_WITHDRAWN")) return "PROFILE_WITHDRAWN";
        if (diagnostics.contains("CONSENT_REVOKED")) return "CONSENT_REVOKED";
        if (diagnostics.contains("PROFILE_STALE")) return "PROFILE_STALE";
        if (diagnostics.contains("VERSION_MISMATCH")) return "VERSION_MISMATCH";
        if (diagnostics.contains("GENERATION_MISMATCH")) return "GENERATION_MISMATCH";
        if (diagnostics.contains("GLOBAL_COMPETITION")) return "GLOBAL_COMPETITION";
        if (diagnostics.contains("OPTIMIZATION_NOT_SELECTED")) return "OPTIMIZATION_NOT_SELECTED";
        if (droppedByScope && noUsable) return "RECALL_TRUNCATED";
        if (ineligible) return "NO_RECOMMENDATION_ELIGIBLE_EDGE";
        if (insufficient) return "INSUFFICIENT_EVIDENCE";
        return "NO_LEGAL_CANDIDATE";
    }

    private static List<String> distinct(List<String> codes) {
        return List.copyOf(new LinkedHashSet<>(codes));
    }

    private static GlobalMatchingResult aborted(GlobalMatchingGraph graph, Objective objective, long wallStart, String detail) {
        List<GlobalMatchingResult.UnmatchedPerson> unmatched = new ArrayList<>();
        for (CompatibilitySubject user : graph.input().eligibleUsers()) {
            unmatched.add(new GlobalMatchingResult.UnmatchedPerson(user.userId(), "RUN_ABORTED", List.of("RUN_ABORTED")));
        }
        return new GlobalMatchingResult(graph.input().matchingRunId(), objective, graph.scope(), false, false, false,
                List.of(), unmatched, 0, 0, 0, Map.of("RUN_ABORTED", unmatched.size()), Map.of(),
                GlobalMatchingGraph.CAP_REACHED, detail, 0, 0, 0,
                (System.nanoTime() - wallStart) / 1_000_000L, 0);
    }
}
