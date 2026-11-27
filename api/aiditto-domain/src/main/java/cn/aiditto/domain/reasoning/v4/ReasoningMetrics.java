package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engineering metrics for a fixture model. These numbers are not a real-model quality result.
 * The gates in {@link ReasoningExperiment} stay frozen and are not scored here.
 */
public final class ReasoningMetrics {
    private ReasoningMetrics() {}

    public record Report(int relations, int dev, int holdout, String datasetHash, String promptHash,
                         int directionCorrect, int directionAttempted, int safetyRefusals,
                         int fitTp, int fitFp, int fitExpected, int fitHit,
                         int conflictTp, int conflictFp, int conflictExpected, int conflictHit,
                         int complementTp, int complementFp, int complementExpected, int complementHit,
                         int unsupportedUsable, int usableClaims,
                         int missingDetected, int missingExpected,
                         int contradictionKept, int contradictionExpected,
                         int illegalHardAccepted, int unauthorizedReads,
                         int pairwiseBefore, int pairwiseAfter, int pairwiseTotal,
                         double ndcgBefore, double ndcgAfter,
                         long latencyP50, long latencyP95,
                         String fixtureModel, String realModel) {}

    public static void main(String[] args) {
        Report report = fixture();
        System.out.println("relations=" + report.relations() + " dev=" + report.dev() + " holdout=" + report.holdout());
        System.out.println("dataset=" + report.datasetHash());
        System.out.println("prompt=" + report.promptHash());
        System.out.println("direction=" + report.directionCorrect() + "/" + report.directionAttempted()
                + " refusals=" + report.safetyRefusals());
        System.out.println("fit=" + report.fitTp() + "/" + (report.fitTp() + report.fitFp()) + " relations=" + report.fitHit() + "/" + report.fitExpected());
        System.out.println("conflict=" + report.conflictTp() + "/" + (report.conflictTp() + report.conflictFp()) + " relations=" + report.conflictHit() + "/" + report.conflictExpected());
        System.out.println("complement=" + report.complementTp() + "/" + (report.complementTp() + report.complementFp()) + " relations=" + report.complementHit() + "/" + report.complementExpected());
        System.out.println("unsupportedUsable=" + report.unsupportedUsable() + "/" + report.usableClaims());
        System.out.println("missing=" + report.missingDetected() + "/" + report.missingExpected());
        System.out.println("contradiction=" + report.contradictionKept() + "/" + report.contradictionExpected());
        System.out.println("hard=" + report.illegalHardAccepted() + " unauthorized=" + report.unauthorizedReads());
        System.out.println("pairwise=" + report.pairwiseBefore() + "->" + report.pairwiseAfter() + "/" + report.pairwiseTotal());
        System.out.println("ndcg=" + report.ndcgBefore() + "->" + report.ndcgAfter());
        System.out.println("latencyP50=" + report.latencyP50() + " p95=" + report.latencyP95());
        System.out.println("real=" + report.realModel());
    }

