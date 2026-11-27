package cn.aiditto.domain.compatibility.v4;

/**
 * Frozen experiment settings for compat-1. These are not product rules.
 * A fit score is not a like probability, an accept probability, or a relationship success rate.
 */
public final class CompatibilityFormula {
    public static final String VERSION = "compat-1";
    public static final int SUPER = 3;
    public static final int KINDA = 2;
    public static final int NONE = 1;
    public static final double MIN_DIRECTION_SCORE = 60.0;
    public static final double MIN_COVERAGE = 0.60;
    public static final double MIN_STRONG_RATE = 0.60;

    private CompatibilityFormula() {}
}
