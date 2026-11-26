package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Polarity;

import java.util.List;

/** Contract for a future observation. P01 snapshots keep the list empty. */
public record MemoryObservation(String observationId, String featureCode, String sourceEventRef, String observationType,
                                Polarity polarity, double confidence, List<String> evidenceRefs, FeatureStatus status) {
    public MemoryObservation {
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("memory confidence");
        evidenceRefs = List.copyOf(evidenceRefs);
    }
}
