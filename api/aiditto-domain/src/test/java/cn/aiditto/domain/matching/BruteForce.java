package cn.aiditto.domain.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/** Exact maximum-weight matching by DP over vertex subsets; only for tiny graphs (≤ 20 vertices). */
final class BruteForce {
    private BruteForce() {}

    static long bestWeight(List<Edge> edges) {
        List<Long> ids = new ArrayList<>(new TreeSet<>(edges.stream().flatMap(e -> java.util.stream.Stream.of(e.low(), e.high())).toList()));
        int n = ids.size();
        if (n > 20) throw new IllegalArgumentException("too many vertices for brute force: " + n);
        long[][] w = new long[n][n];
        for (long[] row : w) Arrays.fill(row, -1);
        for (Edge e : edges) {
            int i = ids.indexOf(e.low());
            int j = ids.indexOf(e.high());
            w[i][j] = w[j][i] = e.weight();
        }
        long[] memo = new long[1 << n];
        Arrays.fill(memo, -1);
        return best((1 << n) - 1, w, memo);
    }

    private static long best(int free, long[][] w, long[] memo) {
        if (free == 0) return 0;
        if (memo[free] >= 0) return memo[free];
        int i = Integer.numberOfTrailingZeros(free);
        int rest = free & ~(1 << i);
        long b = best(rest, w, memo);
        for (int j = i + 1; j < w.length; j++) {
            if ((rest & (1 << j)) != 0 && w[i][j] >= 0) b = Math.max(b, w[i][j] + best(rest & ~(1 << j), w, memo));
        }
        memo[free] = b;
        return b;
    }

    static long total(List<Edge> chosen) {
        return chosen.stream().mapToLong(Edge::weight).sum();
    }
}
