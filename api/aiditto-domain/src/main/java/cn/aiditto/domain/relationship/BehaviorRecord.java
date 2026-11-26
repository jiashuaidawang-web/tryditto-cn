package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.BehaviorKind;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;

import java.util.List;

public record BehaviorRecord(String featureCode, BehaviorKind kind, FeatureValue value, List<String> evidenceRefs,
                             FeatureStatus status) {
    public BehaviorRecord {
        evidenceRefs = List.copyOf(evidenceRefs);
        if (featureCode == null || kind == null || status == null) throw new IllegalArgumentException("behavior");
        if (status != FeatureStatus.CONTESTED && value == null) throw new IllegalArgumentException("behavior value");
    }
}
