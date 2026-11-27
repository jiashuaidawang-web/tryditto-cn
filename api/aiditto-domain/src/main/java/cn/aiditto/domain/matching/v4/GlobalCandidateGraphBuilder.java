package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the diagnostic graph and the solver graph. Only a legal, recommendation-eligible,
 * in-range edge enters the solver graph. Truncation and a crossed cap never put a partial
 * edge list forward as the full optimum.
 */
public final class GlobalCandidateGraphBuilder {
    public enum Strategy { E_FULL_POOL, F_SHORTLIST }

    private GlobalCandidateGraphBuilder() {}

    public static GlobalMatchingGraph build(GlobalMatchingInput input, Strategy strategy) {
        return build(input, strategy, System.nanoTime() + GlobalBudgets.WALL_MS * 1_000_000L, GlobalBudgets.MAX_SOLVER_EDGES);
    }

    public static GlobalMatchingGraph build(GlobalMatchingInput input, Strategy strategy, long deadlineNanos, int maxSolverEdges) {
        long started = System.nanoTime();
        List<CompatibilitySubject> users = new ArrayList<>(input.eligibleUsers());
        users.sort(Comparator.comparingLong(CompatibilitySubject::userId));
        if (users.size() > GlobalBudgets.MAX_USERS || !OptimizationWeight.encodingFits(users.size())) {
            return capped(input, strategy, users.size(), "user or encoding cap", started);
        }
        long profileMs = (System.nanoTime() - started) / 1_000_000L;
        CompatibilityContext context = CompatibilityContext.experiment(input.matchingPeriod(), BidirectionalCompatibilityEngine.NOW,
                users, input.historicalRestrictions(), input.forbiddenPairs());
        BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
        Counters counters = new Counters();
        List<PreparedEdge> diagnostic = new ArrayList<>();
        List<PreparedEdge> solver = new ArrayList<>();
        boolean retainAll = users.size() <= 40;
        long filterStart = System.nanoTime();
        if (strategy == Strategy.F_SHORTLIST) {
            scoreShortlist(input, users, context, engine, deadlineNanos, maxSolverEdges, counters, diagnostic, solver, retainAll);
        } else {
            scoreAll(input, users, context, engine, deadlineNanos, maxSolverEdges, counters, diagnostic, solver, retainAll);
        }
        long filterAndScore = (System.nanoTime() - filterStart) / 1_000_000L;
        long buildMs = (System.nanoTime() - started) / 1_000_000L;
        String detail = counters.capped
                ? counters.detail + " loopMs=" + filterAndScore + " diagnosticOmitted=" + counters.diagnosticOmitted
                : "buildMs=" + buildMs + " diagnosticOmitted=" + counters.diagnosticOmitted + " userNotes=" + counters.notes.size();
        GlobalMatchingGraph.Coverage coverage = counters.capped
                ? GlobalMatchingGraph.Coverage.ABORTED
                : strategy == Strategy.F_SHORTLIST ? GlobalMatchingGraph.Coverage.TRUNCATED : GlobalMatchingGraph.Coverage.FULL_ELIGIBLE;
        return new GlobalMatchingGraph(input, strategy.name(), strategy.name(), coverage,
                counters.capped ? List.copyOf(diagnostic) : List.copyOf(diagnostic),
                counters.capped ? List.of() : List.copyOf(solver),
                counters.stats(users.size(), diagnostic.size(), counters.capped ? 0 : solver.size(), profileMs, counters.filterMs, counters.scoreMs(), buildMs),
                counters.capped ? GlobalMatchingGraph.CAP_REACHED : GlobalMatchingGraph.COMPLETE, detail, counters.notes);
    }

