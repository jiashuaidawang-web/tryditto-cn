package cn.aiditto.domain.recall.v4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recall metrics. Unlabeled candidates are not negatives.
 * Precision is undefined when labeled rows cover less than half of the inspected slots.
 * An empty denominator stays null.
 */
public final class RecallMetrics {
    public record Label(long seeker, long candidate, String split, String kind, String direction, int grade) {}

    public record Summary(
            Double recallAt10,
            Double recallAt20,
            Double recallAt50,
            Double recallReturned,
            Double precisionAt20,
            Double hitRateAt20,
            Double mrr,
            Double ndcgAt20,
            Double candidateCoverage,
            Double bidirectionalCoverage,
            Double candidatesP50,
            Double candidatesP95,
            Double zeroCandidateRate,
            Double budgetLossAt10,
            int eligiblePositives,
            int hardViolations,
            int expiredReturned,
            int withdrawnReturned,
            Map<String, Integer> routeFound) {}

    private RecallMetrics() {}

    public static Summary summarize(List<RecallSubject> seekers, Map<Long, V4RecallEngine.Result> results,
                                    List<Label> labels) {
        Map<Long, Map<Long, Label>> bySeeker = new HashMap<>();
        for (Label label : labels) {
            bySeeker.computeIfAbsent(label.seeker(), ignored -> new HashMap<>()).put(label.candidate(), label);
        }
        int eligible = 0;
        int hit10 = 0;
        int hit20 = 0;
        int hit50 = 0;
        int hitAll = 0;
        int precisionHits = 0;
        int precisionLabeled = 0;
        int precisionSlots = 0;
        int seekersWithPositive = 0;
        int seekersHit = 0;
        double reciprocal = 0;
        double ndcg = 0;
        int ndcgSeekers = 0;
        int covered = 0;
        int coverBase = 0;
        int mutual = 0;
        int directed = 0;
        int budgetLoss = 0;
        int violations = 0;
        int expired = 0;
        int withdrawn = 0;
        List<Integer> sizes = new ArrayList<>();
        int zeros = 0;
        Map<String, Integer> routeFound = new HashMap<>();
        Set<Long> returnedPairs = new HashSet<>();
        for (RecallSubject seeker : seekers) {
            V4RecallEngine.Result result = results.get(seeker.userId());
            if (result == null) continue;
            violations += result.hardViolations();
            expired += result.expiredReturned();
            withdrawn += result.withdrawnReturned();
            sizes.add(result.hits().size());
            if (result.hits().isEmpty()) zeros++;
            if (result.legalCount() > 0) {
                coverBase++;
                if (!result.hits().isEmpty()) covered++;
            }
            for (V4RecallEngine.Hit hit : result.hits()) {
                returnedPairs.add(pair(hit.userId(), hit.candidateId()));
            }
            Map<Long, Label> mine = bySeeker.getOrDefault(seeker.userId(), Map.of());
            List<Label> positives = new ArrayList<>();
            for (Label label : mine.values()) {
                if ("RELEVANT".equals(label.kind())) positives.add(label);
            }
            eligible += positives.size();
            Set<Long> top10 = ids(result, 10);
            Set<Long> top20 = ids(result, 20);
            Set<Long> top50 = ids(result, 50);
            Set<Long> returned = ids(result, result.hits().size());
            if (!positives.isEmpty()) {
                seekersWithPositive++;
                boolean any = false;
                int first = Integer.MAX_VALUE;
                for (Label positive : positives) {
                    if (top10.contains(positive.candidate())) hit10++;
                    if (top20.contains(positive.candidate())) {
                        hit20++;
                        any = true;
                        first = Math.min(first, rankOf(result, positive.candidate()));
                        String route = reasonOf(result, positive.candidate());
                        routeFound.merge(route, 1, Integer::sum);
                    }
                    if (top50.contains(positive.candidate())) hit50++;
                    if (returned.contains(positive.candidate())) hitAll++;
                    if (top50.contains(positive.candidate()) && !top10.contains(positive.candidate())) budgetLoss++;
                }
                if (any) seekersHit++;
                if (first < Integer.MAX_VALUE) reciprocal += 1.0 / first;
                ndcg += ndcg(result, positives, 20);
                ndcgSeekers++;
            }
            int labeledHere = 0;
            int relevantHere = 0;
            int slots = Math.min(20, result.hits().size());
            for (int i = 0; i < slots; i++) {
                Label label = mine.get(result.hits().get(i).candidateId());
                if (label == null || "UNKNOWN".equals(label.kind())) continue;
                if ("HARD_VETO".equals(label.kind())) continue;
                labeledHere++;
                if ("RELEVANT".equals(label.kind())) relevantHere++;
            }
            precisionSlots += slots;
            precisionLabeled += labeledHere;
            precisionHits += relevantHere;
        }
        for (long key : returnedPairs) {
            long seeker = key >>> 32;
            long candidate = key & 0xffffffffL;
            directed++;
            if (returnedPairs.contains(pair(candidate, seeker))) mutual++;
        }
        sizes.sort(Comparator.naturalOrder());
        return new Summary(
                ratio(hit10, eligible),
                ratio(hit20, eligible),
                ratio(hit50, eligible),
                ratio(hitAll, eligible),
                precision(precisionHits, precisionLabeled, precisionSlots),
                ratio(seekersHit, seekersWithPositive),
                ratioDouble(reciprocal, seekersWithPositive),
                ratioDouble(ndcg, ndcgSeekers),
                ratio(covered, coverBase),
                ratio(mutual, directed),
                percentile(sizes, 0.50),
                percentile(sizes, 0.95),
                ratio(zeros, seekers.size()),
                ratio(budgetLoss, eligible),
                eligible,
                violations,
                expired,
                withdrawn,
                Map.copyOf(routeFound));
    }

