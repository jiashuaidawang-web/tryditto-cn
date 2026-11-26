package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.BehaviorKind;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Polarity;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Visibility;

/**
 * One piece of support for one feature. The id is computed by the server from the user and the source.
 * A model-supplied id that does not match that computation is rejected.
 * Confidence is how strongly this evidence supports the claim. It is not the probability the user
 * objectively has the trait, and it is not {@link Importance}.
 */
public record ProfileEvidence(
        String evidenceId,
        long subjectUserId,
        String featureCode,
        Section section,
        FeatureValue claim,
        EvidenceSource sourceType,
        String sourceRef,
        String sourceVersion,
        double confidence,
        EvidenceStatus status,
        Visibility visibility,
        String consentScope,
        Operator operator,
        Importance importance,
        ConstraintType constraintType,
        BehaviorKind behaviorKind,
        Polarity polarity) {

    public ProfileEvidence {
        if (subjectUserId <= 0) throw new IllegalArgumentException("subjectUserId");
        if (featureCode == null || featureCode.isBlank()) throw new IllegalArgumentException("featureCode");
        if (section == null || claim == null || sourceType == null || status == null || visibility == null) {
            throw new IllegalArgumentException("evidence fields");
        }
        if (sourceRef == null || sourceRef.isBlank() || sourceVersion == null || sourceVersion.isBlank()) {
            throw new IllegalArgumentException("source");
        }
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be finite and inside 0..1");
        }
        if (consentScope == null || consentScope.isBlank()) throw new IllegalArgumentException("consentScope");
        String expected = EvidenceIds.compute(subjectUserId, sourceType, sourceRef, sourceVersion, featureCode);
        if (evidenceId == null || !evidenceId.equals(expected)) {
            throw new IllegalArgumentException("evidence id is not the server id for this user and source");
        }
        if (section == Section.PREFERENCE && (operator == null || importance == null || constraintType == null)) {
            throw new IllegalArgumentException("preference evidence needs operator, importance, and constraint");
        }
        if (section == Section.BEHAVIOR && behaviorKind == null) throw new IllegalArgumentException("behavior kind");
        if (section == Section.MEMORY && polarity == null) throw new IllegalArgumentException("memory polarity");
        if (sourceType == EvidenceSource.MODEL_INFERENCE && status != EvidenceStatus.PROVISIONAL) {
            throw new IllegalArgumentException("model inference stays provisional until the user confirms it");
        }
        if (constraintType == ConstraintType.DOCUMENTED_EXISTING_RULE
                && (sourceType != EvidenceSource.DIRECT_ANSWER || status != EvidenceStatus.CONFIRMED)) {
            throw new IllegalArgumentException("only a confirmed direct answer can point at an existing hard rule");
        }
        if (constraintType == ConstraintType.DOCUMENTED_EXISTING_RULE && !EXISTING_RULES.contains(featureCode)) {
            throw new IllegalArgumentException("a new preference cannot be marked as an existing hard rule");
        }
        if (sourceType == EvidenceSource.MODEL_INFERENCE && section == Section.PREFERENCE
                && constraintType != ConstraintType.SOFT) {
            throw new IllegalArgumentException("model inference cannot become a hard or unbounded preference");
        }
        if (behaviorKind == BehaviorKind.OBSERVED_BEHAVIOR && sourceType != EvidenceSource.FEEDBACK_OBSERVATION) {
            throw new IllegalArgumentException("only a real feedback observation can be observed behavior");
        }
        if (behaviorKind == BehaviorKind.OBSERVED_BEHAVIOR && simulated(sourceRef)) {
            throw new IllegalArgumentException("simulated content is not observed behavior");
        }
        if (section == Section.MEMORY && sourceType != EvidenceSource.FEEDBACK_OBSERVATION) {
            throw new IllegalArgumentException("memory only comes from a real feedback observation");
        }
    }

    private static final java.util.Set<String> EXISTING_RULES = java.util.Set.of(
            "partner.age", "partner.height", "partner.education", "partner.gender");

    private static boolean simulated(String sourceRef) {
        String ref = sourceRef.toLowerCase(java.util.Locale.ROOT);
        return ref.startsWith("voice-") || ref.startsWith("voice:")
                || ref.startsWith("persona-") || ref.startsWith("persona:")
                || ref.startsWith("llm-") || ref.startsWith("llm:")
                || ref.startsWith("sim-") || ref.startsWith("sim:");
    }

    public static ProfileEvidence answer(long userId, String featureCode, Section section, FeatureValue claim,
                                         String sourceRef, FeatureRegistry registry) {
        return build(userId, featureCode, section, claim, RelationshipTypes.EvidenceSource.DIRECT_ANSWER,
                sourceRef, EvidenceIds.CATALOG, 1.0, EvidenceStatus.CONFIRMED, null, null, null, null, null, registry);
    }

    public static ProfileEvidence build(long userId, String featureCode, Section section, FeatureValue claim,
                                        EvidenceSource source, String sourceRef, String sourceVersion, double confidence,
                                        EvidenceStatus status, Operator operator, Importance importance,
                                        ConstraintType constraint, BehaviorKind behaviorKind, Polarity polarity,
                                        FeatureRegistry registry) {
        FeatureDefinition definition = registry.require(featureCode);
        if (!definition.allows(section, source)) {
            throw new IllegalArgumentException("source " + source + " cannot support " + featureCode + " in " + section);
        }
        registry.checkValue(definition, claim);
        if (definition.sensitivity() == RelationshipTypes.Sensitivity.SENSITIVE_REF && !(claim instanceof FeatureValue.ControlledRef)) {
            throw new IllegalArgumentException("sensitive feature must be a controlled reference, not a copied value");
        }
        return new ProfileEvidence(
                EvidenceIds.compute(userId, source, sourceRef, sourceVersion, featureCode),
                userId, featureCode, section, claim, source, sourceRef, sourceVersion, confidence, status,
                Visibility.OWNER, "profile-self", operator, importance, constraint, behaviorKind, polarity);
    }
}
