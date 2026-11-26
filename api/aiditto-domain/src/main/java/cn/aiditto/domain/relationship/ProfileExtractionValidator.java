package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.BehaviorKind;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects model candidates that the registry, the issued context, or the section rules cannot support.
 * A citation proves the source was in this context. It does not prove the conclusion.
 * Accepted model evidence stays provisional.
 */
public final class ProfileExtractionValidator {
    private ProfileExtractionValidator() {}

    public record Rejection(String featureCode, String reason) {}

    public record Verdict(String featureCode, SemanticSupport support) {}

    public record Result(List<ProfileEvidence> accepted, List<Rejection> rejected, List<Verdict> verdicts,
                         int invalidRefs, int hardRequests) {
        public Result {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
            verdicts = List.copyOf(verdicts);
        }
    }

    public static Result check(ProfileContext context, List<ExtractedFeature> proposed, FeatureRegistry registry) {
        List<ProfileEvidence> accepted = new ArrayList<>();
        List<Rejection> rejected = new ArrayList<>();
        List<Verdict> verdicts = new ArrayList<>();
        int invalidRefs = 0;
        int hardRequests = 0;
        if (proposed == null) return new Result(List.of(), List.of(), List.of(), 0, 0);
        for (ExtractedFeature feature : proposed) {
            if (feature.hardConstraint()) hardRequests++;
            String code = feature.featureCode() == null ? "" : feature.featureCode();
            try {
                Outcome outcome = one(context, feature, registry);
                verdicts.add(new Verdict(code, outcome.support()));
                if (outcome.support() == SemanticSupport.SEMANTIC_SUPPORTED) accepted.addAll(outcome.evidence());
                else rejected.add(new Rejection(code, outcome.support().name()));
            } catch (IllegalArgumentException e) {
                if ("invalid-ref".equals(e.getMessage())) invalidRefs++;
                verdicts.add(new Verdict(code, SemanticSupport.REJECTED));
                rejected.add(new Rejection(code, e.getMessage()));
            }
        }
        return new Result(accepted, rejected, verdicts, invalidRefs, hardRequests);
    }

    private record Outcome(SemanticSupport support, List<ProfileEvidence> evidence) {}

    private static Outcome one(ProfileContext context, ExtractedFeature feature, FeatureRegistry registry) {
        if (feature.hardConstraint()) throw new IllegalArgumentException("hard-constraint");
        if (!Double.isFinite(feature.confidence()) || feature.confidence() < 0 || feature.confidence() > 1) {
            throw new IllegalArgumentException("confidence");
        }
        if (feature.proposedValue() == null || feature.proposedValue().isBlank()
                || "UNKNOWN".equals(feature.proposedValue()) || "UNBOUNDED".equals(feature.proposedValue())) {
            throw new IllegalArgumentException("unknown-or-unbounded");
        }
        if (!OrdinarySnapshot.safeToken(feature.reasonCode() == null ? "UNSTATED" : feature.reasonCode())) {
            throw new IllegalArgumentException("reason");
        }
        Section section = section(feature.section());
        if (section == Section.MEMORY) throw new IllegalArgumentException("memory");
        FeatureDefinition definition = registry.require(feature.featureCode());
        if (definition.sensitivity() == RelationshipTypes.Sensitivity.SENSITIVE_REF) {
            throw new IllegalArgumentException("sensitive");
        }
        if (!definition.allows(section, EvidenceSource.MODEL_INFERENCE)) throw new IllegalArgumentException("source");
        if (feature.evidenceRefs().isEmpty()) throw new IllegalArgumentException("ungrounded");
        SemanticSupport support = SemanticSupport.SEMANTIC_SUPPORTED;
        for (String ref : feature.evidenceRefs()) {
            ProfileContext.Item item = context.item(ref);
            if (item == null) throw new IllegalArgumentException("invalid-ref");
            if (!eligible(item, section, feature)) throw new IllegalArgumentException("unsupported");
            support = weaker(support, supportOf(item, section, feature));
        }
        if (support != SemanticSupport.SEMANTIC_SUPPORTED) return new Outcome(support, List.of());
        FeatureValue claim = claim(definition, feature.proposedValue());
        BehaviorKind behavior = behavior(section, feature.behaviorKind());
        Operator operator = section == Section.PREFERENCE ? Operator.EQ : null;
        Importance importance = section == Section.PREFERENCE ? importance(feature.importance()) : null;
        ConstraintType constraint = section == Section.PREFERENCE ? ConstraintType.SOFT : null;
        List<ProfileEvidence> evidence = new ArrayList<>();
        for (String ref : feature.evidenceRefs()) {
            evidence.add(ProfileEvidence.build(context.userId(), feature.featureCode(), section, claim,
                    EvidenceSource.MODEL_INFERENCE, ref, context.fingerprintToken(), feature.confidence(),
                    EvidenceStatus.PROVISIONAL, operator, importance, constraint, behavior, null, registry));
        }
        return new Outcome(SemanticSupport.SEMANTIC_SUPPORTED, evidence);
    }

