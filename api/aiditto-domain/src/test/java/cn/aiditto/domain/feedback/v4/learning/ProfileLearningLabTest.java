package cn.aiditto.domain.feedback.v4.learning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileLearningLabTest {
    @Test
    void tenFictionalCasesShowBehaviorWithoutClaimingABetterMatch() {
        List<ProfileLearningLab.LabCase> cases = ProfileLearningLab.cases();
        assertThat(cases).hasSize(10);
        assertThat(cases).allMatch(item -> item.safetyViolations() == 0);
        assertThat(cases.get(0).profileChanged()).isTrue();
        assertThat(cases.get(1).scoreBefore()).isNotEqualTo(cases.get(1).scoreAfter());
        assertThat(cases.get(2).scoreBefore()).isEqualTo(cases.get(2).scoreAfter());
        assertThat(cases.get(5).scoreBefore()).isEqualTo(cases.get(5).scoreAfter());
        assertThat(cases.get(6).proposal()).isEqualTo("NONE");
        assertThat(cases.get(8).proposal()).isEqualTo("REJECTED");
        assertThat(cases.get(8).scoreBefore()).isEqualTo(cases.get(8).scoreAfter());
        assertThat(ProfileLearningLab.json()).contains("\"id\":\"L01\"").contains("\"id\":\"L10\"");
    }
}