    public static GlobalMatchingGraph topK(GlobalMatchingGraph full, int k) {
        if (!full.complete()) return full;
        List<List<PreparedEdge>> byUser = groups(full);
        Set<PairKey> keep = new HashSet<>();
        for (List<PreparedEdge> edges : byUser) {
            edges.sort(Comparator.comparingInt(PreparedEdge::fitCenti).reversed()
                    .thenComparingLong(PreparedEdge::lowId).thenComparingLong(PreparedEdge::highId));
            for (int i = 0; i < Math.min(k, edges.size()); i++) keep.add(edges.get(i).key());
        }
        List<PreparedEdge> solver = new ArrayList<>();
        int dropped = 0;
        for (PreparedEdge edge : full.solver()) {
            if (keep.contains(edge.key())) solver.add(edge);
            else dropped++;
        }
        GraphStats stats = full.stats();
        GraphStats next = new GraphStats(stats.users(), stats.consideredPairs(), stats.diagnosticRetained(), solver.size(),
                stats.hardBlocked(), stats.notEligible(), stats.eligible(), dropped, stats.withdrawnPairs(),
                stats.consentPairs(), stats.historicalPairs(), stats.profileMs(), stats.filterMs(), stats.scoreMs(), stats.buildMs());
        GlobalMatchingGraph.Coverage coverage = full.mathFixture()
                ? GlobalMatchingGraph.Coverage.MATH
                : GlobalMatchingGraph.Coverage.TRUNCATED;
        return new GlobalMatchingGraph(full.input(), full.strategy(), "TOP_" + k, coverage,
                full.diagnostic(), solver, next, GlobalMatchingGraph.COMPLETE, "dropped=" + dropped, full.userDiagnostics());
    }

    private static void scoreAll(GlobalMatchingInput input, List<CompatibilitySubject> users, CompatibilityContext context,
                                 BidirectionalCompatibilityEngine engine, long deadline, int maxSolverEdges, Counters counters,
                                 List<PreparedEdge> diagnostic, List<PreparedEdge> solver, boolean retainAll) {
        for (int i = 0; i < users.size() && !counters.capped; i++) {
            for (int j = i + 1; j < users.size(); j++) {
                if ((counters.considered & 1023) == 0 && System.nanoTime() > deadline) {
                    counters.cap("stopped during scoring after " + counters.considered + " pairs");
                    return;
                }
                counters.considered++;
                accept(input, users.get(i), users.get(j), context, engine, true, counters, diagnostic, solver, retainAll, maxSolverEdges);
                if (counters.capped) return;
            }
        }
    }

    private static void scoreShortlist(GlobalMatchingInput input, List<CompatibilitySubject> users, CompatibilityContext context,
                                       BidirectionalCompatibilityEngine engine, long deadline, int maxSolverEdges, Counters counters,
                                       List<PreparedEdge> diagnostic, List<PreparedEdge> solver, boolean retainAll) {
        List<List<Long>> tops = new ArrayList<>();
        long filterStart = System.nanoTime();
        long seen = 0;
        for (int i = 0; i < users.size(); i++) {
            List<long[]> best = new ArrayList<>();
            for (int j = 0; j < users.size(); j++) {
                if ((++seen & 1023) == 0 && System.nanoTime() > deadline) {
                    counters.cap("stopped during shortlist construction");
                    return;
                }
                if (i == j) continue;
                if (precheck(input, users.get(i), users.get(j), counters) != null) continue;
                if (i < j) counters.passing++;
                int hits = GlobalMatchPeople.structuralHits(users.get(i), users.get(j));
                insert(best, users.get(j).userId(), hits);
            }
            List<Long> ids = new ArrayList<>();
            for (long[] row : best) ids.add(row[0]);
            tops.add(ids);
        }
        counters.filterMs = (System.nanoTime() - filterStart) / 1_000_000L;
        Set<PairKey> chosen = new HashSet<>();
        for (int i = 0; i < users.size(); i++) {
            for (Long other : tops.get(i)) chosen.add(PairKey.of(users.get(i).userId(), other));
        }
        counters.truncated = (int) Math.max(0, counters.passing - chosen.size());
        for (PairKey key : chosen) {
            if (System.nanoTime() > deadline) {
                counters.cap("stopped during shortlist scoring");
                return;
            }
            counters.considered++;
            accept(input, subject(users, key.low()), subject(users, key.high()), context, engine, true,
                    counters, diagnostic, solver, retainAll, maxSolverEdges);
            if (counters.capped) return;
        }
        if (retainAll) {
            for (int i = 0; i < users.size(); i++) {
                for (int j = i + 1; j < users.size(); j++) {
                    PairKey key = PairKey.of(users.get(i).userId(), users.get(j).userId());
                    if (chosen.contains(key)) continue;
                    String block = precheck(input, users.get(i), users.get(j), counters);
                    if (block == null) block = "RECALL_TRUNCATED";
                    remember(diagnostic, excluded(users.get(i), users.get(j), "F_SHORTLIST", block), true, counters);
                }
            }
        }
    }

    private static void insert(List<long[]> best, long id, int hits) {
        best.add(new long[]{id, hits});
        best.sort(Comparator.comparingLong((long[] row) -> row[1]).reversed().thenComparingLong(row -> row[0]));
        if (best.size() > GlobalBudgets.SHORTLIST) best.remove(best.size() - 1);
    }

