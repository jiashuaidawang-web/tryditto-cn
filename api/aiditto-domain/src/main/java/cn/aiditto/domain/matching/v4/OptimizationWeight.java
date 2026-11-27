package cn.aiditto.domain.matching.v4;

/**
 * Turns a 0–100 fit into the integer the solver sees.
 * Fit and optimization weight stay separate. The weight never adds an AI score,
 * an RRF score, a user id, or a learned feedback term.
 */
public final class OptimizationWeight {
    public static final String RELATION = "fitCenti = round(harmonicWithCoverage * 100), limited to 0..10000. "
            + "G0 and G1 optimize fitCenti. G2 and G3 add cardinalityBase = maxPairs * 10000 + 1 "
            + "to every admitted edge, so one extra pair outweighs any fit difference inside a smaller matching. "
            + "G4 adds g4Base = (maxPairs - 1) * 10400 + 1, then fitCenti, then at most 400 fairness centi. "
            + "On an equal number of pairs, fairness can outrank a smaller fit gap. "
            + "Across different numbers of pairs, the fairness sum cannot outrank one extra pair. "
            + "The encoded edge and the encoded matching both stay below 2^52, which a double represents exactly.";

    private OptimizationWeight() {}

    public static int fitCenti(double fit) {
        if (Double.isNaN(fit) || fit <= 0.0 || fit > 100.0) return -1;
        long rounded = Math.round(fit * 100.0);
        if (rounded <= 0 || rounded > GlobalBudgets.FIT_CENTI_MAX) return -1;
        return (int) rounded;
    }

    public static long cardinalityBase(int users) {
        return (long) (users / 2) * GlobalBudgets.FIT_CENTI_MAX + 1L;
    }

    public static long g4SecondaryMax() {
        return (long) GlobalBudgets.FIT_CENTI_MAX + GlobalBudgets.FAIRNESS_EDGE_CAP;
    }

    /** One extra pair stays ahead of any fit-plus-fairness total on a smaller matching. */
    public static long g4Base(int users) {
        int pairs = Math.max(1, users / 2);
        return (long) (pairs - 1) * g4SecondaryMax() + 1L;
    }

    public static boolean encodingFits(int users) {
        if (users < 0 || users > GlobalBudgets.MAX_USERS) return false;
        int pairs = Math.max(1, users / 2);
        long g2Edge = cardinalityBase(users) + GlobalBudgets.FIT_CENTI_MAX;
        long g4Edge = g4Base(users) + g4SecondaryMax();
        long total = Math.max(g2Edge, g4Edge) * pairs;
        return g2Edge > 0 && g4Edge > 0 && total < (1L << 52);
    }

    public static int fairnessBonus(int weeksLow, int weeksHigh) {
        int low = Math.max(0, Math.min(GlobalBudgets.FAIRNESS_WEEK_CAP, weeksLow));
        int high = Math.max(0, Math.min(GlobalBudgets.FAIRNESS_WEEK_CAP, weeksHigh));
        return Math.min(GlobalBudgets.FAIRNESS_EDGE_CAP, (low + high) * GlobalBudgets.FAIRNESS_CENTI_PER_WEEK);
    }

    public static long edgeWeight(Objective objective, int users, int fitCenti, int fairnessCenti) {
        if (fitCenti <= 0) return 0L;
        long fit = fitCenti;
        return switch (objective) {
            case G0_GREEDY, G1_MAX_WEIGHT -> fit;
            case G2_CARDINALITY_THEN_WEIGHT, G3_QUALITY_TIER -> cardinalityBase(users) + fit;
            case G4_FAIRNESS -> g4Base(users) + fit + Math.max(0, fairnessCenti);
        };
    }

    public static String components(Objective objective, int fitCenti, int fairnessCenti, long weight) {
        return "objective=" + objective + " fitCenti=" + fitCenti
                + " fairnessCenti=" + (objective == Objective.G4_FAIRNESS ? fairnessCenti : 0)
                + " optimizationWeight=" + weight;
    }
}
