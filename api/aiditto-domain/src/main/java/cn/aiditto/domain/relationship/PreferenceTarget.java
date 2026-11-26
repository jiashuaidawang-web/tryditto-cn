package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;

import java.util.List;

public record PreferenceTarget(String featureCode, Operator operator, FeatureValue targetValue, Importance importance,
                               ConstraintType constraintType, Double confidence, List<String> evidenceRefs,
                               FeatureStatus status) {
    public PreferenceTarget {
        evidenceRefs = List.copyOf(evidenceRefs);
        if (featureCode == null || status == null || constraintType == null) throw new IllegalArgumentException("preference");
        if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException("preference confidence");
        }
        if (status != FeatureStatus.CONTESTED && (operator == null || targetValue == null || importance == null || confidence == null)) {
            throw new IllegalArgumentException("preference value");
        }
    }
}
