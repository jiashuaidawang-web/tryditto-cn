package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The same invariants as {@link MatchingPropertiesTest} on fixed seeds, without jqwik. */
class MatchingSeededTest {

    static LongStream seeds() {
        return LongStream.range(0, 40).map(i -> 0x5EED_0000L + i * 7919);
    }

    @ParameterizedTest
    @MethodSource("seeds")
    void blossomAndGreedyResultsSatisfyEveryRule(long seed) {
        MatchingInput in = RandomPopulation.input(seed, 20 + (int) Math.floorMod(seed, 100));
        assertThat(MatchingInvariants.violations(in, new MatchingSolver().solve(in))).isEmpty();
        assertThat(MatchingInvariants.violations(in, new MatchingSolver(new GreedySolver(), Duration.ofSeconds(30)).solve(in))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("seeds")
    void blossomIsOptimalOnSmallGraphs(long seed) {
        List<Edge> g = MatchingPropertiesTest.randomGraph(new Random(seed), 14);
        assertThat(BruteForce.total(new BlossomMatcher().match(g))).isEqualTo(BruteForce.bestWeight(g));
    }
}