    private static void accept(GlobalMatchingInput input, CompatibilitySubject low, CompatibilitySubject high,
                               CompatibilityContext context, BidirectionalCompatibilityEngine engine, boolean score,
                               Counters counters, List<PreparedEdge> diagnostic, List<PreparedEdge> solver,
                               boolean retainAll, int maxSolverEdges) {
        if (low.userId() > high.userId()) {
            CompatibilitySubject swap = low;
            low = high;
            high = swap;
        }
        String block = precheck(input, low, high, counters);
        if (block != null) {
            countBlock(counters, block);
            remember(diagnostic, excluded(low, high, "PRECHECK", block), retainAll, counters);
            return;
        }
        if (!score) return;
        long one = System.nanoTime();
        CompatibilityDecision decision = engine.decide(low, high, context);
        counters.scoreNanos += System.nanoTime() - one;
        PreparedEdge edge = fromDecision(low, high, decision);
        if (edge.solverEligible()) {
            counters.eligible++;
            if (solver.size() >= maxSolverEdges) {
                counters.cap("solver edge cap " + maxSolverEdges);
                return;
            }
            solver.add(edge);
        } else {
            counters.notEligible++;
            counters.note(low.userId(), "NO_RECOMMENDATION_ELIGIBLE_EDGE");
            counters.note(high.userId(), "NO_RECOMMENDATION_ELIGIBLE_EDGE");
            if (decision.pair().hardFilterResult().stream().anyMatch(code -> code.startsWith("HARD_"))) counters.hardBlocked++;
        }
        remember(diagnostic, edge, retainAll || edge.solverEligible(), counters);
    }

    private static PreparedEdge fromDecision(CompatibilitySubject low, CompatibilitySubject high, CompatibilityDecision decision) {
        var pair = decision.pair();
        boolean lowFirst = pair.userA() == low.userId();
        Double ab = lowFirst ? pair.scoreAB() : pair.scoreBA();
        Double ba = lowFirst ? pair.scoreBA() : pair.scoreAB();
        Double covAb = lowFirst ? pair.coverageAB() : pair.coverageBA();
        Double covBa = lowFirst ? pair.coverageBA() : pair.coverageAB();
        Double fit = pair.harmonicWithCoverage();
        int centi = fit == null ? -1 : OptimizationWeight.fitCenti(fit);
        String exclusion = "";
        if (!decision.recommendationEligible()) exclusion = "NOT_ELIGIBLE";
        else if (centi < 0) exclusion = "SCORE_OUT_OF_RANGE";
        return new PreparedEdge(low.userId(), high.userId(), low.profileVersion(), high.profileVersion(),
                low.generation(), high.generation(), "M02", decision, ab, ba, covAb, covBa, pair.minDirection(),
                centi > 0 ? fit : null, Math.max(centi, 0), exclusion.isEmpty(), false, exclusion, decision.reasons());
    }

    private static PreparedEdge excluded(CompatibilitySubject low, CompatibilitySubject high, String strategy, String reason) {
        if (low.userId() > high.userId()) {
            CompatibilitySubject swap = low;
            low = high;
            high = swap;
        }
        return new PreparedEdge(low.userId(), high.userId(), low.profileVersion(), high.profileVersion(),
                low.generation(), high.generation(), strategy, null, null, null, null, null, null, null, 0,
                false, false, reason, List.of(reason));
    }

