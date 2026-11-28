package cn.aiditto.domain.integration;

/**
 * V3 taste and feedback learning keep their own tables. They do not gain a second writer
 * for the relationship profile in either mode that is allowed to start.
 */
public final class LegacyLearningPolicy {
    private LegacyLearningPolicy() {}

    public static boolean mayWriteRelationshipProfile(ProductIntegrationMode mode) {
        if (mode == null) return false;
        return false;
    }
}
