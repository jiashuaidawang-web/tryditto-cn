package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fictional snapshots. They are not inserted into a database. */
final class RelationshipProfileCases {
    static final FeatureRegistry REGISTRY = FeatureRegistry.v1();
    static final long A = 9101;
    static final long B = 9102;
    static final long C = 9103;
    static final long D = 9104;
    static final long E = 9105;
    static final long F = 9106;

    private RelationshipProfileCases() {}

    static Map<String, RelationshipProfileSnapshot> all() {
        Map<String, RelationshipProfileSnapshot> out = new LinkedHashMap<>();
        out.put("case-1-a", complementaryA());
        out.put("case-1-b", complementaryB());
        out.put("case-2-similar-pref-reading", similarReading());
        out.put("case-2-similar-pref-gym", similarGym());
        out.put("case-3-one-way-a", oneWayA());
        out.put("case-3-one-way-b", oneWayB());
        out.put("case-4-cold-start", coldStart());
        out.put("case-5-before", preferenceBefore());
        out.put("case-5-after", preferenceAfter());
        out.put("case-6-contested", contested());
        return out;
    }

    static RelationshipProfileSnapshot complementaryA() {
        return snap(A, List.of(
                selfEnum(A, "self.social.initiative", "low", "case-1-a-initiative"),
                selfSet(A, "lifestyle.hobbies", List.of("reading"), "case-1-a-hobbies"),
                selfText(A, "lifestyle.stated_interest", "摄影", "case-1-a-stated"),
                preference(A, "partner.social.initiative", "high", "case-1-a-wants")));
    }

    static RelationshipProfileSnapshot complementaryB() {
        return snap(B, List.of(
                selfEnum(B, "self.social.initiative", "high", "case-1-b-initiative"),
                selfSet(B, "lifestyle.hobbies", List.of("music", "travel"), "case-1-b-hobbies"),
                preference(B, "partner.social.initiative", "low", "case-1-b-wants")));
    }

    static RelationshipProfileSnapshot similarReading() {
        return snap(C, List.of(
                selfSet(C, "lifestyle.hobbies", List.of("reading"), "case-2-reading"),
                preference(C, "partner.social.initiative", "high", "case-2-reading-wants")));
    }

    static RelationshipProfileSnapshot similarGym() {
        return snap(D, List.of(
                selfSet(D, "lifestyle.hobbies", List.of("gym"), "case-2-gym"),
                preference(D, "partner.social.initiative", "high", "case-2-gym-wants")));
    }

    static RelationshipProfileSnapshot oneWayA() {
        return snap(A, List.of(
                selfEnum(A, "self.pace", "slow", "case-3-a-pace"),
                preference(A, "partner.pace", "fast", "case-3-a-wants")));
    }

    static RelationshipProfileSnapshot oneWayB() {
        return snap(B, List.of(selfEnum(B, "self.pace", "slow", "case-3-b-pace")));
    }

    static RelationshipProfileSnapshot coldStart() {
        return RelationshipProfileProjector.project(E, Map.of("relationshipLooking", List.of("serious")), REGISTRY);
    }

    static RelationshipProfileSnapshot preferenceBefore() {
        return RelationshipProfileProjector.project(F, Map.of(
                "hobbies", List.of("reading"),
                "expectedAgeRange", List.of(22, 28),
                "educationImportance", "kinda"), REGISTRY);
    }

    static RelationshipProfileSnapshot preferenceAfter() {
        return RelationshipProfileProjector.project(F, Map.of(
                "hobbies", List.of("reading"),
                "expectedAgeRange", List.of(26, 34),
                "educationImportance", "super"), REGISTRY);
    }

    static RelationshipProfileSnapshot contested() {
        ProfileEvidence low = selfEnum(A, "self.social.initiative", "low", "case-6-low");
        ProfileEvidence high = selfEnum(A, "self.social.initiative", "high", "case-6-high");
        ProfileEvidence retracted = ProfileEvidence.build(A, "self.social.initiative", Section.SELF,
                new FeatureValue.EnumOne("steady"), EvidenceSource.SELF_REPORTED, "case-6-retracted", "case",
                1.0, EvidenceStatus.RETRACTED, null, null, null, null, null, REGISTRY);
        return snap(A, List.of(high, retracted, low));
    }

    private static RelationshipProfileSnapshot snap(long userId, List<ProfileEvidence> evidence) {
        return RelationshipProfile.of(userId, evidence, REGISTRY).snapshot(REGISTRY);
    }

    private static ProfileEvidence selfEnum(long userId, String code, String value, String ref) {
        return ProfileEvidence.build(userId, code, Section.SELF, new FeatureValue.EnumOne(value),
                EvidenceSource.SELF_REPORTED, ref, "case", 1.0, EvidenceStatus.CONFIRMED,
                null, null, null, null, null, REGISTRY);
    }

    private static ProfileEvidence selfSet(long userId, String code, List<String> values, String ref) {
        return ProfileEvidence.build(userId, code, Section.SELF, new FeatureValue.EnumMany(values),
                EvidenceSource.SELF_REPORTED, ref, "case", 1.0, EvidenceStatus.CONFIRMED,
                null, null, null, null, null, REGISTRY);
    }

    private static ProfileEvidence selfText(long userId, String code, String value, String ref) {
        return ProfileEvidence.build(userId, code, Section.SELF, new FeatureValue.Text(value),
                EvidenceSource.SELF_REPORTED, ref, "case", 1.0, EvidenceStatus.CONFIRMED,
                null, null, null, null, null, REGISTRY);
    }

    private static ProfileEvidence preference(long userId, String code, String value, String ref) {
        return ProfileEvidence.build(userId, code, Section.PREFERENCE, new FeatureValue.EnumOne(value),
                EvidenceSource.SELF_REPORTED, ref, "case", 0.9, EvidenceStatus.CONFIRMED,
                Operator.EQ, Importance.KINDA, ConstraintType.SOFT, null, null, REGISTRY);
    }
}
