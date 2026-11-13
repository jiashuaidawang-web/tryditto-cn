package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.matching.MatchingResult.SolverKind;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/** jqwik properties over random pools; {@link MatchingSeededTest} runs the same checks as plain JUnit. */
class MatchingPropertiesTest {

    @Property(tries = 300)
    void solverResultsSatisfyEveryRule(@ForAll long seed, @ForAll @IntRange(min = 0, max = 70) int size) {
        MatchingInput in = RandomPopulation.input(seed, size);
        MatchingResult r = new MatchingSolver().solve(in);
        assertThat(MatchingInvariants.violations(in, r)).isEmpty();
        assertThat(r.stats().solver()).isEqualTo(SolverKind.BLOSSOM);
    }

    @Property(tries = 100)
    void greedyFallbackSatisfiesEveryRule(@ForAll long seed, @ForAll @IntRange(min = 0, max = 70) int size) {
        MatchingInput in = RandomPopulation.input(seed, size);
        MatchingResult r = new MatchingSolver(new GreedySolver(), Duration.ofSeconds(30)).solve(in);
        assertThat(MatchingInvariants.violations(in, r)).isEmpty();
    }

    @Property(tries = 100)
    void deterministic(@ForAll long seed, @ForAll @IntRange(min = 0, max = 50) int size) {
        MatchingInput in = RandomPopulation.input(seed, size);
        MatchingResult a = new MatchingSolver().solve(in);
        MatchingResult b = new MatchingSolver().solve(RandomPopulation.input(seed, size));
        assertThat(a.pairs()).isEqualTo(b.pairs());
        assertThat(a.unmatched()).isEqualTo(b.unmatched());
    }

    @Property(tries = 200)
    void filterAgreesWithTheRestatedRules(@ForAll long seed, @ForAll @IntRange(min = 2, max = 30) int size) {
        MatchingInput in = RandomPopulation.input(seed, size);
        List<Candidate> cs = in.candidates();
        for (Candidate a : cs) {
            for (Candidate b : cs) {
                if (a == b) continue;
                boolean filter = HardFilter.passes(a, b, in);
                assertThat(filter).as(a.id() + "/" + b.id()).isEqualTo(MatchingInvariants.hardViolations(a, b, in).isEmpty());
                assertThat(filter).as("symmetric").isEqualTo(HardFilter.passes(b, a, in));
                assertThat(Scorer.score(a, b, in.weekStart(), in.weights())).isEqualTo(Scorer.score(b, a, in.weekStart(), in.weights()));
            }
        }
    }

    @Property(tries = 500)
    void blossomIsOptimalOnSmallGraphs(@ForAll long seed) {
        List<Edge> g = randomGraph(new Random(seed), 12);
        List<Edge> blossom = new BlossomMatcher().match(g);
        List<Edge> greedy = new GreedySolver().match(g);
        long best = BruteForce.bestWeight(g);
        assertThat(MatchingSolver.invalidReason(blossom, g)).isNull();
        assertThat(MatchingSolver.invalidReason(greedy, g)).isNull();
        assertThat(BruteForce.total(blossom)).isEqualTo(best);
        assertThat(BruteForce.total(greedy) * 2).as("greedy is a 1/2-approximation").isGreaterThanOrEqualTo(best);
    }

    @Property(tries = 50)
    void blossomNeverWorseThanGreedyOnPools(@ForAll long seed, @ForAll @IntRange(min = 10, max = 80) int size) {
        MatchingInput in = RandomPopulation.input(seed, size);
        List<Edge> edges = MatchingSolver.buildEdges(in);
        assertThat(BruteForce.total(new BlossomMatcher().match(edges))).isGreaterThanOrEqualTo(BruteForce.total(new GreedySolver().match(edges)));
    }

    static List<Edge> randomGraph(Random r, int maxVertices) {
        int n = 1 + r.nextInt(maxVertices);
        double density = 0.15 + r.nextDouble() * 0.7;
        List<Edge> g = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (r.nextDouble() < density) {
                    long w = r.nextInt(4) == 0 ? 50_000 : 1 + r.nextInt(100_000);
                    g.add(new Edge(10L * i + 3, 10L * j + 3, MatchingSolverTest.ANY, w));
                }
            }
        }
        return g;
    }
}
