package cn.aiditto.domain.compatibility.v4;

import java.util.List;

/**
 * One decision for later stages. The numbers stay on {@link PairCompatibilityResult}.
 * This object says whether those numbers may recommend or may be sent to offline reasoning.
 */
public record CompatibilityDecision(
        PairCompatibilityResult pair,
        boolean recommendationEligible,
        boolean reasoningEligible,
        List<String> reasons,
        String formulaVersion,
        String policyVersion) {

    public CompatibilityDecision {
        reasons = List.copyOf(reasons);
        if (pair == null || formulaVersion == null || policyVersion == null) throw new IllegalArgumentException("decision");
    }

    public static CompatibilityDecision from(PairCompatibilityResult pair) {
        return new CompatibilityDecision(pair,
                CompatibilityPolicies.recommendationEligible(pair),
                CompatibilityPolicies.reasoningEligible(pair),
                CompatibilityPolicies.reasons(pair),
                pair.formulaVersion(),
                CompatibilityPolicies.VERSION);
    }
}
