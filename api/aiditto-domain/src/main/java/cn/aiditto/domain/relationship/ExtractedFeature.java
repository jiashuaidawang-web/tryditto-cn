package cn.aiditto.domain.relationship;

import java.util.List;

/**
 * A candidate from a model. It is not evidence and it is not a snapshot.
 * The server assigns identity, source type, scope, and status.
 */
public record ExtractedFeature(
        String section,
        String featureCode,
        String proposedValue,
        String targetOperator,
        String importance,
        List<String> evidenceRefs,
        double confidence,
        String reasonCode,
        String behaviorKind,
        boolean hardConstraint) {

    public ExtractedFeature {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
