package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.OnboardingFieldMap;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;

import java.util.TreeSet;

/**
 * How much of a stored profile can enter M02. This does not change the scorer or the B5 floor.
 */
public final class BusinessFeatureCoverage {
    private BusinessFeatureCoverage() {}

    public record Report(int alignmentTargets, int scoreableAlignments, int noAlignmentTarget, int legalUnscored, int unknown) {}

    public record ContractGap(int mappedFeatures, int alignmentPreferences, int alignmentSelfFeatures, int overlap) {}

    public static Report of(RelationshipProfileSnapshot snapshot) {
        FeatureAlignmentRegistry registry = FeatureAlignmentRegistry.v1();
        TreeSet<String> aligned = new TreeSet<>();
        for (FeatureAlignment alignment : registry.alignments()) {
            aligned.add(alignment.preferenceCode());
            aligned.add(alignment.selfCode());
        }
        int scoreable = 0;
        int noTarget = 0;
        int unscored = 0;
        int unknown = 0;
        for (PreferenceTarget target : snapshot.preference().targets()) {
            Count count = classify(target.status(), target.targetValue(), target.constraintType(), aligned.contains(target.featureCode()));
            if (count == Count.SCOREABLE) scoreable++;
            else if (count == Count.NO_TARGET) noTarget++;
            else if (count == Count.UNSCORED) unscored++;
            else unknown++;
        }
        for (ProfileFeature feature : snapshot.self().features()) {
            Count count = classify(feature.status(), feature.value(), ConstraintType.SOFT, aligned.contains(feature.featureCode()));
            if (count == Count.SCOREABLE) scoreable++;
            else if (count == Count.NO_TARGET) noTarget++;
            else if (count == Count.UNSCORED) unscored++;
            else unknown++;
        }
        return new Report(registry.alignments().size(), scoreable, noTarget, unscored, unknown);
    }

    /** Mapped onboarding codes against the frozen alignment registry. Overlap is the scoreable contract. */
    public static ContractGap onboardingContract() {
        FeatureAlignmentRegistry registry = FeatureAlignmentRegistry.v1();
        TreeSet<String> mapped = new TreeSet<>();
        for (String question : RelationshipProfileProjector.STORED_QUESTIONS) {
            OnboardingFieldMap.Row row = OnboardingFieldMap.row(question);
            if ("MAPPED".equals(row.support()) && !row.featureCode().isEmpty()) mapped.add(row.featureCode());
        }
        TreeSet<String> alignment = new TreeSet<>();
        for (FeatureAlignment row : registry.alignments()) {
            alignment.add(row.preferenceCode());
            alignment.add(row.selfCode());
        }
        TreeSet<String> overlap = new TreeSet<>(mapped);
        overlap.retainAll(alignment);
        return new ContractGap(mapped.size(), registry.alignments().size(), registry.alignments().size(), overlap.size());
    }

    private static Count classify(FeatureStatus status, FeatureValue value, ConstraintType constraint, boolean aligned) {
        if (status != FeatureStatus.CONFIRMED || value == null) return Count.UNKNOWN;
        if (value instanceof FeatureValue.Withheld || value instanceof FeatureValue.Unbounded
                || value instanceof FeatureValue.ControlledRef || value instanceof FeatureValue.Text
                || constraint == ConstraintType.UNBOUNDED || constraint == ConstraintType.DOCUMENTED_EXISTING_RULE) {
            return Count.UNSCORED;
        }
        return aligned ? Count.SCOREABLE : Count.NO_TARGET;
    }

    private enum Count { SCOREABLE, NO_TARGET, UNSCORED, UNKNOWN }
}
