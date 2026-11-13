package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.matching.MatchingResult.MatchedPair;
import cn.aiditto.domain.matching.MatchingResult.SolverKind;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchingSolverTest {
    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);
    static final ScoreBreakdown ANY = new ScoreBreakdown(30, 20, 10, 10, 10, 10, 10, 0);

    static Edge e(long a, long b, long w) {
        return new Edge(a, b, ANY, w);
    }

    @Test
    void blossomBeatsGreedyOnAPath() {
        List<Edge> path = List.of(e(1, 2, 60), e(2, 3, 100), e(3, 4, 60));
        assertThat(BruteForce.total(new GreedySolver().match(path))).isEqualTo(100);
        assertThat(new BlossomMatcher().match(path)).containsExactlyInAnyOrder(e(1, 2, 60), e(3, 4, 60));
    }

    @Test
    void blossomHandlesOddCycles() {
        List<Edge> g = List.of(e(1, 2, 10), e(2, 3, 10), e(1, 3, 10), e(3, 4, 9), e(4, 5, 1), e(5, 6, 8), e(1, 6, 1));
        List<Edge> m = new BlossomMatcher().match(g);
        assertThat(BruteForce.total(m)).isEqualTo(BruteForce.bestWeight(g));
        assertThat(MatchingSolver.invalidReason(m, g)).isNull();
    }

    @Test
    void emptyAndSingleEdge() {
        assertThat(new BlossomMatcher().match(List.of())).isEmpty();
        assertThat(new BlossomMatcher().match(List.of(e(5, 9, 1)))).containsExactly(e(5, 9, 1));
        MatchingResult r = new MatchingSolver().solve(MatchingInput.of(WEEK, List.of(), 1));
        assertThat(r.pairs()).isEmpty();
        assertThat(r.stats().solver()).isEqualTo(SolverKind.BLOSSOM);
    }

    @Test
    void endToEndSmallPool() {
        Candidate m1 = Candidate.builder(1).gender(Gender.MAN).wants(GenderPref.WOMEN).build();
        Candidate m2 = Candidate.builder(2).gender(Gender.MAN).wants(GenderPref.WOMEN).hobbies("gym").build();
        Candidate w1 = Candidate.builder(3).gender(Gender.WOMAN).wants(GenderPref.MEN).build();
        Candidate minor = Candidate.builder(4).gender(Gender.WOMAN).wants(GenderPref.MEN).birthday(WEEK.minusYears(17)).build();
        MatchingResult r = new MatchingSolver().solve(MatchingInput.of(WEEK, List.of(w1, m2, minor, m1), 7));
        assertThat(r.pairs()).extracting(MatchedPair::key).containsExactly(PairKey.of(1, 3));
        assertThat(r.unmatched()).containsExactly(2L);
        assertThat(r.stats().participants()).isEqualTo(3);
        assertThat(r.stats().excludedUnderage()).isEqualTo(1);
        assertThat(r.stats().pairsConsidered()).isEqualTo(3);
        assertThat(r.stats().filtered()).containsEntry(HardRule.GENDER, 1);
        assertThat(r.stats().edges()).isEqualTo(2);
        assertThat(r.stats().algoVersion()).isEqualTo(MatchingSolver.ALGO_VERSION);
        assertThat(r.stats().weightsVersion()).isEqualTo("w1");
        assertThat(MatchingInvariants.violations(MatchingInput.of(WEEK, List.of(w1, m2, minor, m1), 7), r)).isEmpty();
    }

    @Test
    void belowThresholdIsDropped() {
        Candidate a = Candidate.builder(1).style(MatchingStyle.THE_ONE).hobbies("art").looking(Looking.CASUAL)
                .budget(Budget.UNDER_20).build();
        Candidate b = Candidate.builder(2).style(MatchingStyle.FASTER).hobbies("gym").looking(Looking.SERIOUS)
                .budget(Budget.OVER_100).build();
        MatchingResult r = new MatchingSolver().solve(MatchingInput.of(WEEK, List.of(a, b), 1));
        assertThat(r.pairs()).isEmpty();
        assertThat(r.stats().belowThreshold()).isEqualTo(1);
    }

    @Test
    void fallsBackOnException() {
        MatchingSolver s = new MatchingSolver(edges -> { throw new IllegalStateException("boom"); }, Duration.ofSeconds(5));
        MatchingInput in = RandomPopulation.input(3, 40);
        MatchingResult r = s.solve(in);
        assertThat(r.stats().solver()).isEqualTo(SolverKind.GREEDY);
        assertThat(r.stats().fallbackReason()).contains("IllegalStateException").contains("boom");
        assertThat(MatchingInvariants.violations(in, r)).isEmpty();
    }

    @Test
    void fallsBackOnTimeout() {
        MatchingSolver s = new MatchingSolver(edges -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }, Duration.ofMillis(100));
        MatchingInput in = RandomPopulation.input(4, 40);
        long t0 = System.nanoTime();
        MatchingResult r = s.solve(in);
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(5));
        assertThat(r.stats().solver()).isEqualTo(SolverKind.GREEDY);
        assertThat(r.stats().fallbackReason()).startsWith("timeout");
        assertThat(MatchingInvariants.violations(in, r)).isEmpty();
    }

    @Test
    void fallsBackOnInvalidResult() {
        MatchingSolver twice = new MatchingSolver(edges -> {
            for (Edge x : edges) for (Edge y : edges) {
                if (x != y && (x.low() == y.low() || x.low() == y.high())) return List.of(x, y);
            }
            throw new AssertionError("fixture needs two edges sharing a person");
        }, Duration.ofSeconds(5));
        MatchingInput in = MatchingInput.of(WEEK, List.of(Candidate.builder(1).build(), Candidate.builder(2).build(), Candidate.builder(3).build()), 5);
        MatchingResult r = twice.solve(in);
        assertThat(r.pairs()).hasSize(1);
        assertThat(r.stats().solver()).isEqualTo(SolverKind.GREEDY);
        assertThat(r.stats().fallbackReason()).contains("twice");

        MatchingSolver invented = new MatchingSolver(edges -> List.of(new Edge(1, 2, ANY, 1)), Duration.ofSeconds(5));
        assertThat(invented.solve(in).stats().fallbackReason()).contains("unknown edge");
    }

    @Test
    void sameInputSameOutput() {
        MatchingInput in = RandomPopulation.input(11, 80);
        MatchingResult a = new MatchingSolver().solve(in);
        MatchingResult b = new MatchingSolver().solve(in);
        assertThat(a.pairs()).isEqualTo(b.pairs());
        assertThat(a.unmatched()).isEqualTo(b.unmatched());
        assertThat(a.stats().totalWeight()).isEqualTo(b.stats().totalWeight());
    }

    @Test
    void seedBreaksTiesAndNothingElse() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) seen.add(MatchingSolver.tiebreak(seed, 1, 2));
        assertThat(seen).hasSizeGreaterThan(10);
        assertThat(MatchingSolver.tiebreak(42, 1, 2)).isEqualTo(MatchingSolver.tiebreak(42, 1, 2)).isBetween(0, 999);
        assertThat(MatchingSolver.weight(ANY, 42, 1, 2) / 1000).isEqualTo(ANY.objective());

        List<Candidate> twins = List.of(
                Candidate.builder(1).gender(Gender.MAN).wants(GenderPref.WOMEN).build(),
                Candidate.builder(2).gender(Gender.WOMAN).wants(GenderPref.MEN).build(),
                Candidate.builder(3).gender(Gender.WOMAN).wants(GenderPref.MEN).build());
        Set<PairKey> winners = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            winners.add(new MatchingSolver().solve(MatchingInput.of(WEEK, twins, seed)).pairs().get(0).key());
        }
        assertThat(winners).as("identical candidates: different seeds pick different winners").hasSize(2);
    }

    @Test
    void fairnessBonusTipsTheBalance() {
        Candidate m = Candidate.builder(1).gender(Gender.MAN).wants(GenderPref.WOMEN).build();
        Candidate w1 = Candidate.builder(2).gender(Gender.WOMAN).wants(GenderPref.MEN).build();
        Candidate w2 = Candidate.builder(3).gender(Gender.WOMAN).wants(GenderPref.MEN).emptyWeekStreak(2).build();
        for (long seed = 0; seed < 10; seed++) {
            MatchingResult r = new MatchingSolver().solve(MatchingInput.of(WEEK, List.of(m, w1, w2), seed));
            assertThat(r.pairs()).extracting(MatchedPair::key).containsExactly(PairKey.of(1, 3));
            assertThat(r.pairs().get(0).score()).as("bonus does not leak into the shown score").isEqualTo(93);
        }
    }

    @Test
    void neverRematchedAcrossWeeks() {
        MatchingInput in = RandomPopulation.input(21, 60);
        Set<PairKey> history = new HashSet<>();
        for (int week = 0; week < 6; week++) {
            MatchingInput weekIn = new MatchingInput(in.weekStart().plusWeeks(week), in.candidates(), history,
                    in.reportedPairs(), in.weights(), in.seed() + week, false);
            MatchingResult r = new MatchingSolver().solve(weekIn);
            assertThat(MatchingInvariants.violations(weekIn, r)).isEmpty();
            for (MatchedPair p : r.pairs()) assertThat(history.add(p.key())).as("rematched " + p.key()).isTrue();
        }
    }
}
