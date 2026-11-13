package cn.aiditto.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HardFilterTest {
    static final LocalDate WEEK = LocalDate.of(2026, 11, 18);

    static Candidate.Builder base(long id) {
        return Candidate.builder(id).contactHashes(Set.of("h" + id));
    }

    static MatchingInput in(Candidate... cs) {
        return MatchingInput.of(WEEK, List.of(cs), 1);
    }

    /** Asserts the rule fires in both argument orders, and is the only rule that fires. */
    static void onlyRule(HardRule rule, Candidate a, Candidate b, MatchingInput in) {
        assertThat(HardFilter.violations(a, b, in)).containsExactly(rule);
        assertThat(HardFilter.violations(b, a, in)).containsExactly(rule);
        assertThat(HardFilter.passes(a, b, in)).isFalse();
    }

    static void passes(Candidate a, Candidate b, MatchingInput in) {
        assertThat(HardFilter.violations(a, b, in)).isEmpty();
        assertThat(HardFilter.violations(b, a, in)).isEmpty();
    }

    @Test
    void defaultsPass() {
        Candidate a = base(1).build();
        Candidate b = base(2).build();
        passes(a, b, in(a, b));
    }

    @Test
    void self() {
        Candidate a = base(1).build();
        assertThat(HardFilter.violations(a, a, in(a))).contains(HardRule.SELF);
    }

    @Test
    void adult() {
        Candidate a = base(1).ageRange(17, 30).build();
        Candidate minor = base(2).birthday(WEEK.minusYears(18).plusDays(1)).ageRange(17, 30).build();
        onlyRule(HardRule.ADULT, a, minor, in(a, minor));
        Candidate justAdult = base(3).birthday(WEEK.minusYears(18)).build();
        passes(a, justAdult, in(a, justAdult));
    }

    @Test
    void samePool() {
        Candidate a = base(1).build();
        Candidate b = base(2).pool("sh").build();
        onlyRule(HardRule.SAME_POOL, a, b, in(a, b));
    }

    @ParameterizedTest(name = "{0} wants {1}; {2} wants {3} → {4}")
    @CsvSource({
            "MAN, WOMEN, WOMAN, MEN, true",
            "MAN, WOMEN, WOMAN, WOMEN, false",
            "MAN, MEN, MAN, MEN, true",
            "WOMAN, WOMEN, WOMAN, WOMEN, true",
            "WOMAN, MEN, WOMAN, EVERYONE, false",
            "NONBINARY, EVERYONE, WOMAN, NONBINARY, true",
            "NONBINARY, EVERYONE, WOMAN, MEN, false",
            "OTHER, EVERYONE, MAN, EVERYONE, true",
            "OTHER, EVERYONE, MAN, WOMEN, false",
            "OTHER, EVERYONE, NONBINARY, NONBINARY, false",
            "NONBINARY, NONBINARY, NONBINARY, NONBINARY, true",
    })
    void gender(Gender ga, GenderPref wa, Gender gb, GenderPref wb, boolean ok) {
        Candidate a = base(1).gender(ga).wants(wa).build();
        Candidate b = base(2).gender(gb).wants(wb).build();
        if (ok) passes(a, b, in(a, b));
        else onlyRule(HardRule.GENDER, a, b, in(a, b));
    }

    @Test
    void genderWithSeveralPreferences() {
        Candidate a = base(1).gender(Gender.MAN).wants(GenderPref.WOMEN, GenderPref.NONBINARY).build();
        Candidate b = base(2).gender(Gender.NONBINARY).wants(GenderPref.MEN).build();
        passes(a, b, in(a, b));
    }

    @Test
    void ageRangeBothWaysInclusive() {
        Candidate a = base(1).birthday(WEEK.minusYears(20)).ageRange(20, 22).build();
        Candidate b22 = base(2).birthday(WEEK.minusYears(22)).ageRange(18, 20).build();
        passes(a, b22, in(a, b22));
        Candidate b23 = base(3).birthday(WEEK.minusYears(23)).ageRange(18, 30).build();
        onlyRule(HardRule.AGE, a, b23, in(a, b23));
        Candidate tooYoungForB = base(4).birthday(WEEK.minusYears(21)).ageRange(21, 30).build();
        onlyRule(HardRule.AGE, a, tooYoungForB, in(a, tooYoungForB));
    }

    @Test
    void heightBothWaysInclusive() {
        Candidate a = base(1).heightCm(160).heightPref(HeightPref.between(175, 190)).build();
        Candidate tall = base(2).heightCm(175).build();
        passes(a, tall, in(a, tall));
        Candidate shorter = base(3).heightCm(174).build();
        onlyRule(HardRule.HEIGHT, a, shorter, in(a, shorter));
        Candidate picky = base(4).heightCm(180).heightPref(HeightPref.between(165, 170)).build();
        onlyRule(HardRule.HEIGHT, a, picky, in(a, picky));
    }

    @Test
    void distanceUsesEachPersonsOwnBudget() {
        Candidate walker = base(1).location(TestPlaces.get("pku").at(TravelMode.WALK, 20)).build();
        Candidate far = base(2).location(TestPlaces.get("sanlitun").at(TravelMode.TRANSIT, 60)).build();
        onlyRule(HardRule.DISTANCE, walker, far, in(walker, far));
        Candidate driver = walker.toBuilder().location(TestPlaces.get("pku").at(TravelMode.DRIVE, 45)).build();
        passes(driver, far, in(driver, far));
        Candidate near = base(3).location(TestPlaces.get("tsinghua").at(TravelMode.WALK, 20)).build();
        passes(walker, near, in(walker, near));
    }

    @Test
    void neverMatchedAndReported() {
        Candidate a = base(1).build();
        Candidate b = base(2).build();
        MatchingInput past = in(a, b).withPastPairs(Set.of(PairKey.of(2, 1)));
        onlyRule(HardRule.NEVER_MATCHED, a, b, past);
        MatchingInput reported = new MatchingInput(WEEK, List.of(a, b), Set.of(), Set.of(PairKey.of(1, 2)), Weights.W1, 1, false);
        onlyRule(HardRule.REPORTED, a, b, reported);
    }

    @Test
    void blocklistEitherDirection() {
        Candidate a = base(1).blockedHashes(Set.of("h2")).build();
        Candidate b = base(2).build();
        onlyRule(HardRule.BLOCKLIST, a, b, in(a, b));
        Candidate c = base(3).build();
        passes(a, c, in(a, c));
    }

    @Test
    void educationOnlyWhenSuperImportant() {
        Candidate grad = base(1).education(EducationLevel.GRADUATE, Importance.SUPER).build();
        Candidate college = base(2).education(EducationLevel.COLLEGE, Importance.NONE).build();
        onlyRule(HardRule.EDUCATION, grad, college, in(grad, college));
        Candidate grad2 = base(3).education(EducationLevel.GRADUATE, Importance.NONE).build();
        passes(grad, grad2, in(grad, grad2));
        Candidate hidden = base(4).education(EducationLevel.PREFER_NOT, Importance.NONE).build();
        onlyRule(HardRule.EDUCATION, grad, hidden, in(grad, hidden));
        Candidate kinda = grad.toBuilder().education(EducationLevel.GRADUATE, Importance.KINDA).build();
        passes(kinda, college, in(kinda, college));
        Candidate chooserHidden = base(5).education(EducationLevel.PREFER_NOT, Importance.SUPER).build();
        passes(chooserHidden, college, in(chooserHidden, college));
        onlyRule(HardRule.EDUCATION, chooserHidden, hidden, in(chooserHidden, hidden));
    }

    @Test
    void gradeWindowOfThree() {
        Candidate freshman = base(1).schoolYear(SchoolYear.FRESHMAN).build();
        Candidate senior = base(2).schoolYear(SchoolYear.SENIOR).build();
        Candidate master = base(3).schoolYear(SchoolYear.MASTER).build();
        passes(freshman, senior, in(freshman, senior));
        onlyRule(HardRule.GRADE_WINDOW, freshman, master, in(freshman, master));
        Candidate other = base(4).schoolYear(SchoolYear.OTHER).build();
        Candidate worker = base(5).student(false).build();
        passes(freshman, other, in(freshman, other));
        onlyRule(HardRule.LIFE_STAGE, freshman, worker, in(freshman, worker));
    }

    @Test
    void aiConstraintsBothWays() {
        Candidate picky = base(1).ai(new AiConstraints(180, null, null, null, false, false)).build();
        Candidate shortB = base(2).heightCm(175).build();
        onlyRule(HardRule.AI_CONSTRAINTS, picky, shortB, in(picky, shortB));
        Candidate studentsOnly = base(3).ai(new AiConstraints(null, null, null, null, true, false)).build();
        Candidate worker = base(4).student(false).build();
        assertThat(HardFilter.violations(studentsOnly, worker, in(studentsOnly, worker)))
                .containsExactly(HardRule.AI_CONSTRAINTS, HardRule.LIFE_STAGE);
        assertThat(HardFilter.violations(worker, studentsOnly, in(worker, studentsOnly)))
                .containsExactly(HardRule.AI_CONSTRAINTS, HardRule.LIFE_STAGE);
        Candidate maxAge = base(5).ai(new AiConstraints(null, null, null, 19, false, false)).build();
        Candidate twenty = base(6).birthday(WEEK.minusYears(20)).build();
        onlyRule(HardRule.AI_CONSTRAINTS, maxAge, twenty, in(maxAge, twenty));
    }

    @Test
    void openMatchOnlyInManualRuns() {
        Candidate a = base(1).hasOpenMatch(true).build();
        Candidate b = base(2).build();
        passes(a, b, in(a, b));
        MatchingInput manual = new MatchingInput(WEEK, List.of(a, b), Set.of(), Set.of(), Weights.W1, 1, true);
        onlyRule(HardRule.OPEN_MATCH, a, b, manual);
    }

    @Test
    void listsEveryFailingRule() {
        Candidate a = base(1).gender(Gender.MAN).wants(GenderPref.MEN).heightPref(HeightPref.between(190, 200)).build();
        Candidate b = base(2).gender(Gender.WOMAN).wants(GenderPref.WOMEN).pool("sh").build();
        assertThat(HardFilter.violations(a, b, in(a, b))).containsExactly(HardRule.SAME_POOL, HardRule.GENDER, HardRule.HEIGHT);
    }
}
