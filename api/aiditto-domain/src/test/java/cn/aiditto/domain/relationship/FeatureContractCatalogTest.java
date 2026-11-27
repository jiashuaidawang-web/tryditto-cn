package cn.aiditto.domain.relationship;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureContractCatalogTest {
    @Test
    void contractListsOnlyModelExtractableFeatures() {
        List<FeatureContract> contracts = FeatureContractCatalog.modelExtractable(FeatureRegistry.v1());
        assertThat(contracts).extracting(FeatureContract::featureCode)
                .contains("self.pace", "partner.social.initiative", "behavior.scenario.signal")
                .doesNotContain("lifestyle.hobbies", "partner.age", "partner.gender", "occupation.text");
        FeatureContract pace = contracts.stream().filter(item -> "self.pace".equals(item.featureCode())).findFirst().orElseThrow();
        assertThat(pace.allowedSections()).containsExactly("SELF");
        assertThat(pace.allowedValues()).contains("slow", "steady", "fast");
        assertThat(pace.explicitExamples()).isNotEmpty();
        assertThat(pace.counterExamples()).isNotEmpty();
        String rendered = FeatureContractCatalog.render(contracts);
        assertThat(rendered).contains("featureCode=self.pace").doesNotContain("lifestyle.hobbies");
        assertThat(contracts).extracting(FeatureContract::featureCode).isSorted();
    }

    @Test
    void promptV1DigestStaysFrozenWhileLaterPromptsDiffer() {
        assertThat(ProfileExtractionPrompts.digest()).isEqualTo(
                "bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195");
        assertThat(ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERSION_V2))
                .isNotEqualTo(ProfileExtractionPrompts.digest());
        assertThat(ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERSION_V3))
                .isNotEqualTo(ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERSION_V2));
        assertThat(ProfileExtractionPrompts.systemV2()).contains("不要改写成契约里看起来接近的码");
        assertThat(ProfileExtractionPrompts.systemV3()).contains("USER_STATEMENT", "evidenceSpan");
        assertThat(ProfileExtractionPrompts.systemV4()).contains("knownFacts");
        assertThat(ProfileExtractionPrompts.verifySystem()).contains("SUPPORTED", "不能使用候选里的置信度");
    }
}
