package cn.aiditto.domain.matching;

import java.util.EnumMap;
import java.util.Map;

/**
 * Scoring weights and thresholds (技术方案 §7.3). Changing any value means a new {@link #version()} and a
 * re-run of the synthetic population report; {@code WeightsTest} pins every component.
 */
public record Weights(
        String version,
        int hobbyMax,
        int hobbyDetailBonus,
        int lookingMax,
        int lookingConflictPenalty,
        int styleSame,
        int styleAdjacent,
        int styleOther,
        int identityStudentsClose,
        int identityStudentsTwoApart,
        int identityStudentsFar,
        int identityStudentUnknownYear,
        int identityBothWorking,
        int identityMixed,
        int budgetMatch,
        int budgetAdjacent,
        int ageMax,
        int distanceMax,
        int fairnessPerEmptyWeek,
        int fairnessCap,
        Map<MatchingStyle, Integer> thresholds,
        int gradeWindow,
        double walkKmh,
        double transitKmh,
        double driveKmh,
        double detourFactor) {

    public static final Weights W1 = new Weights(
            "w1",
            30, 2,
            20, 10,
            10, 6, 2,
            10, 5, 2, 5, 8, 3,
            10, 6,
            10,
            10,
            3, 9,
            thresholds(35, 45, 55, 65),
            3,
            4.5, 18, 25, 1.3);

    public Weights {
        thresholds = Map.copyOf(thresholds);
        for (MatchingStyle s : MatchingStyle.values()) {
            if (!thresholds.containsKey(s)) throw new IllegalArgumentException("missing threshold for " + s);
        }
    }

    private static Map<MatchingStyle, Integer> thresholds(int faster, int steady, int fewer, int theOne) {
        Map<MatchingStyle, Integer> m = new EnumMap<>(MatchingStyle.class);
        m.put(MatchingStyle.FASTER, faster);
        m.put(MatchingStyle.STEADY, steady);
        m.put(MatchingStyle.FEWER, fewer);
        m.put(MatchingStyle.THE_ONE, theOne);
        return m;
    }

    public int threshold(MatchingStyle style) {
        return thresholds.get(style);
    }

    /** The pair's minimum score: the stricter of the two people's styles. */
    public int pairThreshold(Candidate a, Candidate b) {
        return Math.max(threshold(a.style()), threshold(b.style()));
    }

    public double speedKmh(TravelMode mode) {
        return switch (mode) {
            case WALK -> walkKmh;
            case TRANSIT -> transitKmh;
            case DRIVE -> driveKmh;
        };
    }

    public int fairnessBonus(Candidate c) {
        return Math.min(fairnessCap, Math.max(0, c.emptyWeekStreak()) * fairnessPerEmptyWeek);
    }
}
