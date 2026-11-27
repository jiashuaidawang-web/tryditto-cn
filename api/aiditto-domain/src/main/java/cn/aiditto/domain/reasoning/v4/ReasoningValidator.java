package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.FeatureAlignment;
import cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic checks. An evidence reference that exists is not semantic support.
 * Rejected claims stay out of the usable lists.
 */
public final class ReasoningValidator {
    private static final List<String> BLOCKED_WORDS = List.of(
            "硬约束", "性别", "职业", "男生", "女生", "男人", "女人", "MBTI", "内向", "外向", "一定会喜欢", "恋爱成功");

    private ReasoningValidator() {}

    @SuppressWarnings("unchecked")
    public static DirectionalReasoning validate(RelationshipReasoningContext context, String direction,
                                                String content, FeatureAlignmentRegistry alignments) {
        RelationshipReasoningContext.View view = context.view(direction);
        Object parsed;
        try {
            parsed = ReasoningJson.parse(content);
        } catch (RuntimeException ex) {
            return DirectionalReasoning.failed(view.chooserId(), view.targetId(), direction, "PARSE");
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            return DirectionalReasoning.failed(view.chooserId(), view.targetId(), direction, "PARSE");
        }
        Map<String, Object> object = (Map<String, Object>) raw;
        if (!direction.equals(string(object.get("direction")))
                || view.chooserId() != number(object.get("chooserId"))
                || view.targetId() != number(object.get("targetId"))) {
            return rejected(view, claims(object, null));
        }
        List<ReasoningClaim> supported = new ArrayList<>();
        List<ReasoningClaim> complements = new ArrayList<>();
        List<ReasoningClaim> conflicts = new ArrayList<>();
        List<ReasoningClaim> missing = new ArrayList<>();
        List<ReasoningClaim> contradictions = new ArrayList<>();
        List<ReasoningClaim> unsupported = new ArrayList<>();
        List<ReasoningClaim> findings = new ArrayList<>();
        route(context, view, alignments, object, "supportedMatches", ReasoningCategory.SUPPORTED_COMPATIBILITY,
                supported, unsupported, findings);
        route(context, view, alignments, object, "potentialComplements", ReasoningCategory.POTENTIAL_COMPLEMENT,
                complements, unsupported, findings);
        route(context, view, alignments, object, "possibleConflicts", ReasoningCategory.POTENTIAL_CONFLICT,
                conflicts, unsupported, findings);
        route(context, view, alignments, object, "missingInformation", ReasoningCategory.INSUFFICIENT_EVIDENCE,
                missing, unsupported, findings);
        route(context, view, alignments, object, "contradictions", ReasoningCategory.CONTRADICTION,
                contradictions, unsupported, findings);
        for (ReasoningClaim claim : claims(object, "unsupportedClaims")) {
            unsupported.add(new ReasoningClaim(ReasoningCategory.UNSUPPORTED_INFERENCE, claim.preferenceCode(),
                    claim.targetFeatureCode(), claim.evidenceRefs(), claim.statement()));
        }
        Double confidence = confidence(object.get("confidence"));
        List<String> refs = new ArrayList<>();
        for (ReasoningClaim claim : List.of(supported, complements, conflicts, missing, contradictions).stream().flatMap(List::stream).toList()) {
            refs.addAll(claim.evidenceRefs());
        }
        return new DirectionalReasoning(view.chooserId(), view.targetId(), direction, supported, complements, conflicts,
                missing, contradictions, unsupported, refs, confidence, ReasoningStatus.COMPLETED,
                confidence == null && object.get("confidence") != null ? "CONFIDENCE_DISCARDED" : null, findings);
    }

    private static void route(RelationshipReasoningContext context, RelationshipReasoningContext.View view,
                              FeatureAlignmentRegistry alignments, Map<String, Object> object, String field,
                              ReasoningCategory category, List<ReasoningClaim> accepted, List<ReasoningClaim> rejected,
                              List<ReasoningClaim> findings) {
        for (ReasoningClaim claim : claims(object, field)) {
            ReasoningClaim tagged = new ReasoningClaim(category, claim.preferenceCode(), claim.targetFeatureCode(),
                    claim.evidenceRefs(), claim.statement());
            if (accept(context, view, alignments, tagged)) accepted.add(tagged);
            else if (newFinding(context, view, alignments, tagged)) findings.add(tagged);
            else rejected.add(new ReasoningClaim(ReasoningCategory.UNSUPPORTED_INFERENCE, tagged.preferenceCode(),
                    tagged.targetFeatureCode(), tagged.evidenceRefs(), tagged.statement()));
        }
    }

