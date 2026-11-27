package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.List;

/** One direction after validation. Confidence is the model's own report, not a match probability. */
public record DirectionalReasoning(
        long chooserId,
        long targetId,
        String direction,
        List<ReasoningClaim> supportedMatches,
        List<ReasoningClaim> potentialComplements,
        List<ReasoningClaim> possibleConflicts,
        List<ReasoningClaim> missingInformation,
        List<ReasoningClaim> contradictions,
        List<ReasoningClaim> unsupportedClaims,
        List<String> evidenceRefs,
        Double confidence,
        ReasoningStatus status,
        String failure) {

    public DirectionalReasoning {
        supportedMatches = copy(supportedMatches);
        potentialComplements = copy(potentialComplements);
        possibleConflicts = copy(possibleConflicts);
        missingInformation = copy(missingInformation);
        contradictions = copy(contradictions);
        unsupportedClaims = copy(unsupportedClaims);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        if (direction == null || status == null) throw new IllegalArgumentException("reasoning");
    }

    public List<ReasoningClaim> usable() {
        List<ReasoningClaim> all = new ArrayList<>();
        all.addAll(supportedMatches);
        all.addAll(potentialComplements);
        all.addAll(possibleConflicts);
        all.addAll(missingInformation);
        all.addAll(contradictions);
        return List.copyOf(all);
    }

    static DirectionalReasoning refused(long chooserId, long targetId, String direction, String reason) {
        return empty(chooserId, targetId, direction, ReasoningStatus.REFUSED, reason);
    }

    static DirectionalReasoning failed(long chooserId, long targetId, String direction, String reason) {
        return empty(chooserId, targetId, direction, ReasoningStatus.MODEL_FAILED, reason);
    }

    private static DirectionalReasoning empty(long chooserId, long targetId, String direction,
                                              ReasoningStatus status, String reason) {
        return new DirectionalReasoning(chooserId, targetId, direction, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, status, reason);
    }

    private static List<ReasoningClaim> copy(List<ReasoningClaim> claims) {
        return claims == null ? List.of() : List.copyOf(claims);
    }
}
