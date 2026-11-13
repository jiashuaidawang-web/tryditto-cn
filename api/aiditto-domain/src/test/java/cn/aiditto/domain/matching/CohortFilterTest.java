package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner rule: students and workers are separate pools, and synthetic users only meet demo accounts. */
class CohortFilterTest {
    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);

    static Candidate person(long id, boolean student, Cohort cohort) {
        return Candidate.builder(id).student(student).cohort(cohort).build();
    }

    static MatchingInput in(Candidate... cs) {
        return MatchingInput.of(WEEK, List.of(cs), 1);
    }

    @Test
    void studentsDoNotMatchWorkers() {
        Candidate student = person(1, true, Cohort.REAL);
        Candidate worker = person(2, false, Cohort.REAL);
        assertThat(HardFilter.violations(student, worker, in(student, worker))).containsExactly(HardRule.LIFE_STAGE);
        assertThat(MatchingInvariants.hardViolations(student, worker, in(student, worker))).contains("life stage");
    }

    @Test
    void syntheticOnlyMatchesDemo() {
        Candidate synthetic = person(1, true, Cohort.SYNTHETIC);
        Candidate demo = person(2, true, Cohort.DEMO);
        Candidate real = person(3, true, Cohort.REAL);
        Candidate otherSynthetic = person(4, true, Cohort.SYNTHETIC);
        Candidate otherDemo = person(5, true, Cohort.DEMO);
        MatchingInput all = in(synthetic, demo, real, otherSynthetic, otherDemo);

        assertThat(HardFilter.passes(synthetic, demo, all)).isTrue();
        assertThat(HardFilter.passes(demo, otherDemo, all)).isTrue();
        assertThat(HardFilter.violations(synthetic, real, all)).contains(HardRule.COHORT);
        assertThat(HardFilter.violations(synthetic, otherSynthetic, all)).contains(HardRule.COHORT);
        assertThat(HardFilter.violations(demo, real, all)).contains(HardRule.COHORT);
        assertThat(HardFilter.passes(real, person(6, true, Cohort.REAL), in(real, person(6, true, Cohort.REAL)))).isTrue();

        assertThat(MatchingInvariants.hardViolations(synthetic, real, all)).contains("cohort");
        assertThat(MatchingInvariants.hardViolations(synthetic, demo, all)).doesNotContain("cohort");
    }

    @Test
    void solverNeverPairsSyntheticWithRealOrSynthetic() {
        Candidate realA = person(1, true, Cohort.REAL);
        Candidate realB = person(2, true, Cohort.REAL);
        Candidate demo = person(3, true, Cohort.DEMO);
        Candidate synthetic = person(4, true, Cohort.SYNTHETIC);
        Candidate other = person(5, true, Cohort.SYNTHETIC);
        MatchingInput input = in(realA, realB, demo, synthetic, other);
        MatchingResult result = new MatchingSolver().solve(input);
        assertThat(MatchingInvariants.violations(input, result)).isEmpty();
        assertThat(result.pairs()).allSatisfy(p -> {
            Cohort a = input.candidates().stream().filter(c -> c.id() == p.low()).findFirst().orElseThrow().cohort();
            Cohort b = input.candidates().stream().filter(c -> c.id() == p.high()).findFirst().orElseThrow().cohort();
            assertThat(HardFilter.cohortsCompatible(a, b)).isTrue();
        });
        assertThat(result.pairs()).anyMatch(p -> p.low() == 3 && p.high() == 4 || p.low() == 4 && p.high() == 3
                || (p.low() == 1 && p.high() == 2));
    }
}
