package cn.aiditto.domain.relationship;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;
import cn.aiditto.domain.questionnaire.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingFieldMapTest {
    @Test
    void storedQuestionsAreMappedAndEverythingElseStaysUnmapped() {
        assertThat(OnboardingFieldMap.mappedCount()).isEqualTo(RelationshipProfileProjector.STORED_QUESTIONS.size());
        QuestionCatalog catalog = new QuestionCatalog(List.of(
                spec("hobbies"),
                spec("religion"),
                spec("dealbreakers")));
        List<OnboardingFieldMap.Row> rows = OnboardingFieldMap.cover(catalog);
        assertThat(rows).extracting(OnboardingFieldMap.Row::questionCode)
                .containsExactly("hobbies", "religion", "dealbreakers");
        assertThat(rows.get(0).support()).isEqualTo("MAPPED");
        assertThat(rows.get(0).featureCode()).isEqualTo("lifestyle.hobbies");
        assertThat(rows.get(1).support()).isEqualTo("UNMAPPED");
        assertThat(rows.get(1).featureCode()).isEmpty();
        assertThat(rows.get(1).matchUse()).isFalse();
        assertThat(rows.get(2).sensitive()).isTrue();
    }

    @Test
    void missingAnswersAndSelfTraitsDoNotBecomePartnerPreferences() {
        RelationshipProfileSnapshot snapshot = RelationshipProfileProjector.project(7,
                Map.of("hobbies", List.of("reading"), "educationLevel", "preferNot"), FeatureRegistry.v1());
        assertThat(snapshot.selfJson()).contains("reading");
        assertThat(snapshot.preferenceJson()).doesNotContain("reading", "partner.age", "partner.gender");
        assertThat(snapshot.evidenceJson()).contains("WITHHELD");
        assertThat(snapshot.memoryJson()).isEqualTo("[]");
        assertThat(snapshot.behaviorJson()).isEqualTo("[]");
    }

    private static QuestionSpec spec(String id) {
        return new QuestionSpec(id, QuestionType.TEXT, Set.of(), null, null, Set.of(), false, null, false,
                QuestionCatalog.SENSITIVE.contains(id), null, Set.of());
    }
}
