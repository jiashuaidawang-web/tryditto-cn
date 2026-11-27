package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.Scorer;
import cn.aiditto.domain.matching.Weights;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares B0–B6 on the same labeled edges. The winner is chosen from the dev split only.
 * Holdout is reported and cannot change the winner. Thresholds are not lowered after the run.
 */
public final class CompatibilityAblation {
    public enum Method { B0, B1, B2, B3, B4, B5, B6 }

    public record MethodReport(Method method, int pairwiseCorrect, int pairwiseTotal, double ndcg,
                               int oneWayFalse, int oneWayTotal, int insufficientFalse, int insufficientTotal,
                               int conflictsDetected, int conflictTotal, int hardViolations, int unknownMishandled,
                               int withheldLeaks, int directionSeparated, int scored, int labeled,
                               long latencyP50Nanos, long latencyP95Nanos) {
        public String pairwiseText() { return pairwiseCorrect + "/" + pairwiseTotal; }
        public String oneWayText() { return oneWayFalse + "/" + oneWayTotal; }
        public String insufficientText() { return insufficientFalse + "/" + insufficientTotal; }
        public String conflictText() { return conflictsDetected + "/" + conflictTotal; }
    }

    public record Run(List<MethodReport> dev, List<MethodReport> holdout, Method selected, boolean offlineGo, String rule) {}

    private CompatibilityAblation() {}

    public static Run run() {
        BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();
        List<CompatibilityPopulation.LabeledPair> devPairs = CompatibilityPopulation.split("dev");
        List<CompatibilityPopulation.LabeledPair> holdoutPairs = CompatibilityPopulation.split("holdout");
        List<MethodReport> dev = evaluate(engine, devPairs);
        List<MethodReport> holdout = evaluate(engine, holdoutPairs);
        Method selected = select(dev);
        MethodReport chosen = report(dev, selected);
        MethodReport baseline = report(dev, Method.B0);
        MethodReport oneWay = report(dev, Method.B1);
        boolean go = chosen.pairwiseCorrect() * baseline.pairwiseTotal() > baseline.pairwiseCorrect() * chosen.pairwiseTotal()
                && chosen.oneWayFalse() * oneWay.oneWayTotal() < oneWay.oneWayFalse() * chosen.oneWayTotal()
                && chosen.hardViolations() == 0
                && chosen.unknownMishandled() == 0
                && chosen.withheldLeaks() == 0;
        String rule = "开发集上，在 B2 到 B6 中取成对排序正确数最高的方法。"
                + "并列时先看更低的单向误推荐，再看更低的证据不足误推荐，再看更高的强偏好冲突识别。"
                + "仍然并列则保留名单里更早的方法。保留集不参与选择，门槛不因结果下调。";
        return new Run(dev, holdout, selected, go, rule);
    }

    public static String format(Run run) {
        StringBuilder text = new StringBuilder();
        text.append("selected=").append(run.selected()).append(" offlineGo=").append(run.offlineGo()).append('\n');
        text.append("rule=").append(run.rule()).append('\n');
        append(text, "dev", run.dev());
        append(text, "holdout", run.holdout());
        return text.toString();
    }