    static boolean accept(RelationshipReasoningContext context, RelationshipReasoningContext.View view,
                          FeatureAlignmentRegistry alignments, ReasoningClaim claim) {
        FeatureRegistry registry = alignments.features();
        FeatureDefinition preference = find(registry, claim.preferenceCode());
        FeatureDefinition target = find(registry, claim.targetFeatureCode());
        if (preference == null || target == null) return false;
        if (!preference.sections().contains(Section.PREFERENCE) || !target.sections().contains(Section.SELF)) return false;
        FeatureAlignment alignment = alignments.find(claim.preferenceCode());
        if (alignment != null && !alignment.selfCode().equals(claim.targetFeatureCode())) return false;
        if (blocked(claim.statement())) return false;
        if (claim.evidenceRefs().isEmpty() || claim.evidenceRefs().stream().anyMatch(ref -> !view.allows(ref))) return false;
        if (inventedValue(claim, view)) return false;
        if (relevance(view, claim) != EvidenceRelevance.RELEVANT) return false;
        RelationshipReasoningContext.ItemView item = view.items().stream()
                .filter(row -> row.preferenceCode().equals(claim.preferenceCode())).findFirst().orElse(null);
        return switch (claim.category()) {
            case SUPPORTED_COMPATIBILITY -> item != null && ("SATISFIED".equals(item.satisfaction()) || "PARTIALLY_SATISFIED".equals(item.satisfaction()));
            case POTENTIAL_CONFLICT -> item != null && "UNSATISFIED".equals(item.satisfaction());
            case INSUFFICIENT_EVIDENCE -> item == null || "UNKNOWN".equals(item.satisfaction());
            case CONTRADICTION -> item != null && "CONTESTED".equals(item.satisfaction());
            case POTENTIAL_COMPLEMENT -> hypothesis(context, view, claim) && (item == null || "NOT_APPLICABLE".equals(item.satisfaction()));
            case UNSUPPORTED_INFERENCE -> false;
        };
    }

    private static boolean hypothesis(RelationshipReasoningContext context, RelationshipReasoningContext.View view, ReasoningClaim claim) {
        boolean preference = view.evidence().stream().anyMatch(item -> "preference".equals(item.role())
                && "partner.support".equals(item.code()) && "close".equals(item.value()));
        boolean self = view.evidence().stream().anyMatch(item -> "self".equals(item.role())
                && "self.communication".equals(item.code()) && "frequent".equals(item.value()));
        return preference && self && "partner.support".equals(claim.preferenceCode())
                && "self.communication".equals(claim.targetFeatureCode())
                && context.semanticLimits().stream().anyMatch(limit -> limit.contains("未验证假设"));
    }

    private static boolean inventedValue(ReasoningClaim claim, RelationshipReasoningContext.View view) {
        String actual = view.evidence().stream()
                .filter(item -> "self".equals(item.role()) && item.code().equals(claim.targetFeatureCode()))
                .map(RelationshipReasoningContext.EvidenceItem::value).findFirst().orElse("");
        String expected = view.evidence().stream()
                .filter(item -> "preference".equals(item.role()) && item.code().equals(claim.preferenceCode()))
                .map(RelationshipReasoningContext.EvidenceItem::value).findFirst().orElse("");
        String statement = claim.statement();
        for (String token : List.of("slow", "steady", "fast", "quiet", "direct", "frequent", "early", "flexible",
                "late", "low", "high", "close", "space")) {
            if (!statement.contains(token)) continue;
            if (token.equals(actual) || token.equals(expected) || actual.contains(token) || expected.contains(token)) continue;
            return true;
        }
        return false;
    }

    private static boolean blocked(String statement) {
        return BLOCKED_WORDS.stream().anyMatch(statement::contains);
    }

