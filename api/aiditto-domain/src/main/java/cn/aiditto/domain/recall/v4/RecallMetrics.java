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

    public record DirectedPair(long seeker, long candidate) {}

    /**
     * Ratios keep a fixed relevant-label denominator.
     * {@code labeledPrecisionAt20} uses only slots that are relevant or irrelevant.
     * {@code slotPrecisionAt20} uses every returned slot in the denominator. Unlabeled slots are not
     * confirmed negatives; {@code unlabeledAt20} counts them. MRR uses the full returned rank.
     * HitRate and NDCG use the top 20. All three exclude seekers who have no relevant label.
     */
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
            Map<String, Integer> routeFound,
            int recallHitsAt10,
            int recallHitsAt20,
            int recallHitsAt50,
            Double labeledPrecisionAt20,
            Double slotPrecisionAt20,
            int labeledRelevantAt20,
            int labeledIrrelevantAt20,
            int unlabeledAt20,
            int labelCoverageSlots,
            int inspectedSlotsAt20,
            Double labelCoverage,
            Double queryBidirectionalCoverage,
            int queryMutualEdges,
            int queryDirectedEdges,
            int poolMutualEdges,
            int poolDirectedEdges,
            int zeroIneligible,
            int zeroNoLegal,
            int zeroSparse,
            int zeroRecallFailure,
            int safetyAuditViolations,
            int mrrSeekers,
            int hitRateSeekers,
            int missingResultPositives) {}

    private RecallMetrics() {}

    public static Summary summarize(List<RecallSubject> seekers, Map<Long, V4RecallEngine.Result> results,
                                    List<Label> labels) {
        return summarize(seekers, results, labels, seekers, null);
    }

    public static Summary summarize(List<RecallSubject> seekers, Map<Long, V4RecallEngine.Result> results,
                                    List<Label> labels, List<RecallSubject> pool, cn.aiditto.domain.matching.MatchingInput input) {
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
        int zeroIneligible = 0;
        int zeroNoLegal = 0;
        int zeroSparse = 0;
        int zeroRecallFailure = 0;
        int missingPositives = 0;
        int irrelevantAt20 = 0;
        int unlabeledAt20 = 0;
        int labeledSlots = 0;
        Map<String, Integer> routeFound = new HashMap<>();
        Set<DirectedPair> returnedPairs = new HashSet<>();
        Set<Long> queryIds = new HashSet<>();
        for (RecallSubject seeker : seekers) queryIds.add(seeker.userId());
        for (V4RecallEngine.Result result : results.values()) {
            for (V4RecallEngine.Hit hit : result.hits()) {
                returnedPairs.add(new DirectedPair(hit.userId(), hit.candidateId()));
            }
        }
        for (RecallSubject seeker : seekers) {
            V4RecallEngine.Result result = results.get(seeker.userId());
            Map<Long, Label> mine = bySeeker.getOrDefault(seeker.userId(), Map.of());
            List<Label> positives = new ArrayList<>();
            for (Label label : mine.values()) {
                if ("RELEVANT".equals(label.kind())) positives.add(label);
            }
            eligible += positives.size();
            if (result == null) {
                zeros++;
                zeroRecallFailure++;
                missingPositives += positives.size();
                sizes.add(0);
                if (!positives.isEmpty()) {
                    seekersWithPositive++;
                    ndcgSeekers++;
                }
                continue;
            }
            violations += result.hardViolations();
            expired += result.expiredReturned();
            withdrawn += result.withdrawnReturned();
            sizes.add(result.hits().size());
            if (result.hits().isEmpty()) {
                zeros++;
                switch (result.gap()) {
                    case INELIGIBLE -> zeroIneligible++;
                    case NO_LEGAL_PARTNER -> zeroNoLegal++;
                    case SPARSE -> zeroSparse++;
                    default -> zeroRecallFailure++;
                }
            }
            if (result.legalCount() > 0) {
                coverBase++;
                if (!result.hits().isEmpty()) covered++;
            }
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
                        String route = reasonOf(result, positive.candidate());
                        routeFound.merge(route, 1, Integer::sum);
                    }
                    int rank = rankOf(result, positive.candidate());
                    if (rank < first) first = rank;
                    if (top50.contains(positive.candidate())) hit50++;
                    if (returned.contains(positive.candidate())) hitAll++;
                    if (top50.contains(positive.candidate()) && !top10.contains(positive.candidate())) budgetLoss++;
                }
                if (any) seekersHit++;
                if (first < Integer.MAX_VALUE) reciprocal += 1.0 / first;
                ndcg += ndcg(result, positives, 20);
                ndcgSeekers++;
            }
            int slots = Math.min(20, result.hits().size());
            for (int i = 0; i < slots; i++) {
                Label label = mine.get(result.hits().get(i).candidateId());
                if (label == null || "UNKNOWN".equals(label.kind())) {
                    unlabeledAt20++;
                    continue;
                }
                labeledSlots++;
                if ("HARD_VETO".equals(label.kind())) continue;
                if ("IRRELEVANT".equals(label.kind())) irrelevantAt20++;
                if ("RELEVANT".equals(label.kind())) precisionHits++;
            }
            precisionSlots += slots;
        }
        int queryDirected = 0;
        int queryMutual = 0;
        for (DirectedPair edge : returnedPairs) {
            if (!queryIds.contains(edge.seeker())) continue;
            directed++;
            if (returnedPairs.contains(new DirectedPair(edge.candidate(), edge.seeker()))) mutual++;
            if (queryIds.contains(edge.candidate())) {
                queryDirected++;
                if (returnedPairs.contains(new DirectedPair(edge.candidate(), edge.seeker()))) queryMutual++;
            }
        }
        int corpusLabels = 0;
        for (Label label : labels) {
            if (queryIds.contains(label.seeker()) && !"UNKNOWN".equals(label.kind())) corpusLabels++;
        }
        int corpusSlots = seekers.size() * Math.max((pool == null ? seekers.size() : pool.size()) - 1, 0);
        int safety = input == null || pool == null ? 0 : RecallSafetyAudit.violations(index(pool), input, results.values());
        sizes.sort(Comparator.naturalOrder());
        Double labeledPrecision = precisionHits + irrelevantAt20 == 0 ? null
                : precisionHits / (double) (precisionHits + irrelevantAt20);
        return new Summary(
                ratio(hit10, eligible),
                ratio(hit20, eligible),
                ratio(hit50, eligible),
                ratio(hitAll, eligible),
                precision(precisionHits, precisionHits + irrelevantAt20, precisionSlots),
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
                Map.copyOf(routeFound),
                hit10,
                hit20,
                hit50,
                labeledPrecision,
                ratio(precisionHits, precisionSlots),
                precisionHits,
                irrelevantAt20,
                unlabeledAt20,
                labeledSlots,
                precisionSlots,
                ratio(corpusLabels, corpusSlots),
                ratio(queryMutual, queryDirected),
                queryMutual,
                queryDirected,
                mutual,
                directed,
                zeroIneligible,
                zeroNoLegal,
                zeroSparse,
                zeroRecallFailure,
                safety,
                seekersWithPositive,
                seekersWithPositive,
                missingPositives);
    }

    private static Map<Long, RecallSubject> index(List<RecallSubject> pool) {
        Map<Long, RecallSubject> byId = new HashMap<>();
        for (RecallSubject subject : pool) byId.put(subject.userId(), subject);
        return byId;
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
