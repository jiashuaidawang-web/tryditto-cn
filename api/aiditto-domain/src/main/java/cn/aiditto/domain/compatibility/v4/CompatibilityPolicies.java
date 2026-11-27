package cn.aiditto.domain.compatibility.v4;

import java.util.ArrayList;
import java.util.List;

/**
 * The only place that applies the frozen B5 gates. Later stages call this instead of restating 60 and 0.60.
 * Reasoning eligibility is not recommendation eligibility. Neither one bypasses authorization or a hard constraint.
 */
public final class CompatibilityPolicies {
    public static final String VERSION = "compat-decision-1";

    private CompatibilityPolicies() {}

    public static boolean recommendationEligible(PairCompatibilityResult pair) {
        if (!safetyClear(pair)) return false;
        if (pair.status() != CompatibilityStatus.SCORED) return false;
        if (pair.harmonicWithCoverage() == null) return false;
        Double lowest = pair.minDirection();
        Double coverageA = pair.coverageAB();
        Double coverageB = pair.coverageBA();
        if (lowest == null || coverageA == null || coverageB == null) return false;
        if (lowest < CompatibilityFormula.MIN_DIRECTION_SCORE) return false;
        if (coverageA < CompatibilityFormula.MIN_COVERAGE || coverageB < CompatibilityFormula.MIN_COVERAGE) return false;
        if (contested(pair) || strongPreferenceUnevaluable(pair)) return false;
        return true;
    }

    /**
     * Offline reasoning may study missing evidence and contradictions.
     * It may not run when the pair is unauthorized, stale, non-synthetic, or hard-blocked,
     * because those paths must not expose profile content.
     */
    public static boolean reasoningEligible(PairCompatibilityResult pair) {
        return safetyClear(pair);
    }

    public static List<String> reasons(PairCompatibilityResult pair) {
        List<String> reasons = new ArrayList<>();
        if (pair.hardFilterResult().stream().anyMatch(code -> code.startsWith("HARD_"))) reasons.add("HARD_CONSTRAINT");
        if (pair.hardFilterResult().stream().anyMatch(code -> code.startsWith("POOL_") || code.startsWith("CONSENT_") || code.equals("ACCOUNT_MISSING") || code.equals("NOT_SYNTHETIC"))) {
            reasons.add("NOT_AUTHORIZED");
        }
        if (pair.hardFilterResult().stream().anyMatch(code -> code.equals("PROFILE_STALE") || code.equals("PROFILE_WITHDRAWN"))) {
            reasons.add("NOT_CURRENT");
        }
        if (!safetyClear(pair)) {
            reasons.add("REASONING_REFUSED");
            reasons.add("RECOMMENDATION_REFUSED");
            return List.copyOf(reasons);
        }
        reasons.add("REASONING_ALLOWED");
        if (pair.scoreAB() == null || pair.scoreBA() == null) reasons.add("SCORE_UNDEFINED");
        Double lowest = pair.minDirection();
        if (lowest != null && lowest < CompatibilityFormula.MIN_DIRECTION_SCORE) reasons.add("BELOW_MIN_DIRECTION");
        if (belowCoverage(pair.coverageAB()) || belowCoverage(pair.coverageBA())) reasons.add("BELOW_MIN_COVERAGE");
        if (contested(pair)) reasons.add("CONTESTED_PRESENT");
        if (strongPreferenceUnevaluable(pair)) reasons.add("STRONG_PREFERENCE_UNEVALUABLE");
        if (recommendationEligible(pair)) reasons.add("RECOMMEND_B5");
        else reasons.add("RECOMMENDATION_REFUSED");
        return List.copyOf(reasons);
    }

    private static boolean safetyClear(PairCompatibilityResult pair) {
        if (pair.status() == CompatibilityStatus.NOT_CURRENT || pair.status() == CompatibilityStatus.NOT_ELIGIBLE) return false;
        if (pair.status() == CompatibilityStatus.HARD_VETO) return false;
        return pair.hardFilterResult().isEmpty();
    }

    private static boolean belowCoverage(Double coverage) {
        return coverage == null || coverage < CompatibilityFormula.MIN_COVERAGE;
    }

    static boolean contested(PairCompatibilityResult pair) {
        return pair.evidenceContributions().stream().anyMatch(row -> row.satisfaction() == Satisfaction.CONTESTED);
    }

    static boolean strongPreferenceUnevaluable(PairCompatibilityResult pair) {
        return pair.evidenceContributions().stream().anyMatch(row -> "SUPER".equals(row.importance())
                && (row.satisfaction() == Satisfaction.UNKNOWN || row.satisfaction() == Satisfaction.CONTESTED));
    }
}
