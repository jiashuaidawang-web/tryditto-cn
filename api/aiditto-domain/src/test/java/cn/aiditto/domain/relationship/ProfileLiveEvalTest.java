package cn.aiditto.domain.relationship;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileLiveEvalTest {
    private static final Set<String> CATEGORIES = Set.of(
            "self-preference-confusion", "complex-chinese", "negation-condition-hypothesis", "quoted-other",
            "contradiction", "scenario-wording", "cold-start-unknown", "irrelevant-evidence",
            "persona-inducement", "prompt-injection", "private-boundary");

    @Test
    void labelsAndHashAreFrozenBeforeAnyModelRun() {
        var samples = ProfileLiveEval.samples();
        assertThat(samples).hasSizeBetween(60, 100);
        assertThat(samples.stream().map(ProfileLiveEval.Sample::category).collect(Collectors.toSet())).isEqualTo(CATEGORIES);
        assertThat(samples.stream().map(ProfileLiveEval.Sample::text).distinct()).hasSize(samples.size());
        assertThat(ProfileLiveEval.datasetHash()).isEqualTo("4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c");
        ProfileLiveEval.Run run = ProfileLiveEval.notRun();
        assertThat(run.status()).isEqualTo("NOT_RUN");
        assertThat(run.blocker()).contains("mock", "LLM_PRIMARY_API_KEY");
        assertThat(run.datasetVersion()).isEqualTo(ProfileLiveEval.DATASET_VERSION);
        assertThat(run.datasetHash()).isEqualTo(ProfileLiveEval.datasetHash());
        assertThat(run.promptVersion()).isEqualTo(ProfileExtractionPrompts.VERSION);
        assertThat(run.promptHash()).isEqualTo(ProfileExtractionPrompts.digest());
        assertThat(run.provider()).isEqualTo("NOT_RUN");
        assertThat(run.model()).isEqualTo("NOT_RUN");
        assertThat(run.rawPrecision()).isEqualTo("NOT_RUN");
        assertThat(run.validatedPrecision()).isEqualTo("NOT_RUN");
        assertThat(run.rawRecall()).isEqualTo("NOT_RUN");
        assertThat(run.validatedRecall()).isEqualTo("NOT_RUN");
        assertThat(run.illegalHardAccepted()).isEqualTo("NOT_RUN");
        assertThat(run.sensitiveLeaks()).isEqualTo("NOT_RUN");
        assertThat(run.cost()).isEqualTo("NOT_RUN");
    }
}
