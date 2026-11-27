package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.Scorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Same pool, the same legal screen, the same budget, and the same labels.
 * B0 shuffles the legal pool. B1 ranks it with the production {@link Scorer}.
 * B2 is the structured hobby route. B3 is the frozen R1–R5 set.
 * StructuredRecallService is not called: it reads profile rows from PostgreSQL and applies the V3 one-way filter.
 */
public final class RecallControls {
    public record Ties(int equalPairs, int smallerIdFirst) {}

    private RecallControls() {}

    public static Map<String, RecallMetrics.Summary> compare(List<RecallSubject> pool, List<RecallSubject> query,
                                                             MatchingInput input, List<RecallMetrics.Label> labels) {
        Map<String, RecallMetrics.Summary> out = new LinkedHashMap<>();
        out.put("B0-random", measure(pool, query, input, labels, Set.of(), false));
        out.put("B1-scorer", measure(pool, query, input, labels, Set.of(), true));
        out.put("B2-structured", measure(pool, query, input, labels, V4RecallEngine.through(RecallRoute.R1), false));
        out.put("B3-multi", measure(pool, query, input, labels, V4RecallEngine.through(RecallRoute.R5), false));
        return out;
    }

    public static Ties scorerTies(RecallSubject seeker, List<RecallSubject> pool, MatchingInput input) {
        V4RecallEngine.Result screened = V4RecallEngine.ruleBaseline(seeker, pool, input, RecallBench.NOW, pool.size());
        Map<Long, RecallSubject> byId = new LinkedHashMap<>();
        for (RecallSubject subject : pool) byId.put(subject.userId(), subject);
        List<long[]> rows = new ArrayList<>();
        for (V4RecallEngine.Hit hit : screened.hits()) {
            RecallSubject other = byId.get(hit.candidateId());
            int score = Scorer.score(seeker.candidate(), other.candidate(), input.weekStart(), input.weights()).score();
            rows.add(new long[] {other.userId(), score});
        }
        rows.sort(Comparator.comparingLong((long[] row) -> row[1]).reversed().thenComparingLong(row -> row[0]));
        int equal = 0;
        int smaller = 0;
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i - 1)[1] != rows.get(i)[1]) continue;
            equal++;
            if (rows.get(i - 1)[0] < rows.get(i)[0]) smaller++;
        }
        return new Ties(equal, smaller);
    }

    private static RecallMetrics.Summary measure(List<RecallSubject> pool, List<RecallSubject> query, MatchingInput input,
                                                 List<RecallMetrics.Label> labels, Set<RecallRoute> routes, boolean scorer) {
        Map<Long, V4RecallEngine.Result> results = new LinkedHashMap<>();
        for (RecallSubject seeker : query) {
            V4RecallEngine.Result result = scorer
                    ? V4RecallEngine.ruleBaseline(seeker, pool, input, RecallBench.NOW, RecallBench.LIST_SIZE)
                    : V4RecallEngine.recall(seeker, pool, input, RecallBench.NOW, RecallBench.LIST_SIZE, routes, RecallBench.SEED);
            results.put(seeker.userId(), result);
        }
        return RecallMetrics.summarize(query, results, labels, pool, input);
    }
}
