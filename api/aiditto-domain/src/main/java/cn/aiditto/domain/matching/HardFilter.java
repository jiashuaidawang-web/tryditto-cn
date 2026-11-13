package cn.aiditto.domain.matching;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Hard constraints (技术方案 §7.2). Every rule is checked in both directions; {@link #violations} lists
 * every failing rule so the admin page and the property tests can see each one separately.
 */
public final class HardFilter {
    public static final int ADULT_AGE = 18;

    private HardFilter() {}

    public static boolean passes(Candidate a, Candidate b, MatchingInput in) {
        return violations(a, b, in).isEmpty();
    }

    public static List<HardRule> violations(Candidate a, Candidate b, MatchingInput in) {
        List<HardRule> out = new ArrayList<>();
        LocalDate week = in.weekStart();
        PairKey key = a.id() == b.id() ? null : PairKey.of(a.id(), b.id());
        if (key == null) out.add(HardRule.SELF);
        if (!adult(a, week) || !adult(b, week)) out.add(HardRule.ADULT);
        if (!a.pool().equals(b.pool())) out.add(HardRule.SAME_POOL);
        if (!gendersCompatible(a, b)) out.add(HardRule.GENDER);
        if (!agesCompatible(a, b, week)) out.add(HardRule.AGE);
        if (!heightsCompatible(a, b)) out.add(HardRule.HEIGHT);
        if (!withinReach(a, b, in.weights())) out.add(HardRule.DISTANCE);
        if (key != null && in.pastPairs().contains(key)) out.add(HardRule.NEVER_MATCHED);
        if (key != null && in.reportedPairs().contains(key)) out.add(HardRule.REPORTED);
        if (blocked(a, b)) out.add(HardRule.BLOCKLIST);
        if (!educationOk(a, b) || !educationOk(b, a)) out.add(HardRule.EDUCATION);
        if (!withinGradeWindow(a, b, in.weights())) out.add(HardRule.GRADE_WINDOW);
        if (!aiConstraintsOk(a, b, week) || !aiConstraintsOk(b, a, week)) out.add(HardRule.AI_CONSTRAINTS);
        if (in.manualRun() && (a.hasOpenMatch() || b.hasOpenMatch())) out.add(HardRule.OPEN_MATCH);
        if (a.student() != b.student()) out.add(HardRule.LIFE_STAGE);
        if (!cohortsCompatible(a.cohort(), b.cohort())) out.add(HardRule.COHORT);
        return Collections.unmodifiableList(out);
    }

    public static boolean adult(Candidate c, LocalDate weekStart) {
        return c.ageOn(weekStart) >= ADULT_AGE;
    }

    /** Whether {@code wants} (one person's {@code expectedGender}) includes {@code gender}. */
    public static boolean accepts(Set<GenderPref> wants, Gender gender) {
        if (wants.contains(GenderPref.EVERYONE)) return true;
        return switch (gender) {
            case MAN -> wants.contains(GenderPref.MEN);
            case WOMAN -> wants.contains(GenderPref.WOMEN);
            case NONBINARY -> wants.contains(GenderPref.NONBINARY);
            case OTHER -> false;
        };
    }

    public static boolean gendersCompatible(Candidate a, Candidate b) {
        return accepts(a.wants(), b.gender()) && accepts(b.wants(), a.gender());
    }

    public static boolean agesCompatible(Candidate a, Candidate b, LocalDate weekStart) {
        int ageA = a.ageOn(weekStart);
        int ageB = b.ageOn(weekStart);
        return ageB >= a.ageMin() && ageB <= a.ageMax() && ageA >= b.ageMin() && ageA <= b.ageMax();
    }

    public static boolean heightsCompatible(Candidate a, Candidate b) {
        return a.heightPref().accepts(b.heightCm()) && b.heightPref().accepts(a.heightCm());
    }

    public static boolean withinReach(Candidate a, Candidate b, Weights w) {
        return Commute.minutesToMidpoint(a.location(), b.location(), w) <= a.location().minutes()
                && Commute.minutesToMidpoint(b.location(), a.location(), w) <= b.location().minutes();
    }

    public static boolean blocked(Candidate a, Candidate b) {
        return !Collections.disjoint(a.blockedHashes(), b.contactHashes())
                || !Collections.disjoint(b.blockedHashes(), a.contactHashes());
    }

    /** One direction of rule 9: {@code chooser} insists on {@code partner} having at least their level. */
    public static boolean educationOk(Candidate chooser, Candidate partner) {
        if (chooser.educationImportance() != Importance.SUPER) return true;
        int partnerRank = educationRank(partner.education());
        if (partnerRank < 0) return false;
        return partnerRank >= Math.max(0, educationRank(chooser.education()));
    }

    /** {@code -1} for unknown or "prefer not to say". */
    static int educationRank(EducationLevel e) {
        if (e == null || e == EducationLevel.PREFER_NOT) return -1;
        return e.ordinal();
    }

    public static boolean withinGradeWindow(Candidate a, Candidate b, Weights w) {
        int ga = gradeIndex(a);
        int gb = gradeIndex(b);
        if (ga < 0 || gb < 0) return true;
        return Math.abs(ga - gb) <= w.gradeWindow();
    }

    /** Seniority index for students with a known year, otherwise {@code -1}. */
    static int gradeIndex(Candidate c) {
        if (!c.student() || c.schoolYear() == null || c.schoolYear() == SchoolYear.OTHER) return -1;
        return c.schoolYear().ordinal();
    }

    /**
     * Real only with real. A synthetic user only with a demo account (never another synthetic, never a real user).
     * Two demo accounts may match each other.
     */
    public static boolean cohortsCompatible(Cohort a, Cohort b) {
        if (a == Cohort.REAL || b == Cohort.REAL) return a == Cohort.REAL && b == Cohort.REAL;
        return a == Cohort.DEMO || b == Cohort.DEMO;
    }

    /** One direction of rule 10: {@code chooser}'s extracted constraints accept {@code partner}. */
    public static boolean aiConstraintsOk(Candidate chooser, Candidate partner, LocalDate weekStart) {
        return chooser.ai().accepts(partner.heightCm(), partner.ageOn(weekStart), partner.student());
    }
}
