package cn.aiditto.domain.matching;

/**
 * A pair that passed every hard rule and its threshold. {@code weight = objective × 1000 + tiebreak}
 * (integer, so equal scores still resolve deterministically).
 */
public record Edge(long low, long high, ScoreBreakdown score, long weight) {
    public Edge {
        if (low >= high) throw new IllegalArgumentException("low must be < high");
    }

    public PairKey key() {
        return new PairKey(low, high);
    }
}
