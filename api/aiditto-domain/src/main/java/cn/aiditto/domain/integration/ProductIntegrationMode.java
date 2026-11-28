package cn.aiditto.domain.integration;

/**
 * How the product talks to the V4 profile. The default keeps today's V3 behavior.
 * Shadow and pilot are named so a bad config fails startup instead of running an empty stand-in.
 */
public enum ProductIntegrationMode {
    LEGACY_ONLY,
    V4_INTEGRATION_TEST;

    public static ProductIntegrationMode parse(String raw) {
        if (raw == null || raw.isBlank()) return LEGACY_ONLY;
        String name = raw.trim();
        if ("V4_SHADOW".equals(name) || "V4_PILOT".equals(name)) {
            throw new IllegalStateException(name + " is not enabled");
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("unknown integration mode");
        }
    }

    /** This mode never turns on a live model, Qdrant, production matching, V4 learning, or strategy activation. */
    public boolean allowsProductionV4() {
        return false;
    }
}
