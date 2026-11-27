package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.matching.PairKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class GlobalMatchStudy {
    public static final int[] SCALE_SIZES = {100, 500, 1_000, 5_000, 10_000};

    private GlobalMatchStudy() {}

    public static String line(GlobalMatchingResult result) {
        int min = result.pairs().stream().mapToInt(GlobalMatchingResult.MatchedPair::fitCenti).min().orElse(0);
        double mean = result.pairs().isEmpty() ? 0
                : result.pairs().stream().mapToInt(GlobalMatchingResult.MatchedPair::fitCenti).average().orElse(0) / 100.0;
        return "scope=" + result.scope() + " objective=" + result.objective()
                + " pairs=" + result.pairs().size()
                + " users=" + result.matchedUsers() + "/" + (result.matchedUsers() + result.unmatched().size())
                + " fitCenti=" + result.totalFitCenti()
                + " minFit=" + (min / 100.0)
                + " meanFit=" + String.format(java.util.Locale.ROOT, "%.2f", mean)
                + " weight=" + result.totalOptimizationWeight()
                + " safety=" + result.safetyViolations()
                + " optimal=" + result.optimalForThisGraph()
                + " full=" + result.fullPopulationGraph()
                + " status=" + result.status()
                + " reasons=" + result.unmatchedReasons()
                + " solveMs=" + result.solveMs()
                + distribution(result)
                + " detail=" + result.statusDetail();
    }

    private static String distribution(GlobalMatchingResult result) {
        if (result.pairs().isEmpty()) return " p10=na p50=na p95=na coverage=na direction=na highQuality=0/0";
        List<Integer> fit = new ArrayList<>();
        List<Double> coverage = new ArrayList<>();
        List<Double> direction = new ArrayList<>();
        int high = 0;
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) {
            fit.add(pair.fitCenti());
            if (pair.fitCenti() >= GlobalBudgets.G3_MIN_FIT_CENTI) high++;
            if (pair.coverageLowToHigh() != null && pair.coverageHighToLow() != null) {
                coverage.add(Math.min(pair.coverageLowToHigh(), pair.coverageHighToLow()));
            }
            if (pair.minDirection() != null) direction.add(pair.minDirection());
        }
        fit.sort(Comparator.naturalOrder());
        coverage.sort(Comparator.naturalOrder());
        direction.sort(Comparator.naturalOrder());
        return " p10=" + (percentile(fit, 0.10) / 100.0)
                + " p50=" + (percentile(fit, 0.50) / 100.0)
                + " p95=" + (percentile(fit, 0.95) / 100.0)
                + " minCoverage=" + (coverage.isEmpty() ? "na" : coverage.get(0))
                + " minDirection=" + (direction.isEmpty() ? "na" : direction.get(0))
                + " highQuality=" + high + "/" + fit.size();
    }

    private static int percentile(List<Integer> sorted, double p) {
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;
        return sorted.get(index);
    }

    public static String graphLine(GlobalMatchingGraph graph) {
        GraphStats stats = graph.stats();
        return "strategy=" + graph.strategy() + " scope=" + graph.scope()
                + " status=" + graph.status()
                + " users=" + stats.users()
                + " considered=" + stats.consideredPairs()
                + " eligible=" + stats.eligible()
                + " solver=" + stats.solverEdges()
                + " notEligible=" + stats.notEligible()
                + " hard=" + stats.hardBlocked()
                + " truncated=" + stats.truncated()
                + " withdrawn=" + stats.withdrawnPairs()
                + " consent=" + stats.consentPairs()
                + " historical=" + stats.historicalPairs()
                + " profileMs=" + stats.profileMs()
                + " filterMs=" + stats.filterMs()
                + " scoreMs=" + stats.scoreMs()
                + " " + graph.statusDetail();
    }

    public static List<ScalePoint> scale() {
        List<ScalePoint> out = new ArrayList<>();
        long deadline = System.nanoTime() + GlobalBudgets.WALL_MS * 1_000_000L;
        Double perPair = null;
        for (int users : SCALE_SIZES) {
            long pairs = (long) users * (users - 1) / 2;
            if (System.nanoTime() > deadline || (perPair != null && perPair * pairs > GlobalBudgets.WALL_MS)) {
                out.add(new ScalePoint(users, pairs, null, null, null, null, "SKIPPED",
                        "remaining wall or measured pair time would pass 90s"));
                continue;
            }
            GlobalMatchingInput input = GlobalMatchFixture.scale(users);
            GlobalMatchingGraph graph = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL, deadline,
                    GlobalBudgets.MAX_SOLVER_EDGES);
            GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, Objective.SELECTED);
            if (graph.stats().consideredPairs() > 0 && graph.stats().scoreMs() > 0) {
                perPair = graph.stats().scoreMs() / (double) graph.stats().consideredPairs();
            }
            StringBuilder detail = new StringBuilder(graph.statusDetail())
                    .append(" profileMs=").append(graph.stats().profileMs())
                    .append(" filterMs=").append(graph.stats().filterMs())
                    .append(" scoreMs=").append(graph.stats().scoreMs())
                    .append(" buildMs=").append(graph.stats().buildMs())
                    .append(" planMs=").append(result.planMs())
                    .append(" auditMs=").append(result.auditMs())
                    .append(" wallMs=").append(result.wallMs())
                    .append(" heapBytes=").append(result.heapSample())
                    .append(" safety=").append(result.safetyViolations())
                    .append(" pairs=").append(result.pairs().size())
                    .append(" fit=").append(result.totalFitCenti())
                    .append(" optimal=").append(result.optimalForThisGraph());
            if (graph.complete()) {
                for (int k : new int[]{10, 20, 50}) {
                    GlobalMatchingGraph top = GlobalCandidateGraphBuilder.topK(graph, k);
                    GlobalMatchingResult topResult = V4GlobalMatchingEngine.solve(top, Objective.SELECTED);
                    detail.append(" top").append(k).append("=").append(topResult.pairs().size())
                            .append("/").append(topResult.totalFitCenti());
                }
                GlobalMatchingGraph shortlist = GlobalCandidateGraphBuilder.build(input,
                        GlobalCandidateGraphBuilder.Strategy.F_SHORTLIST, deadline, GlobalBudgets.MAX_SOLVER_EDGES);
                GlobalMatchingResult shortResult = V4GlobalMatchingEngine.solve(shortlist, Objective.SELECTED);
                detail.append(" Fedges=").append(shortlist.stats().solverEdges())
                        .append(" FscoreMs=").append(shortlist.stats().scoreMs())
                        .append(" Fpairs=").append(shortResult.pairs().size())
                        .append(" Ffit=").append(shortResult.totalFitCenti())
                        .append(" Fsafety=").append(shortResult.safetyViolations());
            }
            out.add(new ScalePoint(users, graph.stats().consideredPairs(), graph.stats().eligible(), graph.stats().solverEdges(),
                    graph.stats().scoreMs(), result.solveMs(), result.status(), detail.toString()));
            if (!GlobalMatchingGraph.COMPLETE.equals(graph.status())) {
                for (int skipped : SCALE_SIZES) {
                    if (skipped <= users) continue;
                    out.add(new ScalePoint(skipped, (long) skipped * (skipped - 1) / 2, null, null, null, null, "NOT_STARTED",
                            "not started: a smaller population already crossed the solver edge cap"));
                }
                break;
            }
        }
        return out;
    }

    public static String labJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"policy\": \"").append(Objective.SELECTED_VERSION).append("\",\n  \"cases\": [\n");
        List<Case> cases = List.of(
                mathCase("local-optimum", GlobalMatchFixture.localOptimum()),
                mathCase("cardinality", GlobalMatchFixture.cardinality()),
                mathCase("triangle", GlobalMatchFixture.triangle()),
                mathCase("odd-node", GlobalMatchFixture.odd()),
                mathCase("empty", GlobalMatchFixture.empty()),
                mathCase("topk-full", GlobalMatchFixture.truncation()),
                mathCase("topk-1", GlobalCandidateGraphBuilder.topK(GlobalMatchFixture.truncation(), 1)),
                mathCase("quality-tier", GlobalMatchFixture.qualityTier()),
                mathCase("fairness", GlobalMatchFixture.fairness()));
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) out.append(",\n");
            out.append(cases.get(i).json());
        }
        GlobalMatchingGraph dev = GlobalCandidateGraphBuilder.build(GlobalMatchFixture.dev(), GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL);
        out.append(",\n").append(populationCase("dev-cohort", dev).json());
        out.append("\n  ]\n}\n");
        return out.toString();
    }

    private static Case mathCase(String id, GlobalMatchingGraph graph) {
        return new Case(id, graph, List.of(Objective.G0_GREEDY, Objective.G1_MAX_WEIGHT, Objective.G2_CARDINALITY_THEN_WEIGHT,
                Objective.G3_QUALITY_TIER, Objective.G4_FAIRNESS));
    }

    private static Case populationCase(String id, GlobalMatchingGraph graph) {
        return new Case(id, graph, List.of(Objective.G0_GREEDY, Objective.G1_MAX_WEIGHT, Objective.G2_CARDINALITY_THEN_WEIGHT,
                Objective.G3_QUALITY_TIER, Objective.G4_FAIRNESS));
    }

    public record ScalePoint(int users, long considered, Integer eligible, Integer solverEdges, Long scoreMs, Long solveMs,
                             String status, String detail) {}

    private record Case(String id, GlobalMatchingGraph graph, List<Objective> objectives) {
        String json() {
            StringBuilder out = new StringBuilder();
            out.append("    {\n      \"id\": \"").append(id).append("\",\n");
            out.append("      \"fullPopulation\": ").append(graph.fullPopulation()).append(",\n");
            out.append("      \"scope\": \"").append(graph.scope()).append("\",\n");
            out.append("      \"users\": [");
            for (int i = 0; i < graph.input().eligibleUsers().size(); i++) {
                var user = graph.input().eligibleUsers().get(i);
                if (i > 0) out.append(", ");
                out.append("{\"id\":").append(user.userId())
                        .append(",\"version\":").append(user.profileVersion())
                        .append(",\"generation\":").append(user.generation())
                        .append(",\"consent\":\"").append(graph.input().consentSnapshot().get(user.userId())).append("\"")
                        .append(",\"withdrawn\":").append(user.withdrawn())
                        .append(",\"current\":").append(user.current())
                        .append(",\"unmatchedWeeks\":").append(graph.input().unmatchedWeeks(user.userId()))
                        .append("}");
            }
            out.append("],\n      \"solverEdges\": [");
            writeEdges(out, graph.solver());
            out.append("],\n      \"refusedEdges\": [");
            List<PreparedEdge> refused = new ArrayList<>();
            for (PreparedEdge edge : graph.diagnostic()) if (!edge.solverEligible()) refused.add(edge);
            writeEdges(out, refused);
            out.append("],\n      \"runs\": [");
            for (int i = 0; i < objectives.size(); i++) {
                GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, objectives.get(i));
                if (i > 0) out.append(", ");
                out.append("{\"objective\":\"").append(result.objective())
                        .append("\",\"optimalForThisGraph\":").append(result.optimalForThisGraph())
                        .append(",\"pairs\":[");
                for (int p = 0; p < result.pairs().size(); p++) {
                    GlobalMatchingResult.MatchedPair pair = result.pairs().get(p);
                    if (p > 0) out.append(",");
                    out.append("{\"pair\":\"").append(pair.lowId()).append("-").append(pair.highId())
                            .append("\",\"fit\":").append(pair.fitCenti() / 100.0)
                            .append(",\"weight\":").append(pair.optimizationWeight())
                            .append(",\"eligible\":").append(pair.recommendationEligible())
                            .append("}");
                }
                out.append("],\"fitCenti\":").append(result.totalFitCenti())
                        .append(",\"optimizationWeight\":").append(result.totalOptimizationWeight())
                        .append(",\"safety\":").append(result.safetyViolations())
                        .append(",\"safetyBreakdown\":{");
                int s = 0;
                for (Map.Entry<String, Integer> entry : result.safetyBreakdown().entrySet()) {
                    if (s++ > 0) out.append(",");
                    out.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                }
                out.append("},\"unmatched\":[");
                for (int u = 0; u < result.unmatched().size(); u++) {
                    GlobalMatchingResult.UnmatchedPerson person = result.unmatched().get(u);
                    if (u > 0) out.append(",");
                    out.append("{\"id\":").append(person.userId())
                            .append(",\"reason\":\"").append(person.finalReason()).append("\"}");
                }
                out.append("]}");
            }
            out.append("]\n    }");
            return out.toString();
        }

        private static void writeEdges(StringBuilder out, List<PreparedEdge> edges) {
            List<PreparedEdge> sorted = new ArrayList<>(edges);
            sorted.sort(Comparator.comparingLong(PreparedEdge::lowId).thenComparingLong(PreparedEdge::highId));
            for (int i = 0; i < sorted.size(); i++) {
                PreparedEdge edge = sorted.get(i);
                if (i > 0) out.append(", ");
                out.append("{\"pair\":\"").append(edge.lowId()).append("-").append(edge.highId())
                        .append("\",\"fit\":").append(edge.fitCenti() / 100.0)
                        .append(",\"scoreAB\":").append(edge.scoreLowToHigh())
                        .append(",\"scoreBA\":").append(edge.scoreHighToLow())
                        .append(",\"coverageAB\":").append(edge.coverageLowToHigh())
                        .append(",\"coverageBA\":").append(edge.coverageHighToLow())
                        .append(",\"eligible\":").append(edge.solverEligible())
                        .append(",\"exclusion\":\"").append(edge.exclusion()).append("\"}");
            }
        }
    }

    public static int labeledKept(GlobalMatchingResult result, List<PairKey> labeled) {
        int kept = 0;
        for (PairKey key : labeled) {
            for (GlobalMatchingResult.MatchedPair pair : result.pairs()) {
                if (pair.key().equals(key)) kept++;
            }
        }
        return kept;
    }
}
