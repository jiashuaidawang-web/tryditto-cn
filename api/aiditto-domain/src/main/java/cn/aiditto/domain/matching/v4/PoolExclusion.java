package cn.aiditto.domain.matching.v4;

/**
 * Why a product user is outside the shadow pool.
 * A sparse but legal profile is not one of these reasons.
 */
public enum PoolExclusion {
    ACCOUNT_INACTIVE,
    ONBOARDING_INCOMPLETE,
    MATCH_CONSENT_MISSING,
    MATCH_CONSENT_REVOKED,
    PROFILE_PROCESSING_NOT_GRANTED,
    PROFILE_UNAVAILABLE,
    STALE_PROFILE,
    INVALID_BUSINESS_FACTS,
    OUTSIDE_COHORT,
    OPEN_MATCH
}
