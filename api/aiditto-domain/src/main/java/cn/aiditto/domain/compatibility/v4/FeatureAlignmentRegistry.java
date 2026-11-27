package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Version {@code align-1}. Mappings are explicit. A shared suffix is not a mapping,
 * and an unverified complement is not treated as the same dimension.
 */
public final class FeatureAlignmentRegistry {
    public static final String VERSION = "align-1";

    public record Hypothesis(String preferenceCode, String preferenceValue, String selfCode, String selfValue, String reason) {}

    private final FeatureRegistry features;
    private final Map<String, FeatureAlignment> byPreference;
    private final List<Hypothesis> hypotheses;

    private FeatureAlignmentRegistry(FeatureRegistry features, List<FeatureAlignment> alignments, List<Hypothesis> hypotheses) {
        this.features = features;
        this.hypotheses = List.copyOf(hypotheses);
        Map<String, FeatureAlignment> map = new java.util.TreeMap<>();
        for (FeatureAlignment alignment : alignments) {
            if (map.put(alignment.preferenceCode(), alignment) != null) {
                throw new IllegalArgumentException("duplicate alignment " + alignment.preferenceCode());
            }
            validate(features, alignment);
        }
        this.byPreference = Map.copyOf(map);
    }

    public static FeatureAlignmentRegistry v1() {
        FeatureRegistry features = FeatureRegistry.v1();
        return new FeatureAlignmentRegistry(features, List.of(
                align("partner.pace", "self.pace", List.of("slow", "steady", "fast"),
                        "相处节奏是登记过的相邻刻度：只允许相邻值部分满足。"),
                align("partner.communication", "self.communication", List.of(),
                        "安静、直接、高频不是同一把尺子，未登记部分满足。"),
                align("partner.social.initiative", "self.social.initiative", List.of("low", "steady", "high"),
                        "社交主动性是登记过的相邻刻度：只允许相邻值部分满足。"),
                align("partner.schedule", "self.schedule", List.of("early", "flexible", "late"),
                        "作息是登记过的相邻刻度：只允许相邻值部分满足。")
        ), List.of(new Hypothesis(
                "partner.support", "close", "self.communication", "frequent",
                "高频沟通没有被证明等于更多情感支持。这条假设不参与评分。")));
    }

    public String version() { return VERSION; }

    public String featureRegistryVersion() { return features.version(); }

    public FeatureRegistry features() { return features; }

    public FeatureAlignment find(String preferenceCode) { return byPreference.get(preferenceCode); }

    public List<FeatureAlignment> alignments() { return List.copyOf(byPreference.values()); }

    public List<Hypothesis> unverifiedHypotheses() { return hypotheses; }

    private static FeatureAlignment align(String preference, String self, List<String> ordinal, String note) {
        return new FeatureAlignment(preference, self, ordinal, note);
    }

    private static void validate(FeatureRegistry features, FeatureAlignment alignment) {
        FeatureDefinition preference = features.require(alignment.preferenceCode());
        FeatureDefinition self = features.require(alignment.selfCode());
        if (!preference.sections().contains(Section.PREFERENCE)) {
            throw new IllegalArgumentException(alignment.preferenceCode() + " is not a preference");
        }
        if (!self.sections().contains(Section.SELF)) {
            throw new IllegalArgumentException(alignment.selfCode() + " is not a self feature");
        }
        if (!alignment.ordinal().isEmpty()) {
            if (!preference.allowedValues().equals(Set.copyOf(alignment.ordinal()))
                    || !self.allowedValues().equals(Set.copyOf(alignment.ordinal()))) {
                throw new IllegalArgumentException(alignment.preferenceCode() + " ordinal does not match the registry");
            }
        }
    }
}