    private static SemanticSupport weaker(SemanticSupport left, SemanticSupport right) {
        if (left == SemanticSupport.REJECTED || right == SemanticSupport.REJECTED) return SemanticSupport.REJECTED;
        if (left == SemanticSupport.SEMANTIC_SUPPORT_PENDING || right == SemanticSupport.SEMANTIC_SUPPORT_PENDING) {
            return SemanticSupport.SEMANTIC_SUPPORT_PENDING;
        }
        return SemanticSupport.SEMANTIC_SUPPORTED;
    }

    private static SemanticSupport supportOf(ProfileContext.Item item, Section section, ExtractedFeature feature) {
        return switch (item.kind()) {
            case "SELF_STATEMENT", "PREFERENCE_STATEMENT" -> StatementSemantics.judge(
                    item.text(), section, feature.featureCode(), feature.proposedValue(), feature.importance());
            case "SCENARIO" -> feature.proposedValue().equals(item.approvedMeaning())
                    ? SemanticSupport.SEMANTIC_SUPPORTED : SemanticSupport.REJECTED;
            case "QUESTIONNAIRE" -> SemanticSupport.REJECTED;
            default -> SemanticSupport.REJECTED;
        };
    }

    private static boolean eligible(ProfileContext.Item item, Section section, ExtractedFeature feature) {
        return switch (item.kind()) {
            case "SELF_STATEMENT" -> section == Section.SELF && blank(feature.behaviorKind());
            case "PREFERENCE_STATEMENT" -> section == Section.PREFERENCE && blank(feature.behaviorKind());
            case "SCENARIO" -> section == Section.BEHAVIOR
                    && "behavior.scenario.signal".equals(feature.featureCode())
                    && "SCENARIO_RESPONSE".equals(feature.behaviorKind())
                    && item.approvedMeaning() != null;
            case "QUESTIONNAIRE" -> false;
            default -> false;
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Section section(String raw) {
        try {
            return Section.valueOf(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("section");
        }
    }

    private static BehaviorKind behavior(Section section, String raw) {
        if (section != Section.BEHAVIOR) {
            if (!blank(raw) && !"SELF_REPORTED".equals(raw)) throw new IllegalArgumentException("behavior");
            return null;
        }
        if ("SCENARIO_RESPONSE".equals(raw) || "SELF_REPORTED".equals(raw)) return BehaviorKind.valueOf(raw);
        throw new IllegalArgumentException("observed-or-missing-behavior");
    }

    private static Importance importance(String raw) {
        if (raw == null || raw.isBlank()) return Importance.NONE;
        try {
            return Importance.valueOf(raw);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("importance");
        }
    }

    private static FeatureValue claim(FeatureDefinition definition, String raw) {
        return switch (definition.valueType()) {
            case ENUM -> {
                if (!definition.allowedValues().contains(raw)) throw new IllegalArgumentException("value");
                yield new FeatureValue.EnumOne(raw);
            }
            case ENUM_SET -> {
                List<String> values = List.of(raw.split(","));
                if (!definition.allowedValues().containsAll(values)) throw new IllegalArgumentException("value");
                yield new FeatureValue.EnumMany(values);
            }
            case BOOLEAN -> {
                if (!"true".equals(raw) && !"false".equals(raw)) throw new IllegalArgumentException("value");
                yield new FeatureValue.Bool("true".equals(raw));
            }
            default -> throw new IllegalArgumentException("value-type");
        };
    }
}
