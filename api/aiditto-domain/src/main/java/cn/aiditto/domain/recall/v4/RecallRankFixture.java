package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-labeled recall population. Labels were written from the declared profiles,
 * not from an M02 score. Dev and holdout use different people and different values.
 */
public final class RecallRankFixture {
    public static final String SOURCE = "synthetic-fixture:v4-m01-rank";
    public static final String POLICY = "recall-rank-1";

    public enum Label { STRONG, ONE_WAY, DECOY, SPARSE, WITHDRAWN, STALE, HARD_AGE, PAST_PAIR }

    public record Person(CompatibilitySubject profile, RecallSubject recall, Label label) {}

    public record Cohort(String split, Person seeker, List<Person> people, Set<PairKey> pastPairs) {
        public Cohort {
            people = List.copyOf(people);
            pastPairs = Set.copyOf(pastPairs);
        }

        public List<Person> labeled(Label label) {
            List<Person> out = new ArrayList<>();
            for (Person person : people) if (person.label() == label) out.add(person);
            return out;
        }
    }

    private RecallRankFixture() {}

    public static Cohort dev() {
        return cohort("dev", 10_000, 40, 4, 4,
                new String[]{"slow", "quiet", "low", "early"},
                new String[]{"fast", "frequent", "high", "late"},
                "reading");
    }

    public static Cohort holdout() {
        return cohort("holdout", 20_000, 50, 3, 3,
                new String[]{"steady", "direct", "steady", "flexible"},
                new String[]{"fast", "frequent", "high", "late"},
                "music");
    }

    private static Cohort cohort(String split, int base, int decoys, int visibleStrong, int hiddenStrong,
                                 String[] ideal, String[] conflict, String hobby) {
        Person seeker = person(split, base, ideal, ideal, hobby,
                Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.STRONG, true, false);
        List<Person> people = new ArrayList<>();
        people.add(seeker);
        for (int i = 1; i <= decoys; i++) {
            people.add(person(split, base + i, conflict, conflict, hobby,
                    Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.DECOY, true, false));
        }
        for (int i = 0; i < visibleStrong; i++) {
            people.add(person(split, base + 8_000 + i, ideal, ideal, hobby,
                    Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.STRONG, true, false));
        }
        for (int i = 0; i < hiddenStrong; i++) {
            people.add(person(split, base + 9_000 + i, ideal, ideal, "gym",
                    Map.of(), Map.of(), Set.of("gym"), Label.STRONG, true, false));
        }
        people.add(person(split, base + 7_000, ideal, conflict, hobby,
                Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.ONE_WAY, true, false));
        people.add(person(split, base + 6_000, new String[]{}, new String[]{}, null,
                Map.of(), Map.of(), Set.of(), Label.SPARSE, true, false));
        people.add(person(split, base + 5_001, ideal, ideal, hobby,
                Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.WITHDRAWN, true, true));
        people.add(person(split, base + 5_002, ideal, ideal, hobby,
                Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.STALE, false, false));
        people.add(hardAge(split, base + 5_003));
        people.add(person(split, base + 5_004, ideal, ideal, hobby,
                Map.of("self.pace", ideal[0]), Map.of("partner.pace", ideal[0]), Set.of(hobby), Label.PAST_PAIR, true, false));
        return new Cohort(split, seeker, people, Set.of(PairKey.of(base, base + 5_004)));
    }

    private static Person hardAge(String split, long id) {
        Candidate candidate = Candidate.builder(id).birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).hobbies("reading").build();
        return person(split, id, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "quiet", "low", "early"},
                "reading", Map.of("self.pace", "slow"), Map.of("partner.pace", "slow"), Set.of("reading"),
                Label.HARD_AGE, true, false, candidate);
    }

    private static Person person(String split, long id, String[] self, String[] preference, String hobby,
                                 Map<String, String> recallSelf, Map<String, String> recallPreference, Set<String> recallHobbies,
                                 Label label, boolean current, boolean withdrawn) {
        Candidate candidate = hobby == null ? Candidate.builder(id).hobbies().build() : Candidate.builder(id).hobbies(hobby).build();
        return person(split, id, self, preference, hobby, recallSelf, recallPreference, recallHobbies, label, current, withdrawn, candidate);
    }

    private static Person person(String split, long id, String[] self, String[] preference, String hobby,
                                 Map<String, String> recallSelf, Map<String, String> recallPreference, Set<String> recallHobbies,
                                 Label label, boolean current, boolean withdrawn, Candidate candidate) {
        String[] codesSelf = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
        String[] codesPref = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};
        List<cn.aiditto.domain.relationship.ProfileFeature> features = new ArrayList<>();
        List<cn.aiditto.domain.relationship.PreferenceTarget> targets = new ArrayList<>();
        for (int i = 0; i < self.length; i++) {
            features.add(CompatibilitySubject.feature(codesSelf[i], new FeatureValue.EnumOne(self[i]), FeatureStatus.CONFIRMED));
            targets.add(CompatibilitySubject.preference(codesPref[i], preference[i], Importance.SUPER));
        }
        CompatibilitySubject profile = CompatibilitySubject.synthetic(id, 1, 1, current, withdrawn, SOURCE, features, targets, candidate);
        RecallSubject recall = new RecallSubject(id, split, 1, current, withdrawn, SOURCE, candidate,
                cn.aiditto.domain.compatibility.v4.CompatibilityContext.eligibleApplicant(candidate),
                cn.aiditto.domain.compatibility.v4.CompatibilityContext.granted(id, cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine.NOW),
                recallSelf, recallHobbies, recallPreference, 1, RecallIngress.SYNTHETIC_FIXTURE);
        return new Person(profile, recall, label);
    }
}