    private static void append(StringBuilder text, String split, List<MethodReport> reports) {
        for (MethodReport report : reports) {
            text.append(split).append(' ').append(report.method())
                    .append(" pairwise=").append(report.pairwiseText())
                    .append(" ndcg=").append(round(report.ndcg()))
                    .append(" oneWayFalse=").append(report.oneWayText())
                    .append(" insufficientFalse=").append(report.insufficientText())
                    .append(" conflicts=").append(report.conflictText())
                    .append(" hard=").append(report.hardViolations())
                    .append(" unknownMishandled=").append(report.unknownMishandled())
                    .append(" withheldLeaks=").append(report.withheldLeaks())
                    .append(" directionSeparated=").append(report.directionSeparated()).append('/').append(report.oneWayTotal())
                    .append(" scored=").append(report.scored()).append('/').append(report.labeled())
                    .append(" p50ns=").append(report.latencyP50Nanos())
                    .append(" p95ns=").append(report.latencyP95Nanos())
                    .append('\n');
        }
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static Method select(List<MethodReport> dev) {
        MethodReport best = null;
        for (Method method : List.of(Method.B2, Method.B3, Method.B4, Method.B5, Method.B6)) {
            MethodReport report = report(dev, method);
            if (best == null || better(report, best)) best = report;
        }
        return best.method();
    }

    private static boolean better(MethodReport candidate, MethodReport current) {
        if (candidate.pairwiseCorrect() != current.pairwiseCorrect()) {
            return candidate.pairwiseCorrect() > current.pairwiseCorrect();
        }
        if (candidate.oneWayFalse() != current.oneWayFalse()) return candidate.oneWayFalse() < current.oneWayFalse();
        if (candidate.insufficientFalse() != current.insufficientFalse()) {
            return candidate.insufficientFalse() < current.insufficientFalse();
        }
        if (candidate.conflictsDetected() != current.conflictsDetected()) {
            return candidate.conflictsDetected() > current.conflictsDetected();
        }
        return false;
    }

    private static MethodReport report(List<MethodReport> reports, Method method) {
        return reports.stream().filter(report -> report.method() == method).findFirst().orElseThrow();
    }

    private static List<MethodReport> evaluate(BidirectionalCompatibilityEngine engine, List<CompatibilityPopulation.LabeledPair> pairs) {
        Map<Long, List<Scored>> bySeeker = new LinkedHashMap<>();
        int unknown = 0;
        int withheld = 0;
        int direction = 0;
        int oneWay = 0;
        int scored = 0;
        List<Long> engineLatency = new ArrayList<>();
        List<Long> scorerLatency = new ArrayList<>();
        for (CompatibilityPopulation.LabeledPair pair : pairs) {
            long started = System.nanoTime();
            PairCompatibilityResult result = engine.score(pair.seeker(), pair.candidate());
            engineLatency.add(System.nanoTime() - started);
            long v3Started = System.nanoTime();
            int v3 = Scorer.score(pair.seeker().candidate(), pair.candidate().candidate(),
                    BidirectionalCompatibilityEngine.WEEK, Weights.W1).score();
            scorerLatency.add(System.nanoTime() - v3Started);
            if (!result.hardFilterResult().isEmpty()) v3 = -1;
            bySeeker.computeIfAbsent(pair.seeker().userId(), ignored -> new ArrayList<>())
                    .add(new Scored(pair, result, v3));
            unknown += mishandled(result);
            withheld += leaks(result);
            if (result.status() == CompatibilityStatus.SCORED) scored++;
            if (pair.grade() == CompatibilityPopulation.Grade.ONE_WAY) {
                oneWay++;
                if (result.scoreAB() != null && result.scoreBA() != null && result.scoreAB() > result.scoreBA()) direction++;
            }
        }
        engineLatency.sort(Long::compare);
        scorerLatency.sort(Long::compare);
        List<MethodReport> reports = new ArrayList<>();
        for (Method method : Method.values()) {
            List<Long> latency = method == Method.B0 ? scorerLatency : engineLatency;
            reports.add(summarize(method, bySeeker, unknown, withheld, direction, oneWay, scored, pairs.size(), latency));
        }
        return reports;
    }

    private static MethodReport summarize(Method method, Map<Long, List<Scored>> bySeeker, int unknown, int withheld,
                                          int direction, int oneWay, int scored, int labeled, List<Long> latency) {
        int pairwiseCorrect = 0;
        int pairwiseTotal = 0;
        double ndcg = 0;
        int seekers = 0;
        int oneWayFalse = 0;
        int oneWayTotal = 0;
        int insufficientFalse = 0;
        int insufficientTotal = 0;
        int conflicts = 0;
        int conflictTotal = 0;
        int hard = 0;
        for (List<Scored> slate : bySeeker.values()) {
            List<Scored> ranked = new ArrayList<>(slate);
            ranked.sort(Comparator
                    .comparing((Scored item) -> rank(method, item) == null)
                    .thenComparing(item -> rank(method, item), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(item -> item.pair().candidate().userId()));
            seekers++;
            ndcg += ndcg(ranked);
            for (int i = 0; i < slate.size(); i++) {
                for (int j = 0; j < slate.size(); j++) {
                    if (slate.get(i).pair().grade().gain() <= slate.get(j).pair().grade().gain()) continue;
                    pairwiseTotal++;
                    Double better = rank(method, slate.get(i));
                    Double worse = rank(method, slate.get(j));
                    if (better != null && (worse == null || better > worse)) pairwiseCorrect++;
                }
            }
            for (Scored item : slate) {
                boolean recommended = recommend(method, item);
                if (recommended && !item.result().hardFilterResult().isEmpty()) hard++;
                if (item.pair().grade() == CompatibilityPopulation.Grade.ONE_WAY) {
                    oneWayTotal++;
                    if (recommended) oneWayFalse++;
                }
                if (item.pair().grade() == CompatibilityPopulation.Grade.INSUFFICIENT
                        || item.pair().grade() == CompatibilityPopulation.Grade.CONTESTED) {
                    insufficientTotal++;
                    if (recommended) insufficientFalse++;
                }
                if (item.pair().grade() == CompatibilityPopulation.Grade.CONFLICT) {
                    conflictTotal++;
                    if (!item.result().strongPreferenceConflicts().isEmpty()) conflicts++;
                }
            }
        }
        double meanNdcg = seekers == 0 ? 0 : ndcg / seekers;
        return new MethodReport(method, pairwiseCorrect, pairwiseTotal, meanNdcg, oneWayFalse, oneWayTotal,
                insufficientFalse, insufficientTotal, conflicts, conflictTotal, hard, unknown, withheld,
                direction, scored, labeled, percentile(latency, 0.50), percentile(latency, 0.95));
    }

    private static Double rank(Method method, Scored item) {
        if (!item.result().hardFilterResult().isEmpty()) return null;
        return switch (method) {
            case B0 -> item.v3() < 0 ? null : (double) item.v3();
            case B1 -> item.result().scoreAB();
            case B2 -> item.result().arithmeticMean();
            case B3 -> item.result().harmonicMean();
            case B4 -> item.result().minDirection();
            case B5 -> item.result().harmonicWithCoverage();
            case B6 -> item.result().harmonicWithCoverageAndStrength();
        };
    }

    private static boolean recommend(Method method, Scored item) {
        if (!item.result().hardFilterResult().isEmpty()) return false;
        Double score = rank(method, item);
        if (score == null) return false;
        if (method == Method.B5 || method == Method.B6) {
            Double lowest = item.result().minDirection();
            return lowest != null && lowest >= CompatibilityFormula.MIN_DIRECTION_SCORE;
        }
        return score >= CompatibilityFormula.MIN_DIRECTION_SCORE;
    }

    private static double ndcg(List<Scored> ranked) {
        double dcg = 0;
        List<Integer> gains = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            int gain = ranked.get(i).pair().grade().gain();
            gains.add(gain);
            dcg += (Math.pow(2, gain) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        gains.sort(Comparator.reverseOrder());
        double ideal = 0;
        for (int i = 0; i < gains.size(); i++) ideal += (Math.pow(2, gains.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        return ideal == 0 ? 0 : dcg / ideal;
    }

    private static int mishandled(PairCompatibilityResult result) {
        int count = 0;
        for (FeatureMatchContribution row : result.evidenceContributions()) {
            if (row.satisfaction() == Satisfaction.UNKNOWN && row.satisfactionValue() != null) count++;
        }
        if (result.explanationAB().evidence().knownWeight() == 0 && result.explanationAB().score() != null) count++;
        if (result.explanationBA().evidence().knownWeight() == 0 && result.explanationBA().score() != null) count++;
        return count;
    }

    private static int leaks(PairCompatibilityResult result) {
        int count = 0;
        for (FeatureMatchContribution row : result.evidenceContributions()) {
            if ("WITHHELD".equals(row.expectedValue()) || "WITHHELD".equals(row.actualValue())) count++;
        }
        return count;
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private record Scored(CompatibilityPopulation.LabeledPair pair, PairCompatibilityResult result, int v3) {}
}
