package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;

import java.util.List;

public record ProfileFeature(String featureCode, FeatureValue value, List<String> evidenceRefs, FeatureStatus status) {
    public ProfileFeature {
        evidenceRefs = List.copyOf(evidenceRefs);
        if (featureCode == null || status == null) throw new IllegalArgumentException("feature");
        if (status != FeatureStatus.CONTESTED && value == null) throw new IllegalArgumentException("feature value");
    }
}
