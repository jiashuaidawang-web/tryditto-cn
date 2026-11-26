package cn.aiditto.domain.relationship;

/** Closed vocabularies for the relationship profile. Unknown is not a number in any of these. */
public final class RelationshipTypes {
    private RelationshipTypes() {}

    public enum ValueType { BOOLEAN, ENUM, ENUM_SET, NUMBER, RANGE, TEXT }

    /** Where a feature is allowed to live. A self code cannot be stored as a partner preference. */
    public enum Section { SELF, PREFERENCE, BEHAVIOR, MEMORY }

    public enum EvidenceSource { DIRECT_ANSWER, SELF_REPORTED, MODEL_INFERENCE, FEEDBACK_OBSERVATION }

    public enum EvidenceStatus { CONFIRMED, PROVISIONAL, RETRACTED }

    public enum Visibility { OWNER, MATCHING_INTERNAL }

    /** How strongly the user said the preference matters. Not a confidence and not a probability. */
    public enum Importance { NONE, KINDA, SUPER }

    public enum Operator { EQ, IN, BETWEEN, AT_LEAST, ANY, CONTROLLED_REF }

    /**
     * SOFT never vetoes a match. UNBOUNDED means the user said 不限.
     * DOCUMENTED_EXISTING_RULE only points at a rule HardFilter already enforces.
     * A model inference cannot use that third kind.
     */
    public enum ConstraintType { SOFT, UNBOUNDED, DOCUMENTED_EXISTING_RULE }

    public enum BehaviorKind { SELF_REPORTED, SCENARIO_RESPONSE, OBSERVED_BEHAVIOR }

    public enum Polarity { POSITIVE, NEGATIVE, MIXED }

    public enum FeatureStatus { CONFIRMED, PROVISIONAL, CONTESTED }

    /** ORDINARY may be copied into the snapshot. SENSITIVE_REF may only be a pointer. */
    public enum Sensitivity { ORDINARY, SENSITIVE_REF }
}
