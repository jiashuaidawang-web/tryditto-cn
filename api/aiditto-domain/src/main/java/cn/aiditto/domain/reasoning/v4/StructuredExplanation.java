package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side reading of the M02 rows. It does not call a model and does not change a score.
 */
public final class StructuredExplanation {
    private StructuredExplanation() {}

    public static DirectionalReasoning explain(RelationshipReasoningContext context, String direction) {
        RelationshipReasoningContext.View view = context.view(direction);
        if (context.sealed()) {
            return DirectionalReasoning.refused(view.chooserId(), view.targetId(), direction, "NOT_ELIGIBLE_FOR_REASONING");
        }
        List<ReasoningClaim> supported = new ArrayList<>();
        List<ReasoningClaim> conflicts = new ArrayList<>();
        List<ReasoningClaim> missing = new ArrayList<>();
        List<ReasoningClaim> contradictions = new ArrayList<>();
        List<ReasoningClaim> complements = new ArrayList<>();
        for (RelationshipReasoningContext.ItemView item : view.items()) {
            ReasoningClaim claim = claim(view, item);
            switch (claim.category()) {
                case SUPPORTED_COMPATIBILITY -> supported.add(claim);
                case POTENTIAL_CONFLICT -> conflicts.add(claim);
                case INSUFFICIENT_EVIDENCE -> missing.add(claim);
                case CONTRADICTION -> contradictions.add(claim);
                default -> { }
            }
        }
        if (value(view, "preference", "partner.support").equals("close")
                && value(view, "self", "self.communication").equals("frequent")) {
            complements.add(new ReasoningClaim(ReasoningCategory.POTENTIAL_COMPLEMENT, "partner.support", "self.communication",
                    refs(view, "partner.support", "self.communication"),
                    "未验证假设只记为潜在互补。"));
        }
        return new DirectionalReasoning(view.chooserId(), view.targetId(), direction, supported, complements, conflicts,
                missing, contradictions, List.of(), List.of(), null, ReasoningStatus.COMPLETED, null, List.of());
    }

    private static ReasoningClaim claim(RelationshipReasoningContext.View view, RelationshipReasoningContext.ItemView item) {
        ReasoningCategory category = switch (item.satisfaction()) {
            case "SATISFIED", "PARTIALLY_SATISFIED" -> ReasoningCategory.SUPPORTED_COMPATIBILITY;
            case "UNSATISFIED" -> ReasoningCategory.POTENTIAL_CONFLICT;
            case "UNKNOWN" -> ReasoningCategory.INSUFFICIENT_EVIDENCE;
            case "CONTESTED" -> ReasoningCategory.CONTRADICTION;
            default -> ReasoningCategory.UNSUPPORTED_INFERENCE;
        };
        return new ReasoningClaim(category, item.preferenceCode(), item.targetFeatureCode(),
                refs(view, item.preferenceCode(), item.targetFeatureCode()),
                "结构化结果 " + item.satisfaction());
    }

    private static List<String> refs(RelationshipReasoningContext.View view, String preference, String target) {
        List<String> refs = new ArrayList<>();
        view.evidence().stream().filter(item -> "preference".equals(item.role()) && item.code().equals(preference))
                .map(RelationshipReasoningContext.EvidenceItem::ref).findFirst().ifPresent(refs::add);
        view.evidence().stream().filter(item -> "self".equals(item.role()) && item.code().equals(target))
                .map(RelationshipReasoningContext.EvidenceItem::ref).findFirst().ifPresent(refs::add);
        return refs;
    }

    private static String value(RelationshipReasoningContext.View view, String role, String code) {
        return view.evidence().stream().filter(item -> role.equals(item.role()) && item.code().equals(code))
                .map(RelationshipReasoningContext.EvidenceItem::value).findFirst().orElse("");
    }
}
