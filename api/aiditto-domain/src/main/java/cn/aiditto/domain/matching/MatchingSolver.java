package cn.aiditto.domain.matching;

import cn.aiditto.domain.matching.MatchingResult.MatchedPair;
import cn.aiditto.domain.matching.MatchingResult.SolverKind;
import cn.aiditto.domain.matching.MatchingResult.Stats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Weekly batch solver (技术方案 §7): drop under-18s, hard-filter every pair, score, drop pairs under the
 * stricter style threshold, then pick at most one partner per person maximising the total weight.
 * Blossom V is the primary algorithm; on timeout, exception or an invalid result the greedy solver runs.
 * Pure: no clock, no IO; same input → same output.
 */
public final class MatchingSolver {
    public static final String ALGO_VERSION = "blossom-v5+greedy/1";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    /** 学习排序分（0-100）在边权里的位权：AI 分 1 分 = 学习分 10 分 = 规则 objective 100 分；AI 主导、学习次之、规则再次。 */
    private static final long LEARNED_SCALE = 100_000L;

    private final WeightedMatcher primary;
    private final WeightedMatcher fallback;
    private final Duration timeout;

    public MatchingSolver() {
        this(new BlossomMatcher(), DEFAULT_TIMEOUT);
    }

    public MatchingSolver(WeightedMatcher primary, Duration timeout) {
        this.primary = primary;
        this.fallback = new GreedySolver();
        this.timeout = timeout;
    }

