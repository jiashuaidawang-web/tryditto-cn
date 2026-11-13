package cn.aiditto.domain.questionnaire;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingRulesTest {

    static Map<String, Object> student() {
        Map<String, Object> a = new HashMap<>();
        a.put("isStudent", "yes");
        a.put("name", "小明");
        a.put("gender", "man");
        a.put("expectedGender", List.of("woman"));
        a.put("birthday", "2004-05-01");
        a.put("hobbies", List.of("music"));
        a.put("location", Map.of("placeId", "wudaokou", "mode", "drive", "minutes", 60));
        a.put("relationshipLooking", List.of("serious"));
        a.put("matchingStyle", "balanced");
        a.put("schoolYear", "junior");
        a.put("schoolEmail", "x@pku.edu.cn");
        return a;
    }

    @Test
    void completeStudentNeedsNothing() {
        assertThat(OnboardingRules.missing(student(), 2, true)).isEmpty();
    }

    @Test
    void reportsPhotosAndUnverifiedEmail() {
        assertThat(OnboardingRules.missing(student(), 1, false)).containsExactly("photos", "emailVerify");
    }

    @Test
    void escapedStudentVerifiesPersonalEmailAndSkipsSchoolYear() {
        Map<String, Object> a = student();
        a.remove("schoolYear");
        a.put(QuestionCatalog.NO_SCHOOL_EMAIL, true);
        assertThat(OnboardingRules.missing(a, 2, true)).containsExactly("personalEmail");
        a.put("personalEmail", "me@example.com");
        assertThat(OnboardingRules.emailToVerify(a)).isEqualTo("me@example.com");
        assertThat(OnboardingRules.missing(a, 2, true)).isEmpty();
    }

    @Test
    void projectorUsesDefaultsAndKeepsOnlyPickedHobbyBranches() {
        QuestionCatalog catalog = new QuestionCatalog(List.of(
                new QuestionSpec("musicGenre", QuestionType.MULTI, Set.of("pop"), null, null, Set.of(), false, null, false, false, "music", Set.of()),
                new QuestionSpec("sportKind", QuestionType.MULTI, Set.of("run"), null, null, Set.of(), false, null, false, false, "sports", Set.of())));
        Map<String, Object> a = student();
        a.put("musicGenre", List.of("pop"));
        a.put("sportKind", List.of("run"));
        ProfileProjector.ProfileSnapshot s = ProfileProjector.project(a, catalog);
        assertThat(s.ageMin()).isEqualTo(18);
        assertThat(s.ageMax()).isEqualTo(24);
        assertThat(s.schoolDomain()).isEqualTo("pku.edu.cn");
        assertThat(s.hobbyDetails()).containsOnlyKeys("musicGenre");
        assertThat(s.placeId()).isEqualTo("wudaokou");
        assertThat(catalog.staleBranchKeys(List.of("music"), a.keySet())).containsExactly("sportKind");
    }

    @Test
    void aliasesMapDittoNamesToQuestionIds() {
        assertThat(FieldAliases.toQuestionId("hobbyRanking")).isEqualTo("hobbies");
        assertThat(FieldAliases.toQuestionId("ageRange")).isEqualTo("expectedAgeRange");
        assertThat(FieldAliases.toQuestionId("name")).isEqualTo("name");
    }
}
