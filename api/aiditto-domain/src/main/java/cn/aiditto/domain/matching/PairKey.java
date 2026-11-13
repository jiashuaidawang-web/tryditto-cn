package cn.aiditto.domain.matching;

/** An unordered pair of user ids, stored as {@code low < high}. */
public record PairKey(long low, long high) implements Comparable<PairKey> {
    public PairKey {
        if (low >= high) throw new IllegalArgumentException("low must be < high");
    }

    public static PairKey of(long a, long b) {
        return new PairKey(Math.min(a, b), Math.max(a, b));
    }

    @Override
    public int compareTo(PairKey o) {
        int c = Long.compare(low, o.low);
        return c != 0 ? c : Long.compare(high, o.high);
    }
}
