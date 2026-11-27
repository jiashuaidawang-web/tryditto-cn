package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.SemanticSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copies a feature into the recall view only when {@link cn.aiditto.domain.relationship.MatchingFeatureGate} allows it.
 * Unknown, withheld, and unbounded values stay out. Conflicting values are dropped instead of last-write-wins.
 */
public final class RecallFeatureAdmission {
    public record Claim(String code, String value, SemanticSupport support, boolean withheld, boolean unbounded) {
        public RecallProfileContract.Claim explicit(EvidenceSource source) {
            return new RecallProfileContract.Claim(code, value, support, withheld, unbounded, source);
        }
    }

    public record View(Map<String, String> enums, Set<String> hobbies, int dropped) {
        public View {
            enums = Map.copyOf(enums);
            hobbies = Set.copyOf(hobbies);
        }
    }

    private RecallFeatureAdmission() {}

    public static View admit(List<Claim> claims, Set<String> hobbyValues, SemanticSupport hobbySupport) {
        List<RecallProfileContract.Claim> explicit = new ArrayList<>();
        for (Claim claim : claims) explicit.add(claim.explicit(EvidenceSource.SELF_REPORTED));
        RecallProfileContract.Admitted admitted = RecallProfileContract.admit(explicit, hobbyValues, hobbySupport);
        Map<String, String> enums = new java.util.TreeMap<>();
        enums.putAll(admitted.selfEnums());
        enums.putAll(admitted.preferenceEnums());
        return new View(enums, admitted.hobbies(), admitted.dropped());
    }
}
