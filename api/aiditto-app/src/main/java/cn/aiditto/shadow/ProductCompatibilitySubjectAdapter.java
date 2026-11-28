package cn.aiditto.shadow;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.SemanticSupport;
import cn.aiditto.match.People;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a stored profile into the existing scorer input.
 * Support is attached only to confirmed features. Nothing here copies a self trait into a preference.
 */
@Component
public class ProductCompatibilitySubjectAdapter {
    public static final String SOURCE = "synthetic-fixture:isolated-business";

    public CompatibilitySubject adapt(People.Member member, RelationshipProfileSnapshot snapshot, long profileVersion, long generation) {
        return new CompatibilitySubject(member.userId(), profileVersion, generation, true, false, SOURCE,
                snapshot.self(), snapshot.preference(), supportSelf(snapshot), supportPreference(snapshot), member.candidate());
    }

    private static Map<String, SemanticSupport> supportSelf(RelationshipProfileSnapshot snapshot) {
        Map<String, SemanticSupport> support = new LinkedHashMap<>();
        for (ProfileFeature feature : snapshot.self().features()) {
            if (feature.status() == FeatureStatus.CONFIRMED && feature.value() != null) {
                support.put(feature.featureCode(), SemanticSupport.SEMANTIC_SUPPORTED);
            }
        }
        return support;
    }

    private static Map<String, SemanticSupport> supportPreference(RelationshipProfileSnapshot snapshot) {
        Map<String, SemanticSupport> support = new LinkedHashMap<>();
        for (PreferenceTarget target : snapshot.preference().targets()) {
            if (target.status() == FeatureStatus.CONFIRMED && target.targetValue() != null) {
                support.put(target.featureCode(), SemanticSupport.SEMANTIC_SUPPORTED);
            }
        }
        return support;
    }
}
