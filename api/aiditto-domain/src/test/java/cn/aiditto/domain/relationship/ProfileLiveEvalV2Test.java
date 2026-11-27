package cn.aiditto.domain.relationship;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileLiveEvalV2Test {
    @Test
    void datasetIsFrozenAndIndependentOfTheFirstLiveSet() {
        List<ProfileLiveEvalV2.Sample> samples = ProfileLiveEvalV2.samples();
        assertThat(samples).hasSize(200);
        assertThat(ProfileLiveEvalV2.development()).hasSize(120);
        assertThat(ProfileLiveEvalV2.holdout()).hasSize(80);
        assertThat(ProfileLiveEvalV2.datasetHash()).hasSize(64);
        Set<String> v1 = new HashSet<>();
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) v1.add(sample.text());
        Set<String> seen = new HashSet<>();
        FeatureRegistry registry = FeatureRegistry.v1();
        for (ProfileLiveEvalV2.Sample sample : samples) {
            assertThat(seen.add(sample.text())).isTrue();
            assertThat(v1).doesNotContain(sample.text());
            assertThat(FreeTextPolicy.evaluationMayExpose(sample.text())).isTrue();
            if ("EXTRACT".equals(sample.disposition())) assertThat(sample.expectedKeys()).isNotEmpty();
            if ("WITHHOLD".equals(sample.disposition()) || "UNKNOWN".equals(sample.disposition())
                    || "CONTRADICTION".equals(sample.disposition()) || "UNEXPRESSIBLE".equals(sample.disposition())) {
                assertThat(sample.expectedKeys()).isEmpty();
            }
            for (String key : sample.expectedKeys()) {
                String[] parts = key.split("\\|", -1);
                assertThat(parts).hasSize(3);
                assertThat(registry.require(parts[1]).allowedValues()).contains(parts[2]);
            }
        }
    }

    @Test
    void unifiedStatementKeepsSectionChecksAndRequiresARealSpan() {
        String text = "我比较慢热，但希望另一半主动分享生活";
        ProfileContext context = ProfileContextBuilder.buildEvaluation(new ProfileContextBuilder.Inputs(
                91_001L, 1, 1, true, true, java.util.Map.of(),
                List.of(new ProfileContextBuilder.Statement("USER", text)), List.of(), 1L));
        assertThat(context.exportCanonical()).contains("USER_STATEMENT").contains(text);
        assertThat(ProfileContextBuilder.build(new ProfileContextBuilder.Inputs(
                91_001L, 1, 1, true, true, java.util.Map.of(),
                List.of(new ProfileContextBuilder.Statement("USER", text)), List.of(), 1L))
                .exportCanonical()).doesNotContain(text);
        String ref = context.items().get(0).token();
        CitedFeature slow = cited(ref, "我比较慢热", "SELF", "self.pace", "slow");
        ProfileExtractionValidator.Result pending = ProfileExtractionValidator.checkCited(context, List.of(slow), FeatureRegistry.v1());
        assertThat(pending.accepted()).isEmpty();
        assertThat(pending.verdicts().get(0).support()).isEqualTo(SemanticSupport.SEMANTIC_SUPPORT_PENDING);
        CitedFeature exact = cited(ref, "我话比较少", "SELF", "self.communication", "quiet");
        assertThat(ProfileExtractionValidator.checkCited(context, List.of(exact), FeatureRegistry.v1()).rejected())
                .anyMatch(item -> "span".equals(item.reason()));
        ProfileContext closed = ProfileContextBuilder.buildEvaluation(new ProfileContextBuilder.Inputs(
                91_001L, 1, 1, true, true, java.util.Map.of(),
                List.of(new ProfileContextBuilder.Statement("USER", "我话比较少")), List.of(), 1L));
        CitedFeature supported = cited(closed.items().get(0).token(), "我话比较少", "SELF", "self.communication", "quiet");
        assertThat(ProfileExtractionValidator.checkCited(closed, List.of(supported), FeatureRegistry.v1()).accepted()).isNotEmpty();
        ProfileContext hypothetical = ProfileContextBuilder.buildEvaluation(new ProfileContextBuilder.Inputs(
                91_001L, 1, 1, true, true, java.util.Map.of(),
                List.of(new ProfileContextBuilder.Statement("USER", "如果我比较慢热，见面可能会冷")), List.of(), 1L));
        ExtractedFeature feature = new ExtractedFeature("SELF", "self.pace", "slow", "EQ", "NONE",
                List.of(hypothetical.items().get(0).token()), 0.0, "STATED", "", false);
        assertThat(ProfileExtractionValidator.checkCited(hypothetical, List.of(new CitedFeature(feature, "我比较慢热")),
                FeatureRegistry.v1()).verdicts().get(0).support()).isEqualTo(SemanticSupport.REJECTED);
    }

    @Test
    void taskPayloadCanOmitDeterministicFacts() {
        ProfileContext context = ProfileContextBuilder.buildEvaluation(new ProfileContextBuilder.Inputs(
                91_001L, 1, 1, true, true, java.util.Map.of("hobbies", List.of("reading")),
                List.of(new ProfileContextBuilder.Statement("USER", "我话比较少")), List.of(), 1L));
        String hidden = ExtractionTask.payload(context, FeatureContractCatalog.modelExtractable(FeatureRegistry.v1()), false);
        assertThat(hidden).contains("\"knownFacts\":[]", "self.pace", "我话比较少").doesNotContain("reading");
        String shown = ExtractionTask.payload(context, FeatureContractCatalog.modelExtractable(FeatureRegistry.v1()), true);
        assertThat(shown).contains("reading");
    }

    private static CitedFeature cited(String ref, String span, String section, String code, String value) {
        return new CitedFeature(new ExtractedFeature(section, code, value, "EQ", "NONE",
                List.of(ref), 0.0, "STATED", "", false), span);
    }
}
