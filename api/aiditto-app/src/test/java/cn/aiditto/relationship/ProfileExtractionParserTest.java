package cn.aiditto.relationship;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileExtractionParserTest {
    @Test
    void acceptsOnlyAFeatureArray() {
        ProfileExtractionParser.Parse parsed = ProfileExtractionParser.parse("""
                {"features":[{"section":"SELF","featureCode":"self.pace","proposedValue":"slow",
                "targetOperator":"EQ","importance":"NONE","evidenceRefs":["ev-1"],"confidence":0.6,
                "reasonCode":"STATED","behaviorKind":"","hardConstraint":false}]}
                """);
        assertThat(parsed.structured()).isTrue();
        assertThat(parsed.features()).hasSize(1);
        assertThat(parsed.features().get(0).featureCode()).isEqualTo("self.pace");
        assertThat(parsed.features().get(0).evidenceRefs()).containsExactly("ev-1");
        assertThat(parsed.features().get(0).confidence()).isEqualTo(0.6);
        assertThat(parsed.features().get(0).hardConstraint()).isFalse();
    }

    @Test
    void stripsAFenceAndRejectsMissingOrBrokenJson() {
        ProfileExtractionParser.Parse fenced = ProfileExtractionParser.parse("""
                ```json
                {"features":[]}
                ```
                """);
        assertThat(fenced.structured()).isTrue();
        assertThat(fenced.features()).isEmpty();
        assertThat(ProfileExtractionParser.parse("not json").structured()).isFalse();
        assertThat(ProfileExtractionParser.parse("{\"mock\":true}").structured()).isFalse();
        assertThat(ProfileExtractionParser.parse("").structured()).isFalse();
        assertThat(ProfileExtractionParser.parse(null).structured()).isFalse();
    }

    @Test
    void readsTheTaskShapeAndDoesNotRewriteCodes() {
        ProfileExtractionParser.TaskParse parsed = ProfileExtractionParser.parseTask("""
                {"features":[{"evidenceRef":"ev-1","evidenceSpan":"原文","proposedSection":"SELF",
                "proposedFeatureCode":"not.a.real.code","proposedValue":"slow","hardConstraint":false}]}
                """);
        assertThat(parsed.structured()).isTrue();
        assertThat(parsed.cited()).hasSize(1);
        assertThat(parsed.cited().get(0).feature().featureCode()).isEqualTo("not.a.real.code");
        assertThat(parsed.cited().get(0).evidenceSpan()).isEqualTo("原文");
        assertThat(ProfileExtractionParser.verdict("{\"verdict\":\"MAYBE\"}")).isEmpty();
        assertThat(ProfileExtractionParser.verdict("{\"verdict\":\"SUPPORTED\"}")).isEqualTo("SUPPORTED");
    }
}
