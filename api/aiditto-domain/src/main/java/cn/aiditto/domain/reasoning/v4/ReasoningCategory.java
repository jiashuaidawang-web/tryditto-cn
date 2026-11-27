package cn.aiditto.domain.reasoning.v4;

/**
 * What a directional claim is allowed to mean.
 * A potential complement is not a verified fit. A potential conflict is not a hard constraint.
 */
public enum ReasoningCategory {
    SUPPORTED_COMPATIBILITY,
    POTENTIAL_COMPLEMENT,
    POTENTIAL_CONFLICT,
    INSUFFICIENT_EVIDENCE,
    CONTRADICTION,
    UNSUPPORTED_INFERENCE
}
