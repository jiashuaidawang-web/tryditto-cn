package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pins every component of {@code w1} to sample values (技术方案 §7.3: weight changes must bump the version). */
class ScorerTest {
    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);
    static final Weights W = Weights.W1;

    @Test
    void w1Constants() {
        assertThat(W.version()).isEqualTo("w1");
        assertThat(W.threshold(MatchingStyle.FASTER)).isEqualTo(35);
        assertThat(W.threshold(MatchingStyle.STEADY)).isEqualTo(45);
        assertThat(W.threshold(MatchingStyle.FEWER)).isEqualTo(55);
        assertThat(W.threshold(MatchingStyle.THE_ONE)).isEqualTo(65);
        assertThat(W.hobbyMax() + W.lookingMax() + W.styleSame() + W.identityStudentsClose() + W.budgetMatch() + W.ageMax() + W.distanceMax())
                .as("components add up to 100").isEqualTo(100);
        Candidate fast = Candidate.builder(1).style(MatchingStyle.FASTER).build();
        Candidate one = Candidate.builder(2).style(MatchingStyle.THE_ONE).build();
        assertThat(W.pairThreshold(fast, one)).isEqualTo(65);
    }

    @Test
    void defaultPair() {
        ScoreBreakdown s = Scorer.score(Candidate.builder(1).build(), Candidate.builder(2).build(), WEEK, W);
        assertThat(s).isEqualTo(new ScoreBreakdown(30, 20, 10, 10, 10, 3, 10, 0));
        assertThat(s.score()).isEqualTo(93);
        assertThat(s.objective()).isEqualTo(93);
    }

    @Test
    void hobbies() {
        Candidate a = Candidate.builder(1).hobbies("music", "movies", "travel").build();
        Candidate b = Candidate.builder(2).hobbies("music", "reading").build();
        assertThat(Scorer.hobbies(a, b, W)).isEqualTo(8);
        Candidate ad = a.toBuilder().hobbyDetails(Map.of("musicSound", Set.of("indie", "rock"))).build();
        Candidate bd = b.toBuilder().hobbyDetails(Map.of("musicSound", Set.of("indie"), "moviesContent", Set.of("indie"))).build();
        assertThat(Scorer.hobbies(ad, bd, W)).isEqualTo(10);
        Candidate none = Candidate.builder(3).hobbies(Set.of()).build();
        assertThat(Scorer.hobbies(none, none, W)).isZero();
        Candidate full = Candidate.builder(4).hobbies("music").hobbyDetails(Map.of("musicSound", Set.of("pop", "indie"))).build();
        assertThat(Scorer.hobbies(full, full, W)).as("capped").isEqualTo(30);
    }

    @Test
    void looking() {
        assertThat(Scorer.looking(Set.of(Looking.CASUAL), Set.of(Looking.SERIOUS), W)).isEqualTo(-10);
        assertThat(Scorer.looking(Set.of(Looking.LIFE_PARTNER, Looking.SERIOUS), Set.of(Looking.CASUAL), W)).isEqualTo(-10);
        assertThat(Scorer.looking(Set.of(Looking.CASUAL), Set.of(Looking.SERIOUS, Looking.CASUAL), W)).isEqualTo(20);
        assertThat(Scorer.looking(Set.of(Looking.FRIENDS), Set.of(Looking.NOT_SURE), W)).isEqualTo(10);
        assertThat(Scorer.looking(Set.of(Looking.FRIENDS), Set.of(Looking.SERIOUS), W)).isZero();
    }

    @Test
    void style() {
        assertThat(Scorer.style(MatchingStyle.STEADY, MatchingStyle.STEADY, W)).isEqualTo(10);
        assertThat(Scorer.style(MatchingStyle.STEADY, MatchingStyle.FEWER, W)).isEqualTo(6);
        assertThat(Scorer.style(MatchingStyle.FASTER, MatchingStyle.THE_ONE, W)).isEqualTo(2);
    }

    @Test
    void identity() {
        Candidate junior = Candidate.builder(1).schoolYear(SchoolYear.JUNIOR).build();
        assertThat(Scorer.identity(junior, Candidate.builder(2).schoolYear(SchoolYear.SENIOR).build(), W)).isEqualTo(10);
        assertThat(Scorer.identity(junior, Candidate.builder(2).schoolYear(SchoolYear.FRESHMAN).build(), W)).isEqualTo(5);
        assertThat(Scorer.identity(junior, Candidate.builder(2).schoolYear(SchoolYear.PHD).build(), W)).isEqualTo(2);
        assertThat(Scorer.identity(junior, Candidate.builder(2).schoolYear(SchoolYear.OTHER).build(), W)).isEqualTo(5);
        Candidate worker = Candidate.builder(3).student(false).build();
        assertThat(Scorer.identity(worker, Candidate.builder(4).student(false).build(), W)).isEqualTo(8);
        assertThat(Scorer.identity(junior, worker, W)).isEqualTo(3);
    }

    @Test
    void budget() {
        assertThat(Scorer.budget(Budget.UNDER_20, Budget.UNDER_20, W)).isEqualTo(10);
        assertThat(Scorer.budget(Budget.NO_PREF, Budget.OVER_100, W)).isEqualTo(10);
        assertThat(Scorer.budget(Budget.UNDER_20, Budget.FROM_20_TO_50, W)).isEqualTo(6);
        assertThat(Scorer.budget(Budget.UNDER_20, Budget.OVER_100, W)).isZero();
    }

    @Test
    void ageFit() {
        Candidate a = Candidate.builder(1).birthday(WEEK.minusYears(20)).ageRange(18, 22).build();
        Candidate b = Candidate.builder(2).birthday(WEEK.minusYears(20)).ageRange(20, 20).build();
        assertThat(Scorer.ageFit(a, b, WEEK, W)).isEqualTo(10);
        Candidate edge = Candidate.builder(3).birthday(WEEK.minusYears(22)).ageRange(18, 22).build();
        assertThat(Scorer.ageFit(a, edge, WEEK, W)).as("(0 + 1) / 2").isEqualTo(5);
        Candidate older = Candidate.builder(4).birthday(WEEK.minusYears(22)).ageRange(22, 26).build();
        assertThat(Scorer.ageFit(a, older, WEEK, W)).isZero();
    }

    @Test
    void distance() {
        Candidate here = Candidate.builder(1).location(TestPlaces.get("pku").at(TravelMode.TRANSIT, 60)).build();
        assertThat(Scorer.distance(here, here, W)).isEqualTo(10);
        Candidate there = Candidate.builder(2).location(TestPlaces.get("sanlitun").at(TravelMode.TRANSIT, 60)).build();
        int d = Scorer.distance(here, there, W);
        assertThat(d).isBetween(1, 9).isEqualTo(Scorer.distance(there, here, W));
        Candidate nearer = Candidate.builder(3).location(TestPlaces.get("tsinghua").at(TravelMode.TRANSIT, 60)).build();
        assertThat(Scorer.distance(here, nearer, W)).isGreaterThan(d);
    }

    @Test
    void clampAndFairness() {
        assertThat(new ScoreBreakdown(0, -10, 2, 2, 0, 0, 0, 0).score()).isZero();
        assertThat(new ScoreBreakdown(30, 20, 10, 10, 10, 10, 10, 9).score()).isEqualTo(100);
        assertThat(new ScoreBreakdown(30, 20, 10, 10, 10, 10, 10, 9).objective()).isEqualTo(109);
        assertThat(W.fairnessBonus(Candidate.builder(1).emptyWeekStreak(2).build())).isEqualTo(6);
        assertThat(W.fairnessBonus(Candidate.builder(1).emptyWeekStreak(5).build())).isEqualTo(9);
        ScoreBreakdown s = Scorer.score(Candidate.builder(1).emptyWeekStreak(1).build(), Candidate.builder(2).emptyWeekStreak(3).build(), WEEK, W);
        assertThat(s.fairness()).isEqualTo(12);
        assertThat(s.score()).isEqualTo(93);
    }
}
