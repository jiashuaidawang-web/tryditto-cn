package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hand-labeled populations. Labels name declared mutual profiles. They are not M02 scores. */
public final class GlobalMatchFixture {
    public static final String[] RED = {"slow", "quiet", "low", "early"};
    public static final String[] BLUE = {"steady", "direct", "steady", "flexible"};
    public static final String[] GREEN = {"fast", "frequent", "high", "late"};

    private GlobalMatchFixture() {}

    public static GlobalMatchingGraph localOptimum() {
        return GlobalMatchingGraph.math("local-optimum", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 2, 100), PreparedEdge.math(1, 3, 90),
                PreparedEdge.math(2, 4, 90), PreparedEdge.math(3, 4, 10)));
    }

    public static GlobalMatchingGraph cardinality() {
        return GlobalMatchingGraph.math("cardinality", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 2, 100), PreparedEdge.math(1, 3, 40), PreparedEdge.math(2, 4, 40)));
    }

    public static GlobalMatchingGraph triangle() {
        return GlobalMatchingGraph.math("triangle", List.of(1L, 2L, 3L), List.of(
                PreparedEdge.math(1, 2, 30), PreparedEdge.math(2, 3, 50), PreparedEdge.math(1, 3, 40)));
    }

    public static GlobalMatchingGraph truncation() {
        return GlobalMatchingGraph.math("truncation", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 3, 100), PreparedEdge.math(2, 3, 99),
                PreparedEdge.math(1, 2, 60), PreparedEdge.math(3, 4, 70)));
    }

    public static GlobalMatchingGraph qualityTier() {
        return GlobalMatchingGraph.math("quality", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 4, 90), PreparedEdge.math(2, 3, 70)));
    }

    public static GlobalMatchingGraph fairness() {
        return GlobalMatchingGraph.math("fairness", List.of(1L, 2L, 3L), List.of(
                PreparedEdge.math(1, 2, 100), PreparedEdge.math(1, 3, 97)), Map.of(3L, 4));
    }

    public static GlobalMatchingGraph empty() {
        return GlobalMatchingGraph.math("empty", List.of(1L, 2L, 3L), List.of());
    }

    public static GlobalMatchingGraph odd() {
        return GlobalMatchingGraph.math("odd", List.of(1L, 2L, 3L), List.of(PreparedEdge.math(1, 2, 80)));
    }

    public static GlobalMatchingInput population(String cohort, int base, String[] left, String[] right, String[] third) {
        List<CompatibilitySubject> users = new ArrayList<>();
        users.add(profile(base + 1, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 2, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 3, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 11, right, right, Importance.SUPER, true, false, false));
        users.add(profile(base + 12, right, right, Importance.SUPER, true, false, false));
        users.add(profile(base + 21, third, third, Importance.SUPER, true, false, false));
        users.add(profile(base + 22, third, third, Importance.SUPER, true, false, false));
        users.add(profile(base + 31, new String[]{}, new String[]{}, Importance.SUPER, true, false, false));
        users.add(profile(base + 41, left, left, Importance.SUPER, true, true, false));
        users.add(profile(base + 42, left, left, Importance.SUPER, false, false, false));
        users.add(profile(base + 43, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 51, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 52, left, left, Importance.SUPER, true, false, false));
        users.add(profile(base + 61, new String[]{left[0], left[1]}, new String[]{left[0], left[1]}, Importance.SUPER, true, false, false));
        users.add(profile(base + 71, left, right, Importance.SUPER, true, false, false));
        users.add(profile(base + 81, left, left, Importance.KINDA, true, false, false));
        users.add(profile(base + 91, left, left, Importance.SUPER, true, false, true));
        return GlobalMatchingInput.of("pop-" + cohort, cohort, users, Set.of(), Set.of(),
                        Set.of(PairKey.of(base + 51, base + 52)), Map.of((long) base + 3, 4), 7L)
                .withConsent(base + 43, "REVOKED");
    }

    public static List<PairKey> labeled(int base) {
        return List.of(PairKey.of(base + 11, base + 12), PairKey.of(base + 21, base + 22));
    }

    public static GlobalMatchingInput dev() {
        return population("dev", 40_000, RED, BLUE, GREEN);
    }

    public static GlobalMatchingInput holdout() {
        return population("holdout", 50_000, GREEN, new String[]{"slow", "direct", "low", "flexible"}, BLUE);
    }

    public static GlobalMatchingInput scale(int users) {
        String[][] palettes = {RED, BLUE, GREEN};
        List<CompatibilitySubject> people = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String[] palette = palettes[(i / 6) % palettes.length];
            people.add(profile(80_000L + i, palette, palette, Importance.SUPER, true, false, false));
        }
        return GlobalMatchingInput.of("scale-" + users, "scale", people, Set.of(), Set.of(), Set.of(), Map.of(), 1L);
    }

    private static CompatibilitySubject profile(long id, String[] self, String[] preference, Importance importance,
                                                boolean current, boolean withdrawn, boolean hardAge) {
        CompatibilitySubject person = GlobalMatchPeople.person(id, self, preference, importance, current, withdrawn);
        if (!hardAge) return person;
        Candidate candidate = Candidate.builder(id).birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).build();
        return CompatibilitySubject.synthetic(id, 1, 1, current, withdrawn, GlobalMatchPeople.SOURCE,
                person.self().features(), person.preference().targets(), candidate);
    }
}
