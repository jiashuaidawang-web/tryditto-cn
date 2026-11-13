package cn.aiditto.auth;

/** The authenticated caller, resolved from the bearer token. Controllers take it via {@code @AuthenticationPrincipal}. */
public record AuthUser(long userId, long sessionId, boolean admin) {}
