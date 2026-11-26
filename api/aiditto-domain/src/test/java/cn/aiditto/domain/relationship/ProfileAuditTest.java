package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The five audit findings. Each example is the case the previous policy got wrong. */
class ProfileAuditTest {
    private final FeatureRegistry registry = FeatureRegistry.v1();

    @Test
    void sameHashAdvancesTheWatermarkAndADelayedSourceIsStale() {
        String contentA = "a".repeat(64);
        String contentB = "b".repeat(64);
        ProfileVersionPolicy.Decision advanced = ProfileVersionPolicy.decide(true, true, 1, 1, 1, 5, 5, true,
                contentA, contentA, ProfileVersionPolicy.WriteMode.DETERMINISTIC);
        assertThat(advanced.kind()).isEqualTo(ProfileVersionPolicy.Kind.KEEP_CURRENT);
        assertThat(advanced.watermark()).isEqualTo(5);
        assertThat(ProfileVersionPolicy.decide(true, true, 1, 1, 5, 3, 3, true, contentA, contentB,
                ProfileVersionPolicy.WriteMode.DETERMINISTIC).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.REJECT_STALE);
    }

    @Test
    void returningToAnOlderHashIsNotRejectedAsADuplicate() {
        ProfileVersionPolicy.Decision again = ProfileVersionPolicy.decide(true, true, 1, 1, 2, 3, 3, true,
                "b".repeat(64), "a".repeat(64), ProfileVersionPolicy.WriteMode.DETERMINISTIC);
        assertThat(again.kind()).isEqualTo(ProfileVersionPolicy.Kind.INSERT);
        assertThat(again.watermark()).isEqualTo(3);
    }

    @Test
    void deterministicConflictIsNotAModelRevision() {
        assertThat(ProfileVersionPolicy.decide(true, true, 1, 1, 4, 4, 4, true,
                "a".repeat(64), "b".repeat(64), ProfileVersionPolicy.WriteMode.DETERMINISTIC).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.REJECT_CONFLICT);
        assertThat(ProfileVersionPolicy.decide(true, true, 1, 1, 4, 4, 4, true,
                "a".repeat(64), "b".repeat(64), ProfileVersionPolicy.WriteMode.MODEL_REVISION).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.ACCEPT_REVISION);
        assertThat(ProfileVersionPolicy.decide(true, true, 1, 1, 4, 5, 9, true,
                "a".repeat(64), "b".repeat(64), ProfileVersionPolicy.WriteMode.DETERMINISTIC).kind())
                .isEqualTo(ProfileVersionPolicy.Kind.REJECT_UNVERIFIED_SOURCE);
    }

    @Test
    void forgedHashCanonicalAndForeignEvidenceAreRejected() {
        RelationshipProfileSnapshot real = project();
        assertThat(SnapshotAdmission.admit(real, registry, honest(real)).inputHash()).isEqualTo(real.inputHash());
        RelationshipProfileSnapshot forgedHash = new RelationshipProfileSnapshot(real.userId(), real.schemaVersion(),
                real.featureRegistryVersion(), real.self(), real.preference(), real.behavior(), real.memory(),
                real.evidence(), real.canonical(), "ab".repeat(32));
        assertThatThrownBy(() -> SnapshotAdmission.admit(forgedHash, registry, honest(real)))
                .isInstanceOf(IllegalArgumentException.class);
        RelationshipProfileSnapshot forgedCanonical = new RelationshipProfileSnapshot(real.userId(), real.schemaVersion(),
                real.featureRegistryVersion(), real.self(), real.preference(), real.behavior(), real.memory(),
                real.evidence(), real.canonical() + " ", real.inputHash());
        assertThatThrownBy(() -> SnapshotAdmission.admit(forgedCanonical, registry, honest(real)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, (user, ref, version) -> Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(RelationshipProfileCases.complementaryA(), registry,
                honest(RelationshipProfileCases.complementaryA()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simulatedFeedbackNeedsNoSpecialPrefixAndScopesStaySeparate() {
        RelationshipProfileSnapshot real = project();
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, fact(real, EvidenceSource.DIRECT_ANSWER,
                OrdinarySnapshot.PROFILE_SCOPE, false, true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, fact(real, EvidenceSource.SELF_REPORTED,
                OrdinarySnapshot.PROFILE_SCOPE, false, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, fact(real, EvidenceSource.DIRECT_ANSWER,
                OrdinarySnapshot.MATCHING_SCOPE, false, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, fact(real, EvidenceSource.DIRECT_ANSWER,
                OrdinarySnapshot.MODEL_EXPORT_SCOPE, false, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotAdmission.admit(real, registry, fact(real, EvidenceSource.DIRECT_ANSWER,
                OrdinarySnapshot.PROFILE_SCOPE, true, false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileEvidence(
                EvidenceIds.compute(8, EvidenceSource.DIRECT_ANSWER, "secret@example.com", EvidenceIds.CATALOG, "lifestyle.hobbies"),
                8, "lifestyle.hobbies", RelationshipTypes.Section.SELF, FeatureValue.enumMany("reading"),
                EvidenceSource.DIRECT_ANSWER, "secret@example.com", EvidenceIds.CATALOG, 1, EvidenceStatus.CONFIRMED,
                RelationshipTypes.Visibility.OWNER, OrdinarySnapshot.PROFILE_SCOPE, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RelationshipProfileSnapshot project() {
        return RelationshipProfileProjector.project(8, Map.of(
                "hobbies", java.util.List.of("reading"),
                "relationshipLooking", java.util.List.of("serious")), registry);
    }

    private static EvidenceSourceDirectory honest(RelationshipProfileSnapshot snapshot) {
        return fact(snapshot, null, OrdinarySnapshot.PROFILE_SCOPE, false, false);
    }

    private static EvidenceSourceDirectory fact(RelationshipProfileSnapshot snapshot, EvidenceSource forcedType,
                                                String scope, boolean retracted, boolean simulated) {
        return (userId, sourceRef, sourceVersion) -> snapshot.evidence().stream()
                .filter(item -> item.subjectUserId() == userId && item.sourceRef().equals(sourceRef)
                        && item.sourceVersion().equals(sourceVersion))
                .findFirst()
                .map(item -> new SourceFact(userId, sourceRef, sourceVersion,
                        forcedType == null ? item.sourceType() : forcedType, scope, retracted, simulated));
    }
}
