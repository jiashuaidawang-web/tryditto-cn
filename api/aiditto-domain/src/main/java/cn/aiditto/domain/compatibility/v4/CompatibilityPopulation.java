package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-labeled fictional relations. The grade is the scenario name.
 * It is not computed by {@link DirectionalCompatibilityScorer}.
 * Dev and holdout repeat these contrasts on new ids. Holdout shifts which dimension carries the mismatch.
 */
public final class CompatibilityPopulation {
    public static final String SOURCE = "synthetic-fixture:v4-m02-eval";
    private static final String[] CODES = {
            "partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"
    };
    private static final String[] SELF = {
            "self.pace", "self.communication", "self.social.initiative", "self.schedule"
    };
    private static final String[] IDEAL = {"slow", "quiet", "low", "early"};
    private static final String MISS = "MISS";
    private static final String HOLD = "HOLD";
    private static final String FIGHT = "FIGHT";

    public enum Grade {
        STRONG, WEAK, ONE_WAY, CONFLICT, INSUFFICIENT, HARD_VETO, CONTESTED;

        public int gain() {
            return switch (this) {
                case STRONG -> 3;
                case WEAK -> 2;
                case ONE_WAY -> 1;
                default -> 0;
            };
        }
    }

    public enum Slot {
        STRONG(Grade.STRONG, "gym"),
        COVERED(Grade.WEAK, "cooking"),
        WEAK(Grade.WEAK, "travel"),
        ONEWAY(Grade.ONE_WAY, "reading"),
        SPARSE(Grade.INSUFFICIENT, "art"),
        CONFLICT(Grade.CONFLICT, "reading"),
        VETO(Grade.HARD_VETO, "gym"),
        CONTESTED(Grade.CONTESTED, "music"),
        WITHHELD(Grade.STRONG, "movies"),
        EMPTY(Grade.INSUFFICIENT, "gaming");

        final Grade grade;
        final String hobby;

        Slot(Grade grade, String hobby) {
            this.grade = grade;
            this.hobby = hobby;
        }
    }

    public record LabeledPair(CompatibilitySubject seeker, CompatibilitySubject candidate, String split, Grade grade, Slot slot) {}

    private CompatibilityPopulation() {}

    public static List<LabeledPair> all() {
        List<LabeledPair> out = new ArrayList<>();
        for (int id = 1001; id <= 1008; id++) out.addAll(slate(id, "dev"));
        for (int id = 2001; id <= 2003; id++) out.addAll(slate(id, "holdout"));
        return List.copyOf(out);
    }

    public static List<LabeledPair> split(String split) {
        return all().stream().filter(pair -> split.equals(pair.split())).toList();
    }

    private static List<LabeledPair> slate(long seekerId, String split) {
        CompatibilitySubject seeker = person(seekerId, split, IDEAL, IDEAL, "reading", false, false);
        List<LabeledPair> out = new ArrayList<>();
        boolean holdout = "holdout".equals(split);
        for (Slot slot : Slot.values()) {
            long candidateId = seekerId * 100L + idRank(slot);
            String[] self = selfOf(slot, holdout);
            String[] preference = prefOf(slot, holdout);
            CompatibilitySubject candidate = person(candidateId, split, self, preference, slot.hobby, slot == Slot.VETO, slot == Slot.CONTESTED);
            out.add(new LabeledPair(seeker, candidate, split, slot.grade, slot));
        }
        return out;
    }

    /** Tie-break ids are not lined up with the label order. */
    private static int idRank(Slot slot) {
        return switch (slot) {
            case CONFLICT -> 1;
            case SPARSE -> 2;
            case VETO -> 3;
            case EMPTY -> 4;
            case CONTESTED -> 5;
            case ONEWAY -> 6;
            case WEAK -> 7;
            case COVERED -> 8;
            case WITHHELD -> 9;
            case STRONG -> 10;
        };
    }

    private static String[] selfOf(Slot slot, boolean holdout) {
        return switch (slot) {
            case STRONG, ONEWAY, EMPTY, VETO -> IDEAL;
            case COVERED -> holdout
                    ? new String[]{"steady", "quiet", "low", "flexible"}
                    : new String[]{"slow", "quiet", "steady", "flexible"};
            case WEAK -> holdout
                    ? new String[]{"fast", "quiet", "low", "late"}
                    : new String[]{"slow", "frequent", "high", "early"};
            case SPARSE -> new String[]{"slow", MISS, MISS, MISS};
            case CONFLICT -> new String[]{"fast", "frequent", "high", "late"};
            case CONTESTED -> new String[]{FIGHT, FIGHT, FIGHT, FIGHT};
            case WITHHELD -> new String[]{"slow", HOLD, "low", "early"};
        };
    }

    private static String[] prefOf(Slot slot, boolean holdout) {
        return switch (slot) {
            case EMPTY -> new String[0];
            case CONTESTED -> new String[]{FIGHT, FIGHT, FIGHT, FIGHT};
            case ONEWAY -> holdout
                    ? new String[]{"fast", "frequent", "high", "early"}
                    : new String[]{"slow", "frequent", "high", "late"};
            case SPARSE, WITHHELD, STRONG, VETO -> IDEAL;
            case COVERED, WEAK, CONFLICT -> selfOf(slot, holdout);
        };
    }

    private static CompatibilitySubject person(long id, String split, String[] selfValues, String[] preferenceValues,
                                               String hobby, boolean veto, boolean contestedPreferences) {
        List<ProfileFeature> self = new ArrayList<>();
        for (int i = 0; i < SELF.length; i++) addSelf(self, SELF[i], selfValues[i]);
        List<PreferenceTarget> preferences = new ArrayList<>();
        for (int i = 0; i < preferenceValues.length; i++) {
            if (FIGHT.equals(preferenceValues[i]) || contestedPreferences) {
                preferences.add(CompatibilitySubject.preference(CODES[i], null, null, null, null, FeatureStatus.CONTESTED));
            } else if (preferenceValues.length > 0 && !MISS.equals(preferenceValues[i])) {
                preferences.add(CompatibilitySubject.preference(CODES[i], preferenceValues[i], Importance.SUPER));
            }
        }
        Candidate.Builder builder = Candidate.builder(id).hobbies(hobby);
        if (veto) builder.birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60);
        long generation = "dev".equals(split) ? 1 : 2;
        return CompatibilitySubject.synthetic(id, id, generation, true, false, SOURCE, self, preferences, builder.build());
    }

    private static void addSelf(List<ProfileFeature> self, String code, String value) {
        if (MISS.equals(value)) return;
        if (HOLD.equals(value)) {
            self.add(CompatibilitySubject.feature(code, new FeatureValue.Withheld(), FeatureStatus.CONFIRMED));
            return;
        }
        if (FIGHT.equals(value)) {
            self.add(CompatibilitySubject.feature(code, null, FeatureStatus.CONTESTED));
            return;
        }
        self.add(CompatibilitySubject.feature(code, new FeatureValue.EnumOne(value), FeatureStatus.CONFIRMED));
    }
}
