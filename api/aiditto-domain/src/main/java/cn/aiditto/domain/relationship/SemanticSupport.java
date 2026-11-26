package cn.aiditto.domain.relationship;

/**
 * How far a candidate may be trusted. A legal citation is not semantic support.
 * The same model response cannot promote its own citation to {@link #SEMANTIC_SUPPORTED}.
 */
public enum SemanticSupport {
    /** The cited token belongs to this context. The conclusion is still unchecked. */
    REFERENCE_VALID,
    /** The citation is legal, and no deterministic rule supports or contradicts the conclusion. */
    SEMANTIC_SUPPORT_PENDING,
    /** A server rule, not the model, matched the stored text or the approved scenario meaning. */
    SEMANTIC_SUPPORTED,
    /** Reserved for a future confirmation by the user. This round does not write it. */
    USER_CONFIRMED,
    /** The citation is illegal, or a deterministic rule contradicts the conclusion. */
    REJECTED
}
