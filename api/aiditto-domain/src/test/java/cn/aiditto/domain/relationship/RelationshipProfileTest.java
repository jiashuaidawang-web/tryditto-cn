package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.BehaviorKind;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Polarity;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Visibility;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipProfileTest {
    private final FeatureRegistry registry = FeatureRegistry.v1();

    @Test
    void selfAndPreferenceStayApart() {
        RelationshipProfileSnapshot a = RelationshipProfileCases.complementaryA();
        RelationshipProfileSnapshot b = RelationshipProfileCases.complementaryB();
        assertThat(codes(a.self())).contains("self.social.initiative", "lifestyle.hobbies", "lifestyle.stated_interest")
                .doesNotContain("partner.social.initiative");
        assertThat(codes(a.preference())).containsExactly("partner.social.initiative");
        assertThat(a.self().features()).anySatisfy(feature -> {
            assertThat(feature.featureCode()).isEqualTo("self.social.initiative");
            assertThat(feature.value()).isEqualTo(new FeatureValue.EnumOne("low"));
        });
        assertThat(a.preference().targets()).allSatisfy(target -> {
            assertThat(target.importance()).isEqualTo(Importance.KINDA);
            assertThat(target.confidence()).isEqualTo(0.9);
            assertThat(target.constraintType()).isEqualTo(ConstraintType.SOFT);
        });
        assertThat(b.self().features()).anySatisfy(feature ->
                assertThat(feature.value()).isEqualTo(new FeatureValue.EnumOne("high")));
        assertThat(b.preference().targets().get(0).targetValue()).isEqualTo(new FeatureValue.EnumOne("low"));
        assertThat(a.memory().observations()).isEmpty();
        assertThat(b.behavior().records()).isEmpty();
        assertThat(a.canonical()).doesNotContain("MBTI", "mbti", "颜值");
    }

    @Test
    void similarPreferenceDoesNotCopySelf() {
        RelationshipProfileSnapshot reading = RelationshipProfileCases.similarReading();
        RelationshipProfileSnapshot gym = RelationshipProfileCases.similarGym();
        assertThat(reading.preference().targets().get(0).targetValue())
                .isEqualTo(gym.preference().targets().get(0).targetValue());
        assertThat(reading.self().features().get(0).value()).isNotEqualTo(gym.self().features().get(0).value());
    }

    @Test
    void oneWayPreferenceDoesNotScoreOrInventTheOtherSide() {
        RelationshipProfileSnapshot a = RelationshipProfileCases.oneWayA();
        RelationshipProfileSnapshot b = RelationshipProfileCases.oneWayB();
        assertThat(codes(a.preference())).containsExactly("partner.pace");
        assertThat(b.preference().targets()).isEmpty();
        assertThat(codes(b.self())).containsExactly("self.pace");
        assertThat(a.canonical()).doesNotContain("score", "rank");
    }

    @Test
    void coldStartStaysUnknown() {
        RelationshipProfileSnapshot sparse = RelationshipProfileCases.coldStart();
        assertThat(codes(sparse.self())).containsExactly("relationship.goal");
        assertThat(sparse.preference().targets()).isEmpty();
        assertThat(sparse.behavior().records()).isEmpty();
        assertThat(sparse.memory().observations()).isEmpty();
        assertThat(sparse.canonical()).doesNotContain("0.5", "UNKNOWN");
        assertThat(sparse.self().features().get(0).value()).isEqualTo(FeatureValue.enumMany("serious"));
    }

    @Test
    void changedPreferenceIsADifferentSnapshot() {
        RelationshipProfileSnapshot before = RelationshipProfileCases.preferenceBefore();
        RelationshipProfileSnapshot after = RelationshipProfileCases.preferenceAfter();
        assertThat(before.inputHash()).isNotEqualTo(after.inputHash());
        assertThat(before.preference().targets()).anySatisfy(target -> {
            assertThat(target.featureCode()).isEqualTo("partner.education");
            assertThat(target.importance()).isEqualTo(Importance.KINDA);
            assertThat(target.constraintType()).isEqualTo(ConstraintType.SOFT);
        });
        assertThat(after.preference().targets()).anySatisfy(target -> {
            assertThat(target.featureCode()).isEqualTo("partner.education");
            assertThat(target.importance()).isEqualTo(Importance.SUPER);
            assertThat(target.constraintType()).isEqualTo(ConstraintType.DOCUMENTED_EXISTING_RULE);
        });
        assertThat(ProfileVersionPolicy.decide(true, 1, 1, before.inputHash(), false, 2, after.inputHash()).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.INSERT);
        assertThat(ProfileVersionPolicy.decide(true, 2, 2, after.inputHash(), false, 1, before.inputHash()).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.REJECT_STALE);
        assertThat(ProfileVersionPolicy.decide(true, 1, 1, before.inputHash(), false, 4, before.inputHash()).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.KEEP_CURRENT);
    }

    @Test
    void retractedEvidenceDropsOutAndContradictionStaysContested() {
        RelationshipProfileSnapshot snapshot = RelationshipProfileCases.contested();
        assertThat(snapshot.self().features()).hasSize(1);
        ProfileFeature feature = snapshot.self().features().get(0);
        assertThat(feature.status()).isEqualTo(RelationshipTypes.FeatureStatus.CONTESTED);
        assertThat(feature.value()).isNull();
        assertThat(snapshot.evidence()).hasSize(3);
        assertThat(snapshot.evidence()).anySatisfy(item -> assertThat(item.status()).isEqualTo(EvidenceStatus.RETRACTED));
        ProfileEvidence retracted = snapshot.evidence().stream()
                .filter(item -> item.status() == EvidenceStatus.RETRACTED).findFirst().orElseThrow();
        assertThat(feature.evidenceRefs()).doesNotContain(retracted.evidenceId()).hasSize(2);
    }

    @Test
    void rejectsIllegalNumbersUnknownSubstitutesAndHardInferences() {
        assertThatThrownBy(() -> new FeatureValue.Num(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeatureValue.Num(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeatureValue.Span(Double.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evidence(1, "self.social.initiative", Section.SELF, new FeatureValue.EnumOne("low"),
                EvidenceSource.SELF_REPORTED, "bad-confidence", Double.NaN, EvidenceStatus.CONFIRMED, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evidence(1, "self.social.initiative", Section.SELF, new FeatureValue.EnumOne("low"),
                EvidenceSource.SELF_REPORTED, "over", 1.1, EvidenceStatus.CONFIRMED, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileEvidence.build(1, "self.social.initiative", Section.SELF, new FeatureValue.EnumOne("mbti"),
                EvidenceSource.SELF_REPORTED, "bad-enum", "case", 1, EvidenceStatus.CONFIRMED, null, null, null, null, null, registry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileEvidence.build(1, "partner.social.initiative", Section.PREFERENCE, new FeatureValue.EnumOne("high"),
                EvidenceSource.MODEL_INFERENCE, "inferred", "llm", 0.4, EvidenceStatus.PROVISIONAL,
                Operator.EQ, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, null, null, registry))
                .isInstanceOf(IllegalArgumentException.class);
        ProfileEvidence inferred = ProfileEvidence.build(1, "partner.social.initiative", Section.PREFERENCE,
                new FeatureValue.EnumOne("high"), EvidenceSource.MODEL_INFERENCE, "inferred-soft", "llm", 0.4,
                EvidenceStatus.PROVISIONAL, Operator.EQ, Importance.SUPER, ConstraintType.SOFT, null, null, registry);
        assertThat(inferred.constraintType()).isEqualTo(ConstraintType.SOFT);
        assertThat(inferred.status()).isEqualTo(EvidenceStatus.PROVISIONAL);
        assertThat(inferred.importance()).isEqualTo(Importance.SUPER);
        assertThat(inferred.confidence()).isEqualTo(0.4);
    }

    @Test
    void simulatedDialogueIsNotObservedBehaviorAndEvidenceMustMatchTheUser() {
        assertThatThrownBy(() -> ProfileEvidence.build(1, "opening.message", Section.BEHAVIOR, new FeatureValue.Text("你好"),
                EvidenceSource.DIRECT_ANSWER, "firstMessage", EvidenceIds.CATALOG, 1, EvidenceStatus.CONFIRMED,
                null, null, null, BehaviorKind.OBSERVED_BEHAVIOR, null, registry))
                .isInstanceOf(IllegalArgumentException.class);
        String id = EvidenceIds.compute(1, EvidenceSource.FEEDBACK_OBSERVATION, "persona-chat", "v1", "opening.message");
        assertThatThrownBy(() -> new ProfileEvidence(id, 1, "opening.message", Section.BEHAVIOR, new FeatureValue.Text("你好"),
                EvidenceSource.FEEDBACK_OBSERVATION, "persona-chat", "v1", 1, EvidenceStatus.CONFIRMED, Visibility.OWNER,
                "profile-self", null, null, null, BehaviorKind.OBSERVED_BEHAVIOR, null))
                .isInstanceOf(IllegalArgumentException.class);
        ProfileEvidence owned = evidence(2, "self.social.initiative", Section.SELF, new FeatureValue.EnumOne("low"),
                EvidenceSource.SELF_REPORTED, "mine", 1, EvidenceStatus.CONFIRMED, null, null, null, null);
        assertThatThrownBy(() -> RelationshipProfile.of(1, List.of(owned), registry)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileEvidence("not-the-server-id", 2, "self.social.initiative", Section.SELF,
                new FeatureValue.EnumOne("low"), EvidenceSource.SELF_REPORTED, "mine", "case", 1, EvidenceStatus.CONFIRMED,
                Visibility.OWNER, "profile-self", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unlimitedIsNotMissingAndWithheldIsNotFalse() {
        RelationshipProfileSnapshot missing = RelationshipProfileProjector.project(7, Map.of("hobbies", List.of("reading")), registry);
        RelationshipProfileSnapshot unlimited = RelationshipProfileProjector.project(7, Map.of(
                "hobbies", List.of("reading"),
                "heightRange", Map.of("any", true, "min", 150, "max", 200),
                "firstDateBudget", "noPref",
                "educationLevel", "preferNot"), registry);
        assertThat(codes(missing.preference())).doesNotContain("partner.height");
        assertThat(unlimited.preference().targets()).anySatisfy(target -> {
            assertThat(target.featureCode()).isEqualTo("partner.height");
            assertThat(target.targetValue()).isEqualTo(new FeatureValue.Unbounded());
            assertThat(target.constraintType()).isEqualTo(ConstraintType.UNBOUNDED);
        });
        assertThat(unlimited.preferenceJson()).doesNotContain("150", "200", "0.5");
        assertThat(unlimited.selfJson()).doesNotContain("0.5");
        assertThat(unlimited.self().features()).anySatisfy(feature -> {
            assertThat(feature.featureCode()).isEqualTo("education.level");
            assertThat(feature.value()).isInstanceOf(FeatureValue.Withheld.class);
        });
        assertThat(unlimited.self().features()).anySatisfy(feature -> {
            assertThat(feature.featureCode()).isEqualTo("spending.first_date");
            assertThat(feature.value()).isInstanceOf(FeatureValue.Unbounded.class);
        });
        assertThat(unlimited.canonical()).doesNotContain("false");
    }

    @Test
    void projectorKeepsSensitiveValuesOutAndDoesNotInfer() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("name", "测试姓名");
        answers.put("gender", "woman");
        answers.put("birthday", "2000-01-02");
        answers.put("expectedGender", List.of("men"));
        answers.put("hobbies", List.of("reading", "摄影"));
        answers.put("relationshipLooking", List.of("serious"));
        answers.put("dealbreakers", "不能接受异地");
        answers.put("personalEmail", "secret@example.com");
        answers.put("schoolEmail", "student@school.edu");
        answers.put("location", Map.of("placeId", "place-secret", "lat", 31.2, "lng", 121.5));
        answers.put("occupation", "联系13800138000或secret@example.com");
        answers.put("firstMessage", "周末去看展");
        answers.put("politics", "center");
        answers.put("religion", List.of("buddhist"));
        RelationshipProfileSnapshot snapshot = RelationshipProfileProjector.project(8, answers, registry);
        String json = snapshot.canonical();
        assertThat(json).doesNotContain("测试姓名", "woman", "2000-01-02", "men", "不能接受异地",
                "secret@example.com", "student@school.edu", "place-secret", "31.2", "121.5", "13800138000",
                "center", "buddhist", "self.social.initiative");
        assertThat(json).contains("reading", "摄影");
        assertThat(codes(snapshot.self())).contains("lifestyle.hobbies", "lifestyle.stated_interest", "occupation.text", "relationship.goal");
        assertThat(codes(snapshot.preference())).containsExactly("partner.gender");
        assertThat(snapshot.preference().targets().get(0).targetValue())
                .isEqualTo(new FeatureValue.ControlledRef("profile.expected_genders_enc"));
        assertThat(snapshot.behavior().records()).singleElement().satisfies(record -> {
            assertThat(record.kind()).isEqualTo(BehaviorKind.SELF_REPORTED);
            assertThat(record.value()).isEqualTo(new FeatureValue.Text("周末去看展"));
        });
        assertThat(snapshot.memory().observations()).isEmpty();
    }

    @Test
    void hashIgnoresMapOrderAndChangesWhenTheAnswerChanges() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("hobbies", List.of("music", "reading"));
        first.put("relationshipLooking", List.of("friends", "serious"));
        first.put("matchingStyle", "steady");
        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("matchingStyle", "steady");
        reversed.put("relationshipLooking", List.of("serious", "friends"));
        reversed.put("hobbies", List.of("reading", "music"));
        RelationshipProfileSnapshot left = RelationshipProfileProjector.project(9, first, registry);
        RelationshipProfileSnapshot right = RelationshipProfileProjector.project(9, reversed, registry);
        assertThat(left.inputHash()).isEqualTo(right.inputHash());
        assertThat(left.canonical()).isEqualTo(right.canonical());
        Map<String, Object> changed = new LinkedHashMap<>(first);
        changed.put("hobbies", List.of("music"));
        assertThat(RelationshipProfileProjector.project(9, changed, registry).inputHash()).isNotEqualTo(left.inputHash());
    }

    @Test
    void evidenceOrderDoesNotChangeTheHashAndMemoryNeedsRealFeedback() {
        ProfileEvidence one = evidence(4, "self.social.initiative", Section.SELF, new FeatureValue.EnumOne("low"),
                EvidenceSource.SELF_REPORTED, "a", 1, EvidenceStatus.CONFIRMED, null, null, null, null);
        ProfileEvidence two = evidence(4, "self.pace", Section.SELF, new FeatureValue.EnumOne("slow"),
                EvidenceSource.SELF_REPORTED, "b", 1, EvidenceStatus.CONFIRMED, null, null, null, null);
        String forward = RelationshipProfile.of(4, List.of(one, two), registry).snapshot(registry).inputHash();
        String backward = RelationshipProfile.of(4, List.of(two, one), registry).snapshot(registry).inputHash();
        assertThat(forward).isEqualTo(backward);
        ProfileEvidence memory = ProfileEvidence.build(4, "self.social.initiative", Section.MEMORY, new FeatureValue.EnumOne("low"),
                EvidenceSource.FEEDBACK_OBSERVATION, "feedback-22", "fb-1", 0.8, EvidenceStatus.CONFIRMED,
                null, null, null, null, Polarity.POSITIVE, registry);
        RelationshipProfile profile = RelationshipProfile.of(4, List.of(memory), registry);
        assertThat(profile.memory().observations()).hasSize(1);
        assertThat(profile.self().features()).isEmpty();
        assertThatThrownBy(() -> ProfileEvidence.build(4, "self.social.initiative", Section.MEMORY, new FeatureValue.EnumOne("low"),
                EvidenceSource.MODEL_INFERENCE, "llm:guess", "llm", 0.2, EvidenceStatus.PROVISIONAL,
                null, null, null, null, Polarity.POSITIVE, registry)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registryRejectsRanksAndCopiesNoSensitiveValue() {
        assertThat(registry.all()).allSatisfy(definition -> {
            assertThat(definition.featureCode()).doesNotContain("mbti", "attractiveness", "rank");
            assertThat(definition.displayName()).doesNotContain("颜值", "人格优劣", "价值排名");
        });
        assertThatThrownBy(() -> ProfileEvidence.build(1, "partner.gender", Section.PREFERENCE, new FeatureValue.EnumOne("men"),
                EvidenceSource.DIRECT_ANSWER, "expectedGender", EvidenceIds.CATALOG, 1, EvidenceStatus.CONFIRMED,
                Operator.EQ, Importance.SUPER, ConstraintType.DOCUMENTED_EXISTING_RULE, null, null, registry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writeCanonicalCases() throws Exception {
        Path dir = Path.of("target", "p01-cases");
        Files.createDirectories(dir);
        for (var entry : RelationshipProfileCases.all().entrySet()) {
            Files.writeString(dir.resolve(entry.getKey() + ".json"), entry.getValue().canonical());
            Files.writeString(dir.resolve(entry.getKey() + ".hash"), entry.getValue().inputHash());
        }
        assertThat(RelationshipProfileCases.all()).hasSize(10);
    }

    private static List<String> codes(SelfProfile profile) {
        return profile.features().stream().map(ProfileFeature::featureCode).toList();
    }

    private static List<String> codes(PreferenceProfile profile) {
        return profile.targets().stream().map(PreferenceTarget::featureCode).toList();
    }

    private ProfileEvidence evidence(long userId, String code, Section section, FeatureValue claim, EvidenceSource source,
                                     String ref, double confidence, EvidenceStatus status, Operator operator,
                                     Importance importance, ConstraintType constraint, BehaviorKind kind) {
        return ProfileEvidence.build(userId, code, section, claim, source, ref, "case", confidence, status,
                operator, importance, constraint, kind, null, registry);
    }
}
