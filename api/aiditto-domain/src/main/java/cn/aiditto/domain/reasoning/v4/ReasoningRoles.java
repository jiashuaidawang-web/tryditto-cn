package cn.aiditto.domain.reasoning.v4;

/**
 * A claim that repeats an M02 satisfaction row is an explanation.
 * The registered complement stays a hypothesis. Anything else is not promoted into the score.
 */
public final class ReasoningRoles {
    private ReasoningRoles() {}

    public static ClaimRole of(RelationshipReasoningContext.View view, ReasoningClaim claim) {
        if (claim.category() == ReasoningCategory.POTENTIAL_COMPLEMENT) return ClaimRole.RELATIONSHIP_HYPOTHESIS;
        RelationshipReasoningContext.ItemView item = view.items().stream()
                .filter(row -> row.preferenceCode().equals(claim.preferenceCode())
                        && row.targetFeatureCode().equals(claim.targetFeatureCode()))
                .findFirst().orElse(null);
        if (item == null) return ClaimRole.NEW_SUPPORTED_RELATION;
        boolean explanation = switch (claim.category()) {
            case SUPPORTED_COMPATIBILITY -> "SATISFIED".equals(item.satisfaction()) || "PARTIALLY_SATISFIED".equals(item.satisfaction());
            case POTENTIAL_CONFLICT -> "UNSATISFIED".equals(item.satisfaction());
            case INSUFFICIENT_EVIDENCE -> "UNKNOWN".equals(item.satisfaction());
            case CONTRADICTION -> "CONTESTED".equals(item.satisfaction());
            case POTENTIAL_COMPLEMENT, UNSUPPORTED_INFERENCE -> false;
        };
        return explanation ? ClaimRole.STRUCTURED_EXPLANATION : ClaimRole.NEW_SUPPORTED_RELATION;
    }
}