    public static Report fixture() {
        ReasoningExperiment experiment = ReasoningExperiment.v1();
        RuleEchoModel model = new RuleEchoModel();
        ReasoningBudget budget = ReasoningBudget.experiment();
        List<ReasoningDataset.Relation> relations = ReasoningDataset.all();
        List<ReasoningExperiment.PairResult> results = new ArrayList<>();
        for (ReasoningDataset.Relation relation : relations) {
            CompatibilityContext context = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                    BidirectionalCompatibilityEngine.NOW, List.of(relation.seeker(), relation.candidate()));
            results.add(experiment.reason(relation.seeker(), relation.candidate(), context, model, budget));
        }
        return summarize(relations, results, model.latencies());
    }

    static Report summarize(List<ReasoningDataset.Relation> relations, List<ReasoningExperiment.PairResult> results, List<Long> latency) {
        int directionCorrect = 0;
        int directionAttempted = 0;
        int refusals = 0;
        int fitTp = 0, fitFp = 0, fitExpected = 0, fitHit = 0;
        int conflictTp = 0, conflictFp = 0, conflictExpected = 0, conflictHit = 0;
        int complementTp = 0, complementFp = 0, complementExpected = 0, complementHit = 0;
        int unsupported = 0, usable = 0;
        int missingDetected = 0, missingExpected = 0;
        int contradictionKept = 0, contradictionExpected = 0;
        int hard = 0, unauthorized = 0;
        for (int i = 0; i < relations.size(); i++) {
            ReasoningDataset.Relation relation = relations.get(i);
            ReasoningExperiment.PairResult result = results.get(i);
            Count ab = count(relation, result.agentA(), true);
            Count ba = count(relation, result.agentB(), false);
            directionCorrect += ab.directionCorrect + ba.directionCorrect;
            directionAttempted += ab.attempted + ba.attempted;
            refusals += ab.refused + ba.refused;
            fitTp += ab.fitTp + ba.fitTp;
            fitFp += ab.fitFp + ba.fitFp;
            conflictTp += ab.conflictTp + ba.conflictTp;
            conflictFp += ab.conflictFp + ba.conflictFp;
            complementTp += ab.complementTp + ba.complementTp;
            complementFp += ab.complementFp + ba.complementFp;
            unsupported += ab.unsupported + ba.unsupported;
            usable += ab.usable + ba.usable;
            hard += ab.hard + ba.hard;
            unauthorized += ab.unauthorized + ba.unauthorized;
            if (expects(relation, true, ReasoningCategory.SUPPORTED_COMPATIBILITY)) {
                fitExpected++;
                if (has(result.agentA(), ReasoningCategory.SUPPORTED_COMPATIBILITY)) fitHit++;
            }
            if (expects(relation, false, ReasoningCategory.SUPPORTED_COMPATIBILITY)) {
                fitExpected++;
                if (has(result.agentB(), ReasoningCategory.SUPPORTED_COMPATIBILITY)) fitHit++;
            }
            if (expects(relation, true, ReasoningCategory.POTENTIAL_CONFLICT)) {
                conflictExpected++;
                if (has(result.agentA(), ReasoningCategory.POTENTIAL_CONFLICT)) conflictHit++;
            }
            if (expects(relation, false, ReasoningCategory.POTENTIAL_CONFLICT)) {
                conflictExpected++;
                if (has(result.agentB(), ReasoningCategory.POTENTIAL_CONFLICT)) conflictHit++;
            }
            if (expects(relation, true, ReasoningCategory.POTENTIAL_COMPLEMENT)) {
                complementExpected++;
                if (has(result.agentA(), ReasoningCategory.POTENTIAL_COMPLEMENT)) complementHit++;
            }
            if (expects(relation, false, ReasoningCategory.POTENTIAL_COMPLEMENT)) {
                complementExpected++;
                if (has(result.agentB(), ReasoningCategory.POTENTIAL_COMPLEMENT)) complementHit++;
            }
            if (expects(relation, true, ReasoningCategory.INSUFFICIENT_EVIDENCE)) {
                missingExpected++;
                if (has(result.agentA(), ReasoningCategory.INSUFFICIENT_EVIDENCE)) missingDetected++;
            }
            if (expects(relation, true, ReasoningCategory.CONTRADICTION)) {
                contradictionExpected++;
                if (has(result.agentA(), ReasoningCategory.CONTRADICTION)) contradictionKept++;
            }
        }
        Pairwise pairwise = pairwise(relations, results);
        return new Report(relations.size(),
                (int) relations.stream().filter(item -> "dev".equals(item.split())).count(),
                (int) relations.stream().filter(item -> "holdout".equals(item.split())).count(),
                ReasoningDataset.hash(), ReasoningPrompt.hash(),
                directionCorrect, directionAttempted, refusals,
                fitTp, fitFp, fitExpected, fitHit, conflictTp, conflictFp, conflictExpected, conflictHit,
                complementTp, complementFp, complementExpected, complementHit, unsupported, usable,
                missingDetected, missingExpected, contradictionKept, contradictionExpected,
                hard, unauthorized, pairwise.before, pairwise.after, pairwise.total,
                pairwise.ndcgBefore, pairwise.ndcgAfter, percentile(latency, 0.50), percentile(latency, 0.95),
                RuleEchoModel.NAME, "NOT_RUN");
    }

    private static Count count(ReasoningDataset.Relation relation, DirectionalReasoning reasoning, boolean ab) {
        Count count = new Count();
        if (reasoning.status() == ReasoningStatus.REFUSED) count.refused = 1;
        else {
            count.attempted = 1;
            if (reasoning.status() == ReasoningStatus.COMPLETED && (ab ? "AB" : "BA").equals(reasoning.direction())) {
                count.directionCorrect = 1;
            }
        }
        SetView allowed = new SetView(ab ? relation.allowedAb() : relation.allowedBa());
        SetView forbidden = new SetView(ab ? relation.forbiddenAb() : relation.forbiddenBa());
        for (ReasoningClaim claim : reasoning.usable()) {
            count.usable++;
            if (claim.category() == ReasoningCategory.UNSUPPORTED_INFERENCE) count.unsupported++;
            if (claim.statement().contains("硬约束")) count.hard++;
            if (claim.evidenceRefs().stream().anyMatch(ref -> !relationAllows(relation, reasoning, ref))) count.unauthorized++;
            boolean ok = allowed.contains(claim.category());
            boolean bad = forbidden.contains(claim.category()) || !ok;
            if (claim.category() == ReasoningCategory.SUPPORTED_COMPATIBILITY) { if (ok) count.fitTp++; if (bad) count.fitFp++; }
            if (claim.category() == ReasoningCategory.POTENTIAL_CONFLICT) { if (ok) count.conflictTp++; if (bad) count.conflictFp++; }
            if (claim.category() == ReasoningCategory.POTENTIAL_COMPLEMENT) { if (ok) count.complementTp++; if (bad) count.complementFp++; }
        }
        return count;
    }

    private static boolean relationAllows(ReasoningDataset.Relation relation, DirectionalReasoning reasoning, String ref) {
        String owner = "AB".equals(reasoning.direction())
                ? (ref.contains(":preference") ? "u" + relation.seeker().userId() : "u" + relation.candidate().userId())
                : (ref.contains(":preference") ? "u" + relation.candidate().userId() : "u" + relation.seeker().userId());
        return ref.startsWith(owner + ":");
    }

    private static boolean expects(ReasoningDataset.Relation relation, boolean ab, ReasoningCategory category) {
        return (ab ? relation.allowedAb() : relation.allowedBa()).contains(category);
    }

    private static boolean has(DirectionalReasoning reasoning, ReasoningCategory category) {
        return reasoning.usable().stream().anyMatch(claim -> claim.category() == category);
    }

    private static Pairwise pairwise(List<ReasoningDataset.Relation> relations, List<ReasoningExperiment.PairResult> results) {
        Map<Long, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < relations.size(); i++) {
            if (relations.get(i).seeker().userId() >= 90000) continue;
            groups.computeIfAbsent(relations.get(i).seeker().userId(), ignored -> new ArrayList<>()).add(i);
        }
        int before = 0, after = 0, total = 0;
        double ndcgBefore = 0, ndcgAfter = 0;
        int groupsUsed = 0;
        for (List<Integer> group : groups.values()) {
            if (group.size() < 2) continue;
            groupsUsed++;
            ndcgBefore += ndcg(group, relations, results, true);
            ndcgAfter += ndcg(group, relations, results, false);
            for (int left : group) {
                for (int right : group) {
                    if (relations.get(left).gain() <= relations.get(right).gain()) continue;
                    total++;
                    if (better(results.get(left).schemeA().m02Score(), results.get(right).schemeA().m02Score())) before++;
                    if (better(results.get(left).schemeC().experimentalScore(), results.get(right).schemeC().experimentalScore())) after++;
                }
            }
        }
        return new Pairwise(before, after, total, groupsUsed == 0 ? 0 : ndcgBefore / groupsUsed, groupsUsed == 0 ? 0 : ndcgAfter / groupsUsed);
    }

    private static boolean better(Double left, Double right) {
        if (left == null) return false;
        return right == null || left > right;
    }

    private static double ndcg(List<Integer> group, List<ReasoningDataset.Relation> relations,
                               List<ReasoningExperiment.PairResult> results, boolean before) {
        List<Integer> ranked = new ArrayList<>(group);
        ranked.sort(Comparator
                .comparing((Integer index) -> score(results.get(index), before) == null)
                .thenComparing(index -> score(results.get(index), before), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(index -> relations.get(index).candidate().userId()));
        double dcg = 0;
        List<Integer> gains = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            int gain = relations.get(ranked.get(i)).gain();
            gains.add(gain);
            dcg += (Math.pow(2, gain) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        gains.sort(Comparator.reverseOrder());
        double ideal = 0;
        for (int i = 0; i < gains.size(); i++) ideal += (Math.pow(2, gains.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        return ideal == 0 ? 0 : dcg / ideal;
    }

    private static Double score(ReasoningExperiment.PairResult result, boolean before) {
        return before ? result.schemeA().m02Score() : result.schemeC().experimentalScore();
    }

    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static final class Count {
        int directionCorrect, attempted, refused, fitTp, fitFp, conflictTp, conflictFp, complementTp, complementFp;
        int unsupported, usable, hard, unauthorized;
    }

    private record SetView(java.util.Set<ReasoningCategory> values) {
        boolean contains(ReasoningCategory category) { return values.contains(category); }
    }

    private record Pairwise(int before, int after, int total, double ndcgBefore, double ndcgAfter) {}
}
