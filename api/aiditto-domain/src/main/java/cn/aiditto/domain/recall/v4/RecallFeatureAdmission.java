package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.relationship.MatchingFeatureGate;
import cn.aiditto.domain.relationship.SemanticSupport;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Copies a feature into the recall view only when {@link MatchingFeatureGate} allows it.
 * Unknown, withheld, and unbounded values stay out. They are not treated as neutral matches.
 * Model inferences that are not semantically supported stay out, so they cannot become a hard constraint.
 */
public final class RecallFeatureAdmission {
    public record Claim(String code, String value, SemanticSupport support, boolean withheld, boolean unbounded) {}

    public record View(Map<String, String> enums, Set<String> hobbies, int dropped) {
        public View {
            enums = Map.copyOf(enums);
            hobbies = Set.copyOf(hobbies);
        }
    }

    private RecallFeatureAdmission() {}

    public static View admit(List<Claim> claims, Set<String> hobbyValues, SemanticSupport hobbySupport) {
        Map<String, String> enums = new TreeMap<>();
        int dropped = 0;
        for (Claim claim : claims) {
            if (!keep(claim)) {
                dropped++;
                continue;
            }
            enums.put(claim.code(), claim.value());
        }
        Set<String> hobbies = new TreeSet<>();
        if (hobbyValues != null && MatchingFeatureGate.eligibleForOfficialScore(hobbySupport)) {
            for (String hobby : hobbyValues) {
                if (hobby != null && !hobby.isBlank() && !"UNKNOWN".equals(hobby)) hobbies.add(hobby);
            }
        } else if (hobbyValues != null && !hobbyValues.isEmpty()) {
            dropped++;
        }
        return new View(enums, hobbies, dropped);
    }

    private static boolean keep(Claim claim) {
        if (claim == null || claim.code() == null || claim.code().isBlank()) return false;
        if (claim.withheld() || claim.unbounded()) return false;
        if (claim.value() == null || claim.value().isBlank() || "UNKNOWN".equals(claim.value())) return false;
        if (claim.code().startsWith("occupation.") || claim.code().startsWith("opening.")) return false;
        return MatchingFeatureGate.eligibleForOfficialScore(claim.support());
    }
}
