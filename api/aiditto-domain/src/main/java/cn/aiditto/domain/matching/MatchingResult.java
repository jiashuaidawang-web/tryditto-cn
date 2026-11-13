package cn.aiditto.domain.matching;

import java.util.List;
import java.util.Map;

/**
 * Solver output: the pairs to write as {@code draft} matches, the people left without a match this week,
 * and statistics for {@code week.stats}.
 */
public record MatchingResult(List<MatchedPair> pairs, List<Long> unmatched, Stats stats) {

    public enum SolverKind { BLOSSOM, GREEDY }

    /** {@code score} is the 0–100 quality stored in {@code match.score}. */
    public record MatchedPair(long low, long high, ScoreBreakdown breakdown) {
        public int score() {
            return breakdown.score();
        }

        public PairKey key() {
            return new PairKey(low, high);
        }
    }

    /**
     * @param filtered how many pairs each hard rule removed (a pair can count under several rules)
     * @param belowThreshold pairs that passed the hard rules but scored under the pair threshold
     * @param fallbackReason why greedy was used, or {@code null}
     */
    public record Stats(
            String algoVersion,
            String weightsVersion,
            int participants,
            int excludedUnderage,
            int pairsConsidered,
            Map<HardRule, Integer> filtered,
            int belowThreshold,
            int edges,
            SolverKind solver,
            String fallbackReason,
            long totalWeight,
            long buildMillis,
            long solveMillis) {}

    public MatchingResult {
        pairs = List.copyOf(pairs);
        unmatched = List.copyOf(unmatched);
    }
}
