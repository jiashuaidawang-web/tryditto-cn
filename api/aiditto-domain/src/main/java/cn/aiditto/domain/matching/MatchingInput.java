package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The whole solver input: a frozen snapshot, no clock, no database (技术方案 §7). The same input and
 * seed always produce the same result.
 *
 * @param weekStart drop Wednesday; ages are measured on this date
 * @param pastPairs every pair ever matched in any week, whatever the outcome
 * @param reportedPairs every pair with a report in either direction
 * @param seed tie-break seed, normally derived from the week id
 * @param manualRun admin "立即匹配"; enables the open-match rule (§7.2 rule 11)
 */
public record MatchingInput(
        LocalDate weekStart,
        List<Candidate> candidates,
        Set<PairKey> pastPairs,
        Set<PairKey> reportedPairs,
        Weights weights,
        long seed,
        boolean manualRun) {

    public MatchingInput {
        Objects.requireNonNull(weekStart, "weekStart");
        Objects.requireNonNull(weights, "weights");
        candidates = List.copyOf(candidates);
        pastPairs = Set.copyOf(pastPairs);
        reportedPairs = Set.copyOf(reportedPairs);
        Set<Long> ids = new HashSet<>();
        for (Candidate c : candidates) {
            if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate candidate id " + c.id());
        }
    }

    public static MatchingInput of(LocalDate weekStart, List<Candidate> candidates, long seed) {
        return new MatchingInput(weekStart, candidates, Set.of(), Set.of(), Weights.W1, seed, false);
    }

    public MatchingInput withPastPairs(Set<PairKey> past) {
        return new MatchingInput(weekStart, candidates, past, reportedPairs, weights, seed, manualRun);
    }

    public MatchingInput withSeed(long s) {
        return new MatchingInput(weekStart, candidates, pastPairs, reportedPairs, weights, s, manualRun);
    }
}
