package cn.aiditto.relationship;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.domain.relationship.FreeTextPolicy;
import cn.aiditto.domain.relationship.ProfileLiveEval;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileLiveEvalRunnerTest {
    @Test
    void missingKeyStopsBeforeAnyModelCall() {
        assertThat(ProfileLiveEvalRunner.hashesMatch()).isTrue();
        assertThat(ProfileLiveEval.datasetHash()).isEqualTo(ProfileLiveEvalRunner.DATASET_HASH);
        assertThat(ProfileLiveEvalRunner.liveConfigured(Map.of())).isFalse();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = ProfileLiveEvalRunner.run(new String[] {"full"}, Map.of(), new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertThat(code).isEqualTo(2);
        assertThat(text).contains("NOT_RUN", "LLM_MODE=live", "LLM_PRIMARY_API_KEY", "LLM_PRIMARY_BASE_URL");
        assertThat(text).doesNotContain("precision=1", "Precision: 1");
    }

    @Test
    void zeroDenominatorIsNotReportedAsPerfect() {
        assertThat(ProfileLiveEvalRunner.ratio(0, 0)).isNull();
        assertThat(ProfileLiveEvalRunner.ratio(0, 4)).isEqualTo(0.0);
        assertThat(ProfileLiveEvalRunner.ratio(3, 4)).isEqualTo(0.75);
    }

    @Test
    void productionPathExportsNoneOfTheFrozenSentences() {
        int leaks = 0;
        int privateVisibleOnEvaluationPath = 0;
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) {
            if (ProfileLiveEvalRunner.production(sample).exportCanonical().contains(sample.text())) leaks++;
            if (ProfileLiveEvalRunner.privacyLane(sample)
                    && ProfileLiveEvalRunner.synthetic(sample).exportCanonical().contains(sample.text())) {
                privateVisibleOnEvaluationPath++;
            }
        }
        assertThat(leaks).isZero();
        assertThat(privateVisibleOnEvaluationPath).isEqualTo(6);
        assertThat(FreeTextPolicy.productionExportsFreeText()).isFalse();
    }

    @Test
    void closedPhraseRuleCannotAcceptEveryFrozenPositiveLabel() {
        assertThat(ProfileLiveEvalRunner.positiveLabels()).isEqualTo(6);
        assertThat(ProfileLiveEvalRunner.closedPhraseCeiling()).isEqualTo(4);
        assertThat(ProfileLiveEvalRunner.SMOKE_IDS).containsExactly("L09", "L06", "L25", "L48", "L55");
    }

    @Test
    void preflightNamesTheModelWithoutPrintingTheKey() {
        Map<String, String> env = Map.of(
                "LLM_MODE", "live",
                "LLM_PRIMARY_BASE_URL", "https://example.test/v1",
                "LLM_PRIMARY_API_KEY", "super-secret-value",
                "LLM_PRIMARY_MODEL", "LongCat-2.5");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = ProfileLiveEvalRunner.run(new String[] {"preflight"}, env,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertThat(code).isZero();
        assertThat(text).contains("mode=live", "baseHost=example.test", "apiKey=present",
                "model=LongCat-2.5", "fallback=disabled", ProfileLiveEvalRunner.DATASET_HASH,
                ProfileLiveEvalRunner.PROMPT_HASH, "database=not-connected", "samplesSent=0");
        assertThat(text).doesNotContain("super-secret-value");
    }

    @Test
    void fallbackConfigurationStopsBeforeACall() {
        Map<String, String> env = Map.of(
                "LLM_MODE", "live",
                "LLM_PRIMARY_BASE_URL", "https://example.test/v1",
                "LLM_PRIMARY_API_KEY", "super-secret-value",
                "LLM_PRIMARY_MODEL", "LongCat-2.5",
                "LLM_FALLBACK_API_KEY", "fallback-secret-value");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = ProfileLiveEvalRunner.run(new String[] {"smoke"}, env,
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertThat(code).isEqualTo(2);
        assertThat(text).contains("fallback");
        assertThat(text).doesNotContain("super-secret-value", "fallback-secret-value");
    }

    @Test
    void scoresStayEmptyWhenNothingWasPredicted() {
        ProfileLiveEvalRunner.SampleRecord empty = new ProfileLiveEvalRunner.SampleRecord(
                "L09", "semantic", true, "called", "LongCat-2.5", true,
                List.of(), List.of(), List.of(), List.of(), false, 10, 1, 1, "primary",
                "complex-chinese", "SELF|self.pace|slow", "{}", List.of(), 0, 0, 0, 0, 0, "", "1");
        ProfileLiveEvalRunner.GroupScore score = ProfileLiveEvalRunner.scoreGroup(List.of(empty), false);
        assertThat(score.precision()).isNull();
        assertThat(score.recall()).isEqualTo(0.0);
        assertThat(score.f1()).isNull();
        assertThat(ProfileLiveEvalRunner.redact("Bearer super-secret-value", "super-secret-value"))
                .doesNotContain("super-secret-value");
    }

    @Test
    void semanticRejectionIsNotCountedAsARawMiss() {
        assertThat(ProfileLiveEvalRunner.classifyFailure("called", true, "SELF|self.pace|slow",
                List.of("SELF|self.pace|slow"), List.of(),
                List.of("self.pace:SEMANTIC_SUPPORT_PENDING"), 0, 0, "")).isEqualTo("5");
        assertThat(ProfileLiveEvalRunner.classifyFailure("not-sent", false, "",
                List.of(), List.of(), List.of(), 0, 0, "")).isEqualTo("8");
        assertThat(ProfileLiveEvalRunner.classifyFailure("degraded", false, "",
                List.of(), List.of(), List.of(), 0, 0, "AUTH http 401")).isEqualTo("10");
    }

    @Test
    void privacySamplesAreNotSent() {
        ProfileLiveEval.Sample privateSample = ProfileLiveEval.samples().stream()
                .filter(sample -> "L61".equals(sample.id())).findFirst().orElseThrow();
        LlmGateway gateway = null;
        ProfileLiveEvalRunner.SampleRecord record = ProfileLiveEvalRunner.one(privateSample, gateway);
        assertThat(record.modelCalled()).isFalse();
        assertThat(record.lane()).isEqualTo("privacy-policy");
        assertThat(record.productionLeak()).isFalse();
        assertThatThrownBy(() -> ProfileLiveEvalRunner.one(
                ProfileLiveEvalRunner.smokeSamples().get(0), null))
                .isInstanceOf(NullPointerException.class);
    }
}
