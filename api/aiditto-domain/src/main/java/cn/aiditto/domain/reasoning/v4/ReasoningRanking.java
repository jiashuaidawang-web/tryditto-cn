package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking schemes on one frozen candidate pool. B0 does not move a score.
 * C1 and C2 are reported apart. A tie is not a win.
 */
public final class ReasoningRanking {
    private ReasoningRanking() {}

    public record Scheme(String name, int pairwise, int total, double ndcg,
                         int scoreMoves, int ineligibleMoves, int insufficientRecommended, int oneWayRecommended) {}

    public record Comparison(Scheme m02, Scheme serverExplanation, Scheme modelExplanation, Scheme c1, Scheme c2) {}

    public static Comparison fixture() {
        ReasoningExperiment experiment = ReasoningExperiment.v1();
        List<ReasoningDataset.Relation> relations = ReasoningDataset.all();
        List<ReasoningExperiment.PairResult> results = new ArrayList<>();
        ReasoningBudget budget = new ReasoningBudget(16, 2, 400);
        for (ReasoningDataset.Relation relation : relations) {
            results.add(experiment.reason(relation.seeker(), relation.candidate(), context(relation), new RuleEchoModel(), budget));
        }
        return compare(relations, results);
    }

    public static Comparison compare(List<ReasoningDataset.Relation> relations, List<ReasoningExperiment.PairResult> results) {
        return new Comparison(
                scheme("A", relations, results, result -> result.schemeA().m02Score()),
                scheme("B0", relations, results, result -> result.schemeA().m02Score()),
                scheme("B1", relations, results, result -> result.schemeB().experimentalScore()),
                scheme("C1", relations, results, result -> result.schemeC1().experimentalScore()),
                scheme("C2", relations, results, result -> result.schemeC2().experimentalScore()));
    }

    private static Scheme scheme(String name, List<ReasoningDataset.Relation> relations,
                                 List<ReasoningExperiment.PairResult> results, Score score) {
        Map<Long, List<Integer>> groups = new LinkedHashMap<>();
        int moves = 0;
        int ineligible = 0;
        int insufficient = 0;
        int oneWay = 0;
        for (int i = 0; i < relations.size(); i++) {
            ReasoningDataset.Relation relation = relations.get(i);
            ReasoningExperiment.PairResult result = results.get(i);
            if (relation.seeker().userId() < 90000) groups.computeIfAbsent(relation.seeker().userId(), ignored -> new ArrayList<>()).add(i);
            boolean moved = "C1".equals(name) ? result.schemeC1().rankChanged()
                    : "C2".equals(name) && result.schemeC2().rankChanged();
            if (moved) moves++;
            if (moved && !result.decision().recommendationEligible()) ineligible++;
            if (result.decision().recommendationEligible()
                    && (relation.kind() == ReasoningDataset.Kind.MISSING || relation.kind() == ReasoningDataset.Kind.CONTRADICTION)) {
                insufficient++;
            }
            if (result.decision().recommendationEligible() && relation.kind() == ReasoningDataset.Kind.ONE_WAY) oneWay++;
        }
        int pairwise = 0;
        int total = 0;
        double ndcg = 0;
        int used = 0;
        for (List<Integer> group : groups.values()) {
            if (group.size() < 2) continue;
            used++;
            ndcg += ndcg(group, relations, results, score);
            for (int left : group) {
                for (int right : group) {
                    if (relations.get(left).gain() <= relations.get(right).gain()) continue;
                    total++;
                    if (better(score.of(results.get(left)), score.of(results.get(right)))) pairwise++;
                }
            }
        }
        return new Scheme(name, pairwise, total, used == 0 ? 0 : ndcg / used, moves, ineligible, insufficient, oneWay);
    }

    private static double ndcg(List<Integer> group, List<ReasoningDataset.Relation> relations,
                               List<ReasoningExperiment.PairResult> results, Score score) {
        List<Integer> ranked = new ArrayList<>(group);
        ranked.sort(Comparator.comparing((Integer index) -> score.of(results.get(index)) == null)
                .thenComparing(index -> score.of(results.get(index)), Comparator.nullsLast(Comparator.reverseOrder()))
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

    private static boolean better(Double left, Double right) {
        if (left == null) return false;
        return right == null || left > right;
    }

    private static CompatibilityContext context(ReasoningDataset.Relation relation) {
        return CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                List.of(relation.seeker(), relation.candidate()));
    }

    private interface Score {
        Double of(ReasoningExperiment.PairResult result);
    }
}
