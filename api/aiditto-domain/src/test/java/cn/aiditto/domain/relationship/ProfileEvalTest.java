package cn.aiditto.domain.relationship;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileEvalTest {
    @Test
    void labeledSetMeetsThresholdsThatWereFixedBeforeScoring() {
        List<ProfileEval.Sample> samples = ProfileEvalCases.all();
        assertThat(samples).hasSizeGreaterThanOrEqualTo(ProfileEvalThresholds.MIN_SAMPLES);
        assertThat(samples.stream().map(ProfileEval.Sample::split).filter("holdout"::equals).count())
                .isGreaterThanOrEqualTo(ProfileEvalThresholds.MIN_HOLDOUT);
        assertThat(samples.stream().map(ProfileEval.Sample::category).distinct()).contains(
                "complementary", "similar-preference", "one-way", "cold-start", "contradiction",
                "scenario", "retraction", "old-versus-new", "sensitive", "malicious",
                "ungrounded-persona", "bad-format");
        ProfileEval.Report report = ProfileEval.score(samples, FeatureRegistry.v1());
        assertThat(ProfileEvalThresholds.meets(report.dev())).isTrue();
        assertThat(ProfileEvalThresholds.meets(report.holdout())).isTrue();
        assertThat(report.dev().hardAccepted()).isZero();
        assertThat(report.holdout().sensitiveLeaks()).isZero();
        assertThat(report.dev().badFormatSuccessRate()).isZero();
    }
}
