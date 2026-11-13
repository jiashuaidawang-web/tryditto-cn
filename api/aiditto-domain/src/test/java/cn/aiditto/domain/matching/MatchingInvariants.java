package cn.aiditto.domain.matching;

import cn.aiditto.domain.matching.MatchingResult.MatchedPair;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a solver result against the rules without going through {@link HardFilter}: the hard
 * constraints are restated here directly from 技术方案 §7.2 so a bug in the filter cannot hide itself.
 * Shared by the jqwik properties and the seeded JUnit runs.
 */
public final class MatchingInvariants {
    private MatchingInvariants() {}

    public static List<String> violations(MatchingInput in, MatchingResult result) {
        List<String> out = new ArrayList<>();
        Map<Long, Candidate> byId = new HashMap<>();
        for (Candidate c : in.candidates()) byId.put(c.id(), c);

        Set<Long> seen = new HashSet<>();
        for (MatchedPair p : result.pairs()) {
            Candidate a = byId.get(p.low());
            Candidate b = byId.get(p.high());
            if (a == null || b == null) {
                out.add(p.key() + ": unknown person");
                continue;
            }
            if (!seen.add(a.id())) out.add(a.id() + " matched twice");
            if (!seen.add(b.id())) out.add(b.id() + " matched twice");
            for (String v : hardViolations(a, b, in)) out.add(p.key() + ": " + v);
            int score = Scorer.score(a, b, in.weekStart(), in.weights()).score();
            int min = Math.max(in.weights().threshold(a.style()), in.weights().threshold(b.style()));
            if (score < min) out.add(p.key() + ": score " + score + " below style minimum " + min);
            if (score != p.score()) out.add(p.key() + ": reported score " + p.score() + " != " + score);
        }

        Set<Long> adults = new HashSet<>();
        for (Candidate c : in.candidates()) if (age(c, in) >= 18) adults.add(c.id());
        Set<Long> accounted = new HashSet<>(seen);
        for (long id : result.unmatched()) {
            if (!accounted.add(id)) out.add(id + " both matched and unmatched, or listed twice");
        }
        if (!accounted.equals(adults)) out.add("matched + unmatched != adult pool");

        Set<Long> unmatched = new HashSet<>(result.unmatched());
        for (Edge e : MatchingSolver.buildEdges(in)) {
            if (unmatched.contains(e.low()) && unmatched.contains(e.high())) {
                out.add("not maximal: " + e.key() + " both unmatched");
                break;
            }
        }
        return out;
    }

    /** §7.2 restated: everything a pair must satisfy, both directions. */
    public static List<String> hardViolations(Candidate a, Candidate b, MatchingInput in) {
        List<String> out = new ArrayList<>();
        int ageA = age(a, in);
        int ageB = age(b, in);
        if (a.id() == b.id()) out.add("self");
        if (ageA < 18 || ageB < 18) out.add("under 18");
        if (!a.pool().equals(b.pool())) out.add("different pool");
        if (!wants(a, b) || !wants(b, a)) out.add("gender preference");
        if (ageB < a.ageMin() || ageB > a.ageMax() || ageA < b.ageMin() || ageA > b.ageMax()) out.add("age range");
        if (!heightOk(a, b) || !heightOk(b, a)) out.add("height range");
        if (Commute.minutesToMidpoint(a.location(), b.location(), in.weights()) > a.location().minutes()
                || Commute.minutesToMidpoint(b.location(), a.location(), in.weights()) > b.location().minutes()) out.add("travel time");
        PairKey key = a.id() == b.id() ? null : PairKey.of(a.id(), b.id());
        if (key != null && in.pastPairs().contains(key)) out.add("matched before");
        if (key != null && in.reportedPairs().contains(key)) out.add("reported");
        for (String h : a.blockedHashes()) if (b.contactHashes().contains(h)) out.add("a blocked b");
        for (String h : b.blockedHashes()) if (a.contactHashes().contains(h)) out.add("b blocked a");
        if (!educationOk(a, b) || !educationOk(b, a)) out.add("education");
        if (a.student() && b.student() && known(a.schoolYear()) && known(b.schoolYear())
                && Math.abs(a.schoolYear().ordinal() - b.schoolYear().ordinal()) > in.weights().gradeWindow()) out.add("grade window");
        if (!aiOk(a.ai(), b, ageB) || !aiOk(b.ai(), a, ageA)) out.add("dealbreaker constraints");
        if (in.manualRun() && (a.hasOpenMatch() || b.hasOpenMatch())) out.add("open match in manual run");
        if (a.student() != b.student()) out.add("life stage");
        if (!cohortOk(a.cohort(), b.cohort())) out.add("cohort");
        return out;
    }

    static int age(Candidate c, MatchingInput in) {
        return Period.between(c.birthday(), in.weekStart()).getYears();
    }

    private static boolean wants(Candidate chooser, Candidate other) {
        Set<GenderPref> w = chooser.wants();
        if (w.contains(GenderPref.EVERYONE)) return true;
        return (other.gender() == Gender.MAN && w.contains(GenderPref.MEN))
                || (other.gender() == Gender.WOMAN && w.contains(GenderPref.WOMEN))
                || (other.gender() == Gender.NONBINARY && w.contains(GenderPref.NONBINARY));
    }

    private static boolean heightOk(Candidate chooser, Candidate other) {
        HeightPref p = chooser.heightPref();
        return p.any() || (other.heightCm() >= p.min() && other.heightCm() <= p.max());
    }

    private static boolean educationOk(Candidate chooser, Candidate other) {
        if (chooser.educationImportance() != Importance.SUPER) return true;
        EducationLevel mine = chooser.education();
        EducationLevel theirs = other.education();
        if (theirs == null || theirs == EducationLevel.PREFER_NOT) return false;
        if (mine == null || mine == EducationLevel.PREFER_NOT) return true;
        return theirs.ordinal() >= mine.ordinal();
    }

    /** Restated, not delegated: real-real, demo-synthetic, and demo-demo only. */
    private static boolean cohortOk(Cohort a, Cohort b) {
        if (a == Cohort.REAL || b == Cohort.REAL) return a == Cohort.REAL && b == Cohort.REAL;
        return a == Cohort.DEMO || b == Cohort.DEMO;
    }

    private static boolean known(SchoolYear y) {
        return y != null && y != SchoolYear.OTHER;
    }

    private static boolean aiOk(AiConstraints c, Candidate other, int otherAge) {
        if (c.minHeight() != null && other.heightCm() < c.minHeight()) return false;
        if (c.maxHeight() != null && other.heightCm() > c.maxHeight()) return false;
        if (c.minAge() != null && otherAge < c.minAge()) return false;
        if (c.maxAge() != null && otherAge > c.maxAge()) return false;
        if (c.studentsOnly() && !other.student()) return false;
        return !(c.nonStudentsOnly() && other.student());
    }
}
