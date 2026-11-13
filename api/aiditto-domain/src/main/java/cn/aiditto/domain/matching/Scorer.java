package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pair scoring, integer points (技术方案 §7.3, {@code weights_version = w1}). Symmetric in its two arguments. */
public final class Scorer {

    private static final Set<Looking> COMMITTED = Set.of(Looking.LIFE_PARTNER, Looking.SERIOUS);

    private Scorer() {}

    public static ScoreBreakdown score(Candidate a, Candidate b, LocalDate weekStart, Weights w) {
        return new ScoreBreakdown(
                hobbies(a, b, w),
                looking(a.looking(), b.looking(), w),
                style(a.style(), b.style(), w),
                identity(a, b, w),
                budget(a.budget(), b.budget(), w),
                ageFit(a, b, weekStart, w),
                distance(a, b, w),
                w.fairnessBonus(a) + w.fairnessBonus(b));
    }

    /** Jaccard × max, rounded half up, plus {@code detailBonus} per shared branch option, capped at max. */
    static int hobbies(Candidate a, Candidate b, Weights w) {
        Set<String> union = new HashSet<>(a.hobbies());
        union.addAll(b.hobbies());
        if (union.isEmpty()) return 0;
        int shared = 0;
        for (String h : a.hobbies()) if (b.hobbies().contains(h)) shared++;
        int base = (w.hobbyMax() * shared * 2 + union.size()) / (2 * union.size());
        int detail = 0;
        for (Map.Entry<String, Set<String>> e : a.hobbyDetails().entrySet()) {
            Set<String> theirs = b.hobbyDetails().get(e.getKey());
            if (theirs == null) continue;
            for (String opt : e.getValue()) if (theirs.contains(opt)) detail++;
        }
        return Math.min(w.hobbyMax(), base + detail * w.hobbyDetailBonus());
    }

    /**
     * Full points for any shared option; a penalty when one side only wants casual and the other only
     * wants something committed; half points when either is "not sure".
     */
    static int looking(Set<Looking> a, Set<Looking> b, Weights w) {
        if (casualVsCommitted(a, b) || casualVsCommitted(b, a)) return -w.lookingConflictPenalty();
        for (Looking l : a) if (b.contains(l)) return w.lookingMax();
        if (a.contains(Looking.NOT_SURE) || b.contains(Looking.NOT_SURE)) return w.lookingMax() / 2;
        return 0;
    }

    private static boolean casualVsCommitted(Set<Looking> x, Set<Looking> y) {
        return x.equals(Set.of(Looking.CASUAL)) && !y.isEmpty() && COMMITTED.containsAll(y);
    }

    static int style(MatchingStyle a, MatchingStyle b, Weights w) {
        int d = Math.abs(a.ordinal() - b.ordinal());
        return d == 0 ? w.styleSame() : d == 1 ? w.styleAdjacent() : w.styleOther();
    }

    static int identity(Candidate a, Candidate b, Weights w) {
        if (a.student() && b.student()) {
            int ga = HardFilter.gradeIndex(a);
            int gb = HardFilter.gradeIndex(b);
            if (ga < 0 || gb < 0) return w.identityStudentUnknownYear();
            int d = Math.abs(ga - gb);
            return d <= 1 ? w.identityStudentsClose() : d == 2 ? w.identityStudentsTwoApart() : w.identityStudentsFar();
        }
        if (!a.student() && !b.student()) return w.identityBothWorking();
        return w.identityMixed();
    }

    static int budget(Budget a, Budget b, Weights w) {
        if (a == Budget.NO_PREF || b == Budget.NO_PREF || a == b) return w.budgetMatch();
        return Math.abs(a.ordinal() - b.ordinal()) == 1 ? w.budgetAdjacent() : 0;
    }

    /** Average over both directions of how close the partner's age is to the middle of one's range. */
    static int ageFit(Candidate a, Candidate b, LocalDate weekStart, Weights w) {
        double sa = ageCloseness(a, b.ageOn(weekStart));
        double sb = ageCloseness(b, a.ageOn(weekStart));
        return (int) Math.round((sa + sb) / 2 * w.ageMax());
    }

    private static double ageCloseness(Candidate chooser, int partnerAge) {
        double mid = (chooser.ageMin() + chooser.ageMax()) / 2.0;
        double half = (chooser.ageMax() - chooser.ageMin()) / 2.0;
        if (half == 0) return partnerAge == chooser.ageMin() ? 1 : 0;
        return clamp01(1 - Math.abs(partnerAge - mid) / half);
    }

    /** Average over both directions of the unused share of one's travel budget. */
    static int distance(Candidate a, Candidate b, Weights w) {
        double ra = Commute.minutesToMidpoint(a.location(), b.location(), w) / a.location().minutes();
        double rb = Commute.minutesToMidpoint(b.location(), a.location(), w) / b.location().minutes();
        return (int) Math.round((clamp01(1 - ra) + clamp01(1 - rb)) / 2 * w.distanceMax());
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