    public MatchingResult solve(MatchingInput in) {
        long t0 = System.nanoTime();
        List<Candidate> sorted = new ArrayList<>(in.candidates());
        sorted.sort(Comparator.comparingLong(Candidate::id));
        List<Candidate> pool = new ArrayList<>();
        int underage = 0;
        for (Candidate c : sorted) {
            if (HardFilter.adult(c, in.weekStart())) pool.add(c);
            else underage++;
        }

        Map<HardRule, Integer> filtered = new EnumMap<>(HardRule.class);
        int[] below = {0};
        List<Edge> edges = buildEdges(pool, in, filtered, below);
        long t1 = System.nanoTime();

        List<Edge> chosen;
        SolverKind kind = SolverKind.BLOSSOM;
        String fallbackReason = null;
        try {
            chosen = runWithTimeout(edges);
            String invalid = invalidReason(chosen, edges);
            if (invalid != null) throw new IllegalStateException(invalid);
        } catch (Exception e) {
            kind = SolverKind.GREEDY;
            fallbackReason = e instanceof TimeoutException ? "timeout after " + timeout.toMillis() + " ms"
                    : e.getClass().getSimpleName() + ": " + e.getMessage();
            chosen = fallback.match(edges);
        }
        long t2 = System.nanoTime();

        chosen = new ArrayList<>(chosen);
        chosen.sort(Comparator.comparingLong(Edge::low).thenComparingLong(Edge::high));
        Set<Long> matched = new HashSet<>();
        List<MatchedPair> pairs = new ArrayList<>();
        long total = 0;
        for (Edge e : chosen) {
            pairs.add(new MatchedPair(e.low(), e.high(), e.score()));
            matched.add(e.low());
            matched.add(e.high());
            total += e.weight();
        }
        List<Long> unmatched = new ArrayList<>();
        for (Candidate c : pool) if (!matched.contains(c.id())) unmatched.add(c.id());

        Stats stats = new Stats(ALGO_VERSION, in.weights().version(), pool.size(), underage,
                pool.size() * (pool.size() - 1) / 2, Map.copyOf(filtered), below[0], edges.size(), kind,
                fallbackReason, total, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
        return new MatchingResult(pairs, unmatched, stats);
    }

    /** Every pair that passes the hard rules and the threshold, sorted by {@code (low, high)}. */
    public static List<Edge> buildEdges(MatchingInput in) {
        List<Candidate> sorted = new ArrayList<>(in.candidates());
        sorted.sort(Comparator.comparingLong(Candidate::id));
        sorted.removeIf(c -> !HardFilter.adult(c, in.weekStart()));
        return buildEdges(sorted, in, new EnumMap<>(HardRule.class), new int[1]);
    }

    private static List<Edge> buildEdges(List<Candidate> pool, MatchingInput in, Map<HardRule, Integer> filtered, int[] below) {
        List<Edge> edges = new ArrayList<>();
        Weights w = in.weights();
        for (int i = 0; i < pool.size(); i++) {
            Candidate a = pool.get(i);
            for (int j = i + 1; j < pool.size(); j++) {
                Candidate b = pool.get(j);
                List<HardRule> v = HardFilter.violations(a, b, in);
                if (!v.isEmpty()) {
                    for (HardRule r : v) filtered.merge(r, 1, Integer::sum);
                    continue;
                }
                // Step 6：硬约束与风格门槛都在 AI 之前完成，AI 分只影响边权，
                // 绝不能让模型幻觉把没通过硬约束/门槛的对放进候选。
                ScoreBreakdown s = Scorer.score(a, b, in.weekStart(), w);
                if (s.score() < w.pairThreshold(a, b)) {
                    below[0]++;
                    continue;
                }
                edges.add(new Edge(a.id(), b.id(), s,
                        weight(s, in.seed(), a.id(), b.id(),
                                aiScore(in, a.id(), b.id()), learnedScore(in, a.id(), b.id()))));
            }
        }
        return edges;
    }

    /** M2 原逻辑，保留供既有性质测试与无 AI 分场景逐位一致使用。 */
    static long weight(ScoreBreakdown s, long seed, long low, long high) {
        return weight(s, seed, low, high, null, null);
    }

    /**
     * Step 6 边权：AI 分主导配对排序（Q1），学习排序分次之、规则分再次、公平分与确定性 tiebreak 保留。
     * <ul>
     *   <li>{@code ai == null && learned == null}：完全走 M2 原公式，零回归。</li>
     *   <li>有 AI 分：{@code ai * 1_000_000 + 规则 objective * 1000 + tiebreak}，AI 分差 1 分
     *       ≈ 规则分差 1000 分，AI 主导但同分时规则/公平仍能定序。</li>
     *   <li>有学习分：额外 {@code learned * 100_000}（{@link #LEARNED_SCALE}），位于 AI 分与规则分之间，
     *       由 {@code user_taste.weights} 直接计算，闭合「反馈→调权重→边权」学习链路（D6 方案 A）。</li>
     * </ul>
     * 展示分 {@code ScoreBreakdown.score()}、硬约束、门槛一律不变。
     */
    static long weight(ScoreBreakdown s, long seed, long low, long high, Integer ai) {
        return weight(s, seed, low, high, ai, null);
    }

    static long weight(ScoreBreakdown s, long seed, long low, long high, Integer ai, Integer learned) {
        long aiPart = ai == null ? 0L : ai.longValue() * 1_000_000L;
        long learnedPart = learned == null ? 0L : learned.longValue() * LEARNED_SCALE;
        return aiPart + learnedPart + s.objective() * 1000L + tiebreak(seed, low, high);
    }

    private static Integer aiScore(MatchingInput in, long a, long b) {
        return in.aiScores().get(PairKey.of(a, b));
    }

    private static Integer learnedScore(MatchingInput in, long a, long b) {
        return in.learnedScores().get(PairKey.of(a, b));
    }

    /** {@code hash(seed, low, high) mod 1000}, SplitMix64 finaliser. */
    static int tiebreak(long seed, long low, long high) {
        long z = seed * 0x9E3779B97F4A7C15L ^ low * 0xC2B2AE3D27D4EB4FL ^ high * 0x165667B19E3779F9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (int) Math.floorMod(z, 1000L);
    }

    private List<Edge> runWithTimeout(List<Edge> edges) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "matching-solver");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<List<Edge>> f = ex.submit(() -> primary.match(edges));
            try {
                return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ce) throw ce;
                throw e;
            } finally {
                f.cancel(true);
            }
        } finally {
            ex.shutdownNow();
        }
    }

    /** Null when {@code chosen} is a matching made only of input edges. */
    static String invalidReason(List<Edge> chosen, List<Edge> edges) {
        Map<PairKey, Edge> known = new HashMap<>();
        for (Edge e : edges) known.put(e.key(), e);
        Set<Long> seen = new HashSet<>();
        for (Edge e : chosen) {
            if (!e.equals(known.get(e.key()))) return "solver returned an unknown edge " + e.key();
            if (!seen.add(e.low()) || !seen.add(e.high())) return "solver matched someone twice in " + e.key();
        }
        return null;
    }
}