    private static String precheck(GlobalMatchingInput input, CompatibilitySubject left, CompatibilitySubject right, Counters counters) {
        boolean versionBad = !input.profileSnapshotVersions().get(left.userId()).equals(left.profileVersion())
                || !input.profileSnapshotVersions().get(right.userId()).equals(right.profileVersion());
        boolean generationBad = !input.lifecycleGenerations().get(left.userId()).equals(left.generation())
                || !input.lifecycleGenerations().get(right.userId()).equals(right.generation());
        if (versionBad) {
            counters.note(left.userId(), "VERSION_MISMATCH");
            counters.note(right.userId(), "VERSION_MISMATCH");
        }
        if (generationBad) {
            counters.note(left.userId(), "GENERATION_MISMATCH");
            counters.note(right.userId(), "GENERATION_MISMATCH");
        }
        if (versionBad) return "VERSION_MISMATCH";
        if (generationBad) return "GENERATION_MISMATCH";
        if (!"GRANTED".equals(input.consentSnapshot().get(left.userId()))
                || !"GRANTED".equals(input.consentSnapshot().get(right.userId()))) {
            if (!"GRANTED".equals(input.consentSnapshot().get(left.userId()))) counters.note(left.userId(), "CONSENT_REVOKED");
            if (!"GRANTED".equals(input.consentSnapshot().get(right.userId()))) counters.note(right.userId(), "CONSENT_REVOKED");
            return "CONSENT_REVOKED";
        }
        if (left.withdrawn() || right.withdrawn()) {
            if (left.withdrawn()) counters.note(left.userId(), "PROFILE_WITHDRAWN");
            if (right.withdrawn()) counters.note(right.userId(), "PROFILE_WITHDRAWN");
            return "PROFILE_WITHDRAWN";
        }
        if (!left.current() || !right.current()) {
            if (!left.current()) counters.note(left.userId(), "PROFILE_STALE");
            if (!right.current()) counters.note(right.userId(), "PROFILE_STALE");
            return "PROFILE_STALE";
        }
        PairKey key = PairKey.of(left.userId(), right.userId());
        if (input.blockedPairs().contains(key)) return "BLOCKED";
        if (input.forbiddenPairs().contains(key)) return "FORBIDDEN";
        if (input.historicalRestrictions().contains(key)) return "HISTORICAL";
        return null;
    }

    private static void countBlock(Counters counters, String block) {
        switch (block) {
            case "PROFILE_WITHDRAWN" -> counters.withdrawnPairs++;
            case "CONSENT_REVOKED" -> counters.consentPairs++;
            case "HISTORICAL", "BLOCKED", "FORBIDDEN" -> counters.historicalPairs++;
            default -> counters.hardBlocked++;
        }
    }

    private static void remember(List<PreparedEdge> diagnostic, PreparedEdge edge, boolean retain, Counters counters) {
        if (!retain || diagnostic.size() >= GlobalBudgets.MAX_DIAGNOSTIC_EDGES) {
            counters.diagnosticOmitted++;
            return;
        }
        diagnostic.add(edge);
    }

    private static CompatibilitySubject subject(List<CompatibilitySubject> users, long id) {
        for (CompatibilitySubject user : users) if (user.userId() == id) return user;
        throw new IllegalArgumentException("missing " + id);
    }

    private static List<List<PreparedEdge>> groups(GlobalMatchingGraph graph) {
        List<List<PreparedEdge>> groups = new ArrayList<>();
        for (int i = 0; i < graph.input().eligibleUsers().size(); i++) groups.add(new ArrayList<>());
        java.util.Map<Long, Integer> index = new java.util.LinkedHashMap<>();
        for (var user : graph.input().eligibleUsers()) index.put(user.userId(), index.size());
        for (PreparedEdge edge : graph.solver()) {
            groups.get(index.get(edge.lowId())).add(edge);
            groups.get(index.get(edge.highId())).add(edge);
        }
        return groups;
    }

    private static GlobalMatchingGraph capped(GlobalMatchingInput input, Strategy strategy, int users, String detail, long started) {
        long ms = (System.nanoTime() - started) / 1_000_000L;
        return new GlobalMatchingGraph(input, strategy.name(), strategy.name(), GlobalMatchingGraph.Coverage.ABORTED, List.of(), List.of(),
                new GraphStats(users, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ms, 0, 0, 0),
                GlobalMatchingGraph.CAP_REACHED, detail, Map.of());
    }

    private static final class Counters {
        long considered;
        long passing;
        int eligible;
        int notEligible;
        int hardBlocked;
        int truncated;
        int withdrawnPairs;
        int consentPairs;
        int historicalPairs;
        long scoreNanos;
        long filterMs;
        int diagnosticOmitted;
        boolean capped;
        String detail = "";
        final java.util.Map<Long, List<String>> notes = new java.util.LinkedHashMap<>();

        void note(long userId, String code) {
            List<String> codes = notes.computeIfAbsent(userId, ignored -> new ArrayList<>());
            if (codes.size() < 8 && !codes.contains(code)) codes.add(code);
        }

        long scoreMs() {
            return scoreNanos / 1_000_000L;
        }

        void cap(String detail) {
            this.capped = true;
            this.detail = detail;
        }

        GraphStats stats(int users, int diagnostic, int solver, long profileMs, long filterMs, long scoreMs, long buildMs) {
            return new GraphStats(users, considered, diagnostic, solver, hardBlocked, notEligible, eligible, truncated,
                    withdrawnPairs, consentPairs, historicalPairs, profileMs, filterMs, scoreMs, buildMs);
        }
    }
}
