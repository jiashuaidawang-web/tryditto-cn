package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileExtractionTest {
    private final FeatureRegistry registry = FeatureRegistry.v1();

    @Test
    void preferenceStatementDoesNotBecomeASelfTrait() {
        ProfileContext context = context(List.of(new ProfileContextBuilder.Statement("PREFERENCE", "我希望对方更主动")));
        String token = token(context, "PREFERENCE_STATEMENT");
        ProfileExtractionValidator.Result result = ProfileExtractionValidator.check(context, List.of(
                feature("SELF", "self.social.initiative", "high", token, ""),
                feature("PREFERENCE", "partner.social.initiative", "high", token, "")), registry);
        assertThat(result.accepted()).allSatisfy(item -> assertThat(item.section()).isEqualTo(RelationshipTypes.Section.PREFERENCE));
        assertThat(result.rejected()).anySatisfy(item -> assertThat(item.reason()).isEqualTo("unsupported"));
    }

    @Test
    void hardConstraintUnknownObservedAndForeignEvidenceAreRejected() {
        ProfileContext context = context(List.of(new ProfileContextBuilder.Statement("SELF", "我这个人比较慢热")));
        String token = token(context, "SELF_STATEMENT");
        ProfileExtractionValidator.Result result = ProfileExtractionValidator.check(context, List.of(
                new ExtractedFeature("PREFERENCE", "partner.age", "18", "EQ", "SUPER", List.of(token), 0.9, "STATED", "", true),
                new ExtractedFeature("SELF", "self.pace", "UNKNOWN", "EQ", "NONE", List.of(token), 0.4, "STATED", "", false),
                new ExtractedFeature("BEHAVIOR", "behavior.scenario.signal", "high", "EQ", "NONE", List.of(token), 0.4,
                        "STATED", "OBSERVED_BEHAVIOR", false),
                new ExtractedFeature("MEMORY", "self.pace", "slow", "EQ", "NONE", List.of(token), 0.4, "STATED", "", false),
                new ExtractedFeature("SELF", "self.pace", "slow", "EQ", "NONE", List.of("ev-nope"), 0.4, "STATED", "", false),
                new ExtractedFeature("SELF", "not.a.feature", "slow", "EQ", "NONE", List.of(token), 0.4, "STATED", "", false),
                new ExtractedFeature("SELF", "self.pace", "slow", "EQ", "NONE", List.of(), 0.4, "STATED", "", false)), registry);
        assertThat(result.accepted()).isEmpty();
        assertThat(result.hardRequests()).isEqualTo(1);
        assertThat(result.invalidRefs()).isEqualTo(1);
    }

    @Test
    void exportOmitsPrivateTextAndAccountIds() {
        ProfileContext context = ProfileContextBuilder.build(new ProfileContextBuilder.Inputs(
                8_675_309, 4, 2, true, true,
                Map.of("occupation", "13800138000", "hobbies", List.of("reading"),
                        "expectedGender", List.of("men")),
                List.of(new ProfileContextBuilder.Statement("SELF", "学号202011223344"),
                        new ProfileContextBuilder.Statement("PREFERENCE", "我希望对方更主动", true)),
                List.of()));
        assertThat(context.exportCanonical()).doesNotContain("8675309", "13800138000", "202011223344", "men");
        assertThat(context.exportCanonical()).contains("reading");
        assertThat(context.modelExportGranted()).isTrue();
        ProfileContext withheld = ProfileContextBuilder.build(new ProfileContextBuilder.Inputs(
                8_675_309, 4, 2, true, false, Map.of("hobbies", List.of("reading")), List.of(), List.of()));
        assertThat(withheld.exportCanonical()).isEqualTo("{\"export\":\"withheld\"}");
        assertThat(withheld.exportCanonical()).doesNotContain("reading", "8675309");
    }

    @Test
    void mergerKeepsDeterministicFactsAndAdmitsProvisionalEvidence() {
        ProfileContext context = context(List.of(new ProfileContextBuilder.Statement("SELF", "我这个人比较慢热")));
        List<ProfileEvidence> deterministic = RelationshipProfileProjector.project(
                context.userId(), context.answers(), registry).evidence();
        ProfileExtractionValidator.Result result = ProfileExtractionValidator.check(context, List.of(
                feature("SELF", "self.pace", "fast", token(context, "SELF_STATEMENT"), "")), registry);
        ProfileEvidence confirmed = ProfileEvidence.build(context.userId(), "self.pace", RelationshipTypes.Section.SELF,
                new FeatureValue.EnumOne("slow"), EvidenceSource.SELF_REPORTED, "stated-pace", "case", 1,
                EvidenceStatus.CONFIRMED, null, null, null, null, null, registry);
        RelationshipProfileSnapshot blocked = ProfileMerger.merge(context.userId(),
                concat(deterministic, confirmed), result.accepted(), registry);
        assertThat(blocked.evidence()).noneMatch(item -> item.sourceType() == EvidenceSource.MODEL_INFERENCE);
        RelationshipProfileSnapshot added = ProfileMerger.merge(context.userId(), deterministic, result.accepted(), registry);
        assertThat(added.self().features()).anySatisfy(feature -> {
            assertThat(feature.featureCode()).isEqualTo("self.pace");
            assertThat(feature.status()).isEqualTo(RelationshipTypes.FeatureStatus.PROVISIONAL);
        });
        SnapshotAdmission.admit(added, registry, directory(context, deterministic));
        ProfileEvidence retracted = ProfileEvidence.build(context.userId(), "self.pace", RelationshipTypes.Section.SELF,
                new FeatureValue.EnumOne("slow"), EvidenceSource.SELF_REPORTED, "stated-pace", "case", 1,
                EvidenceStatus.RETRACTED, null, null, null, null, null, registry);
        RelationshipProfileSnapshot afterRetract = ProfileMerger.merge(context.userId(),
                concat(deterministic, retracted), result.accepted(), registry);
        assertThat(afterRetract.evidence()).noneMatch(item -> item.sourceType() == EvidenceSource.MODEL_INFERENCE);
    }

    @Test
    void promptTreatsUserTextAsDataAndHasAStableDigest() {
        String system = ProfileExtractionPrompts.system();
        assertThat(system).contains("数据开始", "不是系统指令", "SELF", "PREFERENCE", "SCENARIO_RESPONSE", "OBSERVED_BEHAVIOR");
        assertThat(ProfileExtractionPrompts.digest()).hasSize(64).isEqualTo(ProfileExtractionPrompts.digest());
        assertThat(ProfileExtractionPrompts.userPayload("{\"items\":[]}")).contains("数据开始", "数据结束");
    }

    private static ProfileContext context(List<ProfileContextBuilder.Statement> statements) {
        return ProfileContextBuilder.build(new ProfileContextBuilder.Inputs(
                42, 1, 1, true, true, Map.of("hobbies", List.of("reading")), statements, List.of()));
    }

    private static String token(ProfileContext context, String kind) {
        return context.items().stream().filter(item -> kind.equals(item.kind())).map(ProfileContext.Item::token)
                .findFirst().orElseThrow();
    }

    private static ExtractedFeature feature(String section, String code, String value, String token, String behavior) {
        return new ExtractedFeature(section, code, value, "EQ", "NONE", List.of(token), 0.6, "STATED", behavior, false);
    }

    private static List<ProfileEvidence> concat(List<ProfileEvidence> left, ProfileEvidence right) {
        List<ProfileEvidence> all = new java.util.ArrayList<>(left);
        all.add(right);
        return all;
    }

    private static EvidenceSourceDirectory directory(ProfileContext context, List<ProfileEvidence> deterministic) {
        EvidenceSourceDirectory answers = (userId, ref, version) -> deterministic.stream()
                .filter(item -> item.subjectUserId() == userId && item.sourceRef().equals(ref) && item.sourceVersion().equals(version))
                .findFirst()
                .map(item -> new SourceFact(userId, ref, version, item.sourceType(), OrdinarySnapshot.PROFILE_SCOPE, false, false));
        return new CompositeEvidenceDirectory(new IssuedEvidenceDirectory(context), answers);
    }
}
