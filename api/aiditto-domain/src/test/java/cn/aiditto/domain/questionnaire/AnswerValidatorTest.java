package cn.aiditto.domain.questionnaire;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerValidatorTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    static final AnswerValidator.Context CTX = new AnswerValidator.Context(TODAY, "wudaokou"::equals);

    static QuestionSpec spec(String id, QuestionType type) {
        return new QuestionSpec(id, type, Set.of(), null, null, Set.of(), false, null, false, false, null, Set.of());
    }

    static QuestionSpec choice(String id, QuestionType type, Set<String> options, Integer maxSelect, Set<String> exclusive, boolean custom) {
        return new QuestionSpec(id, type, options, maxSelect, null, exclusive, false, null, custom, false, null, Set.of());
    }

    static DomainException fails(QuestionSpec q, Object value) {
        try {
            AnswerValidator.validate(q, value, CTX);
        } catch (DomainException e) {
            return e;
        }
        throw new AssertionError("expected " + q.id() + " to reject " + value);
    }

    @Test
    void birthdayUnder18IsRejectedWithUnderAge() {
        QuestionSpec q = spec("birthday", QuestionType.DATE);
        DomainException e = fails(q, "2008-11-14");
        assertThat(e.code()).isEqualTo(ErrorCode.UNDER_AGE);
        assertThat(e.fields()).containsExactly("birthday");
        assertThat(AnswerValidator.validate(q, "2008-11-13", CTX)).isEqualTo("2008-11-13");
    }

    @Test
    void birthdayMustBeARealPastDate() {
        QuestionSpec q = spec("birthday", QuestionType.DATE);
        assertThat(fails(q, "2001-02-30").code()).isEqualTo(ErrorCode.VALIDATION);
        assertThat(fails(q, "2027-01-01").code()).isEqualTo(ErrorCode.VALIDATION);
        assertThat(fails(q, "1899-12-31").code()).isEqualTo(ErrorCode.VALIDATION);
        assertThat(fails(q, 20010101).code()).isEqualTo(ErrorCode.VALIDATION);
    }

    @Property(tries = 200)
    void anyBirthdayYoungerThan18IsRejected(@ForAll @IntRange(min = 0, max = 18 * 366) int daysAgo) {
        LocalDate d = TODAY.minusDays(daysAgo);
        QuestionSpec q = spec("birthday", QuestionType.DATE);
        boolean adult = !d.isAfter(TODAY.minusYears(18));
        if (adult) assertThat(AnswerValidator.validate(q, d.toString(), CTX)).isEqualTo(d.toString());
        else assertThat(fails(q, d.toString()).code()).isEqualTo(ErrorCode.UNDER_AGE);
    }

    @Test
    void singleAcceptsOptionsAndShortCustomText() {
        QuestionSpec q = choice("occupation", QuestionType.SINGLE, Set.of("a", "b"), null, Set.of(), true);
        assertThat(AnswerValidator.validate(q, "a", CTX)).isEqualTo("a");
        assertThat(AnswerValidator.validate(q, "custom:摄影师", CTX)).isEqualTo("custom:摄影师");
        fails(q, "c");
        fails(q, "custom:" + "x".repeat(41));
        fails(choice("g", QuestionType.SINGLE, Set.of("a"), null, Set.of(), false), "custom:x");
    }

    @Test
    void multiDedupesAndEnforcesMaxAndExclusive() {
        QuestionSpec q = choice("hobbies", QuestionType.MULTI, Set.of("a", "b", "c", "none"), 2, Set.of("none"), false);
        assertThat(AnswerValidator.validate(q, List.of("a", "a", "b"), CTX)).isEqualTo(List.of("a", "b"));
        fails(q, List.of("a", "b", "c"));
        fails(q, List.of("a", "none"));
        fails(q, List.of());
        assertThat(AnswerValidator.validate(q, List.of("none"), CTX)).isEqualTo(List.of("none"));
    }

    @Test
    void textIsTrimmedAndCapped() {
        QuestionSpec q = spec("name", QuestionType.TEXT);
        assertThat(AnswerValidator.validate(q, "  小明 ", CTX)).isEqualTo("小明");
        fails(q, "   ");
        fails(q, "字".repeat(AnswerValidator.TEXT_MAX_DEFAULT + 1));
    }

    @Test
    void rangesAndHeight() {
        assertThat(AnswerValidator.validate(spec("expectedAgeRange", QuestionType.AGE_RANGE), List.of(20, 26), CTX)).isEqualTo(List.of(20, 26));
        fails(spec("expectedAgeRange", QuestionType.AGE_RANGE), List.of(17, 26));
        fails(spec("expectedAgeRange", QuestionType.AGE_RANGE), List.of(30, 26));
        assertThat(AnswerValidator.validate(spec("height", QuestionType.HEIGHT), Map.of("cm", 172, "unit", "cm"), CTX))
                .isEqualTo(Map.of("cm", 172, "unit", "cm"));
        fails(spec("height", QuestionType.HEIGHT), Map.of("cm", 300, "unit", "cm"));
        assertThat(AnswerValidator.validate(spec("heightRange", QuestionType.HEIGHT_RANGE), Map.of("any", true), CTX)).isNotNull();
        fails(spec("heightRange", QuestionType.HEIGHT_RANGE), Map.of("any", false, "min", 190, "max", 160));
    }

    @Test
    void emailsAndLocation() {
        QuestionSpec school = new QuestionSpec("schoolEmail", QuestionType.EMAIL, Set.of(), null, null, Set.of(), false, "school", false, false, null, Set.of());
        assertThat(AnswerValidator.validate(school, " a@pku.edu.cn ", CTX)).isEqualTo("a@pku.edu.cn");
        assertThat(fails(school, "a@gmail.com").code()).isEqualTo(ErrorCode.INVALID_EMAIL);
        QuestionSpec loc = spec("location", QuestionType.LOCATION);
        assertThat(AnswerValidator.validate(loc, Map.of("placeId", "wudaokou", "mode", "drive", "minutes", 60), CTX))
                .isEqualTo(Map.of("placeId", "wudaokou", "mode", "drive", "minutes", 60));
        fails(loc, Map.of("placeId", "nowhere", "mode", "drive", "minutes", 60));
        fails(loc, Map.of("placeId", "wudaokou", "mode", "fly", "minutes", 60));
    }

    @Test
    void photosCodesAndInterstitialsAreNotWritable() {
        for (QuestionType t : List.of(QuestionType.PHOTOS, QuestionType.CODE, QuestionType.INTERSTITIAL)) {
            assertThatThrownBy(() -> AnswerValidator.validate(spec("x", t), "1", CTX)).isInstanceOf(DomainException.class);
        }
    }
}
