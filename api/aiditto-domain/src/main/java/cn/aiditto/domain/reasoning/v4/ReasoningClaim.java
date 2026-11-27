package cn.aiditto.domain.reasoning.v4;

import java.util.List;

/** One claim bound to a preference, the other person's feature, and evidence that was actually supplied. */
public record ReasoningClaim(
        ReasoningCategory category,
        String preferenceCode,
        String targetFeatureCode,
        List<String> evidenceRefs,
        String statement) {

    public ReasoningClaim {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        if (category == null || preferenceCode == null || targetFeatureCode == null || statement == null) {
            throw new IllegalArgumentException("claim");
        }
    }
}
