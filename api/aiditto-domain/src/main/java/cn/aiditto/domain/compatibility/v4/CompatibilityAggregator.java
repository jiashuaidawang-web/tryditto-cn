package cn.aiditto.domain.compatibility.v4;

/**
 * The five frozen aggregates. A missing direction stays missing.
 * Zero is a real score. It is not used as a stand-in for unknown.
 */
public final class CompatibilityAggregator {
    private CompatibilityAggregator() {}

    public static Double arithmetic(Double left, Double right) {
        if (left == null || right == null) return null;
        return (left + right) / 2.0;
    }

    public static Double harmonic(Double left, Double right) {
        if (left == null || right == null) return null;
        if (left == 0.0 || right == 0.0) return 0.0;
        return 2.0 * left * right / (left + right);
    }

    public static Double lowest(Double left, Double right) {
        if (left == null || right == null) return null;
        return Math.min(left, right);
    }

    public static Double geometric(Double left, Double right) {
        if (left == null || right == null) return null;
        if (left == 0.0 || right == 0.0) return 0.0;
        return Math.sqrt(left * right);
    }

    public static Double harmonicWithCoverage(Double left, Double right, Double coverageLeft, Double coverageRight) {
        if (left == null || right == null || coverageLeft == null || coverageRight == null) return null;
        if (coverageLeft < CompatibilityFormula.MIN_COVERAGE || coverageRight < CompatibilityFormula.MIN_COVERAGE) return null;
        return harmonic(left, right);
    }

    public static Double harmonicWithCoverageAndStrength(Double left, Double right, Double coverageLeft, Double coverageRight,
                                                         Double strongLeft, Double strongRight) {
        Double gated = harmonicWithCoverage(left, right, coverageLeft, coverageRight);
        if (gated == null) return null;
        if (strongLeft != null && strongLeft < CompatibilityFormula.MIN_STRONG_RATE) return null;
        if (strongRight != null && strongRight < CompatibilityFormula.MIN_STRONG_RATE) return null;
        return gated;
    }
}
