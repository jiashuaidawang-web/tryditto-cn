package cn.aiditto.domain.matching.v4;

/**
 * Experiment objectives. G2 is the pre-registered choice: eligibility already applied the
 * quality floor, then more matched people outrank a higher total fit, then fit breaks ties.
 * G3 and G4 are comparisons. They are not selected as the default.
 */
public enum Objective {
    G0_GREEDY,
    G1_MAX_WEIGHT,
    G2_CARDINALITY_THEN_WEIGHT,
    G3_QUALITY_TIER,
    G4_FAIRNESS;

    public static final Objective SELECTED = G2_CARDINALITY_THEN_WEIGHT;
    public static final String SELECTED_VERSION = "global-objective-g2-1";
}
