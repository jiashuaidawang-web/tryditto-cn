package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceProfile;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.SelfProfile;
import cn.aiditto.domain.relationship.SemanticSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoring input. It keeps the P01 profile, the preference strength, the operator, and the support.
 * It is not the lossy recall enum map, and it is not a second profile model.
 */
public record CompatibilitySubject(
        long userId,
        long profileVersion,
        long generation,
        boolean current,
        boolean withdrawn,
        String fixtureSource,
        SelfProfile self,
        PreferenceProfile preference,
        Map<String, SemanticSupport> selfSupport,
        Map<String, SemanticSupport> preferenceSupport,
        Candidate candidate) {

    public CompatibilitySubject {
        if (userId <= 0 || profileVersion < 1 || generation < 1) throw new IllegalArgumentException("identity");
        if (fixtureSource == null || !fixtureSource.startsWith("synthetic-fixture:")) {
            throw new IllegalArgumentException("only a synthetic fixture can be scored in this round");
        }
        if (self == null || preference == null || candidate == null) throw new IllegalArgumentException("profile");
        if (candidate.id() != userId) throw new IllegalArgumentException("candidate identity");
        selfSupport = Map.copyOf(selfSupport);
        preferenceSupport = Map.copyOf(preferenceSupport);
    }

    public static PreferenceTarget preference(String code, String value, Importance importance) {
        return preference(code, Operator.EQ, new FeatureValue.EnumOne(value), importance, ConstraintType.SOFT, FeatureStatus.CONFIRMED);
    }

    public static PreferenceTarget preference(String code, Operator operator, FeatureValue value, Importance importance,
                                              ConstraintType constraint, FeatureStatus status) {
        Double confidence = status == FeatureStatus.CONTESTED ? null : 1.0;
        return new PreferenceTarget(code, status == FeatureStatus.CONTESTED ? null : operator,
                status == FeatureStatus.CONTESTED ? null : value,
                status == FeatureStatus.CONTESTED ? null : importance,
                constraint == null ? ConstraintType.SOFT : constraint,
                confidence, List.of(), status);
    }

    public static ProfileFeature feature(String code, FeatureValue value, FeatureStatus status) {
        return new ProfileFeature(code, status == FeatureStatus.CONTESTED ? null : value, List.of(), status);
    }

    public static CompatibilitySubject synthetic(long userId, List<ProfileFeature> self, List<PreferenceTarget> preferences, Candidate candidate) {
        return synthetic(userId, 1, 1, true, false, "synthetic-fixture:v4-m02", self, preferences, candidate);
    }

    public static CompatibilitySubject synthetic(long userId, long profileVersion, long generation, boolean current,
                                                 boolean withdrawn, String source, List<ProfileFeature> self,
                                                 List<PreferenceTarget> preferences, Candidate candidate) {
        Map<String, SemanticSupport> selfSupport = new LinkedHashMap<>();
        Map<String, SemanticSupport> preferenceSupport = new LinkedHashMap<>();
        for (ProfileFeature feature : self) selfSupport.put(feature.featureCode(), SemanticSupport.USER_CONFIRMED);
        for (PreferenceTarget target : preferences) preferenceSupport.put(target.featureCode(), SemanticSupport.USER_CONFIRMED);
        return new CompatibilitySubject(userId, profileVersion, generation, current, withdrawn, source,
                new SelfProfile(self), new PreferenceProfile(preferences), selfSupport, preferenceSupport, candidate);
    }

    public CompatibilitySubject support(String code, SemanticSupport selfLevel, SemanticSupport preferenceLevel) {
        Map<String, SemanticSupport> selfSupport = new LinkedHashMap<>(this.selfSupport);
        Map<String, SemanticSupport> preferenceSupport = new LinkedHashMap<>(this.preferenceSupport);
        if (selfLevel == null) selfSupport.remove(code);
        else selfSupport.put(code, selfLevel);
        if (preferenceLevel == null) preferenceSupport.remove(code);
        else preferenceSupport.put(code, preferenceLevel);
        return new CompatibilitySubject(userId, profileVersion, generation, current, withdrawn, fixtureSource,
                self, preference, selfSupport, preferenceSupport, candidate);
    }
}
