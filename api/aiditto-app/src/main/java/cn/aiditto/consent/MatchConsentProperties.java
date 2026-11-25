package cn.aiditto.consent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * V4 compliance mode is the default: only {@code GRANTED} may enter the pool or be published.
 * {@code legacyAllowUnknown} is a migration escape hatch and is not a V4 acceptance mode.
 * {@code REVOKED} never passes, even when the hatch is open. Do not enable it without a
 * separate approval, and do not treat a run with it on as V4-compliant.
 */
@ConfigurationProperties("aiditto.match.consent")
public record MatchConsentProperties(boolean legacyAllowUnknown) {}