    private static Set<Long> ids(V4RecallEngine.Result result, int k) {
        Set<Long> ids = new HashSet<>();
        int limit = Math.min(k, result.hits().size());
        for (int i = 0; i < limit; i++) ids.add(result.hits().get(i).candidateId());
        return ids;
    }

    private static int rankOf(V4RecallEngine.Result result, long candidate) {
        for (V4RecallEngine.Hit hit : result.hits()) {
            if (hit.candidateId() == candidate) return hit.fusedRank();
        }
        return Integer.MAX_VALUE;
    }

    private static String reasonOf(V4RecallEngine.Result result, long candidate) {
        for (V4RecallEngine.Hit hit : result.hits()) {
            if (hit.candidateId() == candidate) return hit.recallReasonCode();
        }
        return "";
    }

    private static double ndcg(V4RecallEngine.Result result, List<Label> positives, int k) {
        Map<Long, Integer> grade = new HashMap<>();
        for (Label label : positives) grade.put(label.candidate(), label.grade());
        double dcg = 0;
        int limit = Math.min(k, result.hits().size());
        for (int i = 0; i < limit; i++) {
            int value = grade.getOrDefault(result.hits().get(i).candidateId(), 0);
            if (value > 0) dcg += (Math.pow(2, value) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        List<Integer> ideal = new ArrayList<>(grade.values());
        ideal.sort(Comparator.reverseOrder());
        double idcg = 0;
        int n = Math.min(k, ideal.size());
        for (int i = 0; i < n; i++) {
            idcg += (Math.pow(2, ideal.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    private static long pair(long seeker, long candidate) {
        return (seeker << 32) ^ (candidate & 0xffffffffL);
    }

    public static Double ratio(int numerator, int denominator) {
        if (denominator == 0) return null;
        return numerator / (double) denominator;
    }

    private static Double ratioDouble(double numerator, int denominator) {
        if (denominator == 0) return null;
        return numerator / denominator;
    }

    private static Double precision(int hits, int labeled, int slots) {
        if (slots == 0 || labeled * 2 < slots) return null;
        if (labeled == 0) return null;
        return hits / (double) labeled;
    }

    private static Double percentile(List<Integer> sorted, double q) {
        if (sorted.isEmpty()) return null;
        int index = (int) Math.ceil(q * sorted.size()) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;
        return sorted.get(index).doubleValue();
    }
}
