package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.util.ArrayList;
import java.util.List;

public final class GlobalMatchPeople {
    public static final String SOURCE = "synthetic-fixture:v4-m04";
    private static final String[] SELF = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
    private static final String[] PREF = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};

    private GlobalMatchPeople() {}

    public static CompatibilitySubject placeholder(long id) {
        return CompatibilitySubject.synthetic(id, 1, 1, true, false, SOURCE, List.of(), List.of(), Candidate.builder(id).build());
    }

    public static CompatibilitySubject person(long id, String[] self, String[] preference, Importance importance,
                                              boolean current, boolean withdrawn) {
        List<cn.aiditto.domain.relationship.ProfileFeature> features = new ArrayList<>();
        List<cn.aiditto.domain.relationship.PreferenceTarget> targets = new ArrayList<>();
        int n = Math.min(self.length, preference.length);
        for (int i = 0; i < n; i++) {
            features.add(CompatibilitySubject.feature(SELF[i], new FeatureValue.EnumOne(self[i]), FeatureStatus.CONFIRMED));
            targets.add(CompatibilitySubject.preference(PREF[i], preference[i], importance));
        }
        return CompatibilitySubject.synthetic(id, 1, 1, current, withdrawn, SOURCE, features, targets, Candidate.builder(id).build());
    }

    public static int structuralHits(CompatibilitySubject left, CompatibilitySubject right) {
        return direction(left, right) + direction(right, left);
    }

    private static int direction(CompatibilitySubject chooser, CompatibilitySubject target) {
        int hits = 0;
        for (var preference : chooser.preference().targets()) {
            if (!(preference.targetValue() instanceof FeatureValue.EnumOne wanted)) continue;
            String selfCode = preference.featureCode().startsWith("partner.")
                    ? "self." + preference.featureCode().substring("partner.".length()) : null;
            if (selfCode == null) continue;
            for (var feature : target.self().features()) {
                if (selfCode.equals(feature.featureCode())
                        && feature.value() instanceof FeatureValue.EnumOne actual
                        && wanted.value().equals(actual.value())) {
                    hits++;
                }
            }
        }
        return hits;
    }
}