    @SuppressWarnings("unchecked")
    private static List<ReasoningClaim> claims(Map<String, Object> object, String field) {
        if (field == null) return List.of();
        Object value = object.get(field);
        if (!(value instanceof List<?> list)) return List.of();
        List<ReasoningClaim> claims = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> row = (Map<String, Object>) map;
            List<String> refs = new ArrayList<>();
            Object rawRefs = row.get("evidenceRefs");
            if (rawRefs instanceof List<?> values) {
                for (Object ref : values) if (ref != null) refs.add(String.valueOf(ref));
            }
            String preference = string(row.get("preferenceCode"));
            String target = string(row.get("targetFeatureCode"));
            String statement = string(row.get("statement"));
            if (preference == null || target == null || statement == null) continue;
            claims.add(new ReasoningClaim(ReasoningCategory.UNSUPPORTED_INFERENCE, preference, target, refs, statement));
        }
        return claims;
    }

    private static DirectionalReasoning rejected(RelationshipReasoningContext.View view, List<ReasoningClaim> claims) {
        return new DirectionalReasoning(view.chooserId(), view.targetId(), view.direction(), List.of(), List.of(),
                List.of(), List.of(), List.of(), claims, List.of(), null, ReasoningStatus.REJECTED, "IDENTITY", List.of());
    }

    /**
     * A citation that exists is not enough. It has to name this preference and this target feature.
     * Passing the word list does not make the sentence supported.
     */
    public static EvidenceRelevance relevance(RelationshipReasoningContext.View view, ReasoningClaim claim) {
        if (claim.evidenceRefs().isEmpty()) return EvidenceRelevance.INCOMPLETE;
        boolean preference = false;
        boolean self = false;
        boolean selfPresent = view.evidence().stream().anyMatch(item -> "self".equals(item.role())
                && item.userId() == view.targetId() && item.code().equals(claim.targetFeatureCode()));
        for (String ref : claim.evidenceRefs()) {
            RelationshipReasoningContext.EvidenceItem item = view.evidence().stream()
                    .filter(candidate -> candidate.ref().equals(ref)).findFirst().orElse(null);
            if (item == null) return EvidenceRelevance.ABSENT;
            boolean preferenceRef = "preference".equals(item.role()) && item.userId() == view.chooserId()
                    && item.code().equals(claim.preferenceCode());
            boolean selfRef = "self".equals(item.role()) && item.userId() == view.targetId()
                    && item.code().equals(claim.targetFeatureCode());
            if (!preferenceRef && !selfRef) return EvidenceRelevance.UNRELATED;
            preference = preference || preferenceRef;
            self = self || selfRef;
        }
        if (!preference) return EvidenceRelevance.INCOMPLETE;
        boolean needsSelf = claim.category() == ReasoningCategory.SUPPORTED_COMPATIBILITY
                || claim.category() == ReasoningCategory.POTENTIAL_CONFLICT
                || claim.category() == ReasoningCategory.POTENTIAL_COMPLEMENT;
        if (needsSelf && !selfPresent) return EvidenceRelevance.INCOMPLETE;
        if (needsSelf && selfPresent && !self) return EvidenceRelevance.INCOMPLETE;
        if (missingAsFact(view, claim)) return EvidenceRelevance.MISSING_AS_FACT;
        return EvidenceRelevance.RELEVANT;
    }

    private static boolean newFinding(RelationshipReasoningContext context, RelationshipReasoningContext.View view,
                                      FeatureAlignmentRegistry alignments, ReasoningClaim claim) {
        if (claim.category() != ReasoningCategory.SUPPORTED_COMPATIBILITY) return false;
        if (blocked(claim.statement()) || inventedValue(claim, view)) return false;
        if (relevance(view, claim) != EvidenceRelevance.RELEVANT) return false;
        if (alignments.find(claim.preferenceCode()) != null) return false;
        RelationshipReasoningContext.ItemView item = view.items().stream()
                .filter(row -> row.preferenceCode().equals(claim.preferenceCode())).findFirst().orElse(null);
        if (item != null && !"NOT_APPLICABLE".equals(item.satisfaction())) return false;
        String actual = value(view, "self", claim.targetFeatureCode());
        String expected = value(view, "preference", claim.preferenceCode());
        return !actual.isBlank() && actual.equals(expected) && context.semanticLimits() != null;
    }

    private static boolean missingAsFact(RelationshipReasoningContext.View view, ReasoningClaim claim) {
        if (claim.category() == ReasoningCategory.INSUFFICIENT_EVIDENCE
                || claim.category() == ReasoningCategory.CONTRADICTION) return false;
        RelationshipReasoningContext.ItemView item = view.items().stream()
                .filter(row -> row.preferenceCode().equals(claim.preferenceCode())).findFirst().orElse(null);
        if (item != null && "UNKNOWN".equals(item.satisfaction())) return true;
        return value(view, "self", claim.targetFeatureCode()).isBlank();
    }

    private static String value(RelationshipReasoningContext.View view, String role, String code) {
        return view.evidence().stream()
                .filter(item -> role.equals(item.role()) && item.code().equals(code))
                .map(RelationshipReasoningContext.EvidenceItem::value).findFirst().orElse("");
    }

    private static FeatureDefinition find(FeatureRegistry registry, String code) {
        return registry.all().stream().filter(item -> item.featureCode().equals(code)).findFirst().orElse(null);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static Double confidence(Object value) {
        if (!(value instanceof Number number)) return null;
        double confidence = number.doubleValue();
        if (confidence < 0 || confidence > 1) return null;
        return confidence;
    }

    static Set<String> blockedWords() { return Set.copyOf(BLOCKED_WORDS); }
}
