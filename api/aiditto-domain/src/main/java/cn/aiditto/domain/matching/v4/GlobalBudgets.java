package cn.aiditto.domain.matching.v4;

/**
 * Caps and quality floors fixed before any matching run.
 * A run that crosses a cap is not reported as a global optimum.
 */
public final class GlobalBudgets {
    public static final int MAX_USERS = 10_000;
    public static final int MAX_SOLVER_EDGES = 50_000;
    public static final int MAX_DIAGNOSTIC_EDGES = 2_000;
    public static final long WALL_MS = 90_000L;
    public static final int SHORTLIST = 20;
    public static final int G3_MIN_FIT_CENTI = 8_000;
    public static final double G3_MIN_DIRECTION = 80.0;
    public static final double G3_MIN_COVERAGE = 0.80;
    public static final int FIT_CENTI_MAX = 10_000;
    public static final int FAIRNESS_CENTI_PER_WEEK = 100;
    public static final int FAIRNESS_WEEK_CAP = 4;
    public static final int FAIRNESS_EDGE_CAP = FAIRNESS_CENTI_PER_WEEK * FAIRNESS_WEEK_CAP;

    private GlobalBudgets() {}
}
