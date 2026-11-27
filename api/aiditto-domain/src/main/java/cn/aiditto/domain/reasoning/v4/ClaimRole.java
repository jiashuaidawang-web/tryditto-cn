package cn.aiditto.domain.reasoning.v4;

/**
 * Separates a restatement of an M02 row from an offline hypothesis or a new finding.
 * A hypothesis and a new finding stay out of the formal score.
 */
public enum ClaimRole {
    STRUCTURED_EXPLANATION,
    RELATIONSHIP_HYPOTHESIS,
    NEW_SUPPORTED_RELATION
}
