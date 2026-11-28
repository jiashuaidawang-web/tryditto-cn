package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.integration.ProductIntegrationSettings;
import org.springframework.stereotype.Service;

/**
 * In an isolated test mode, a completed onboarding edit keeps the stored V4 snapshot
 * on the server projection. Legacy mode does not write that snapshot.
 */
@Service
public class IsolatedProfileSync {
    private final ProductIntegrationSettings settings;
    private final RelationshipProfileService profiles;

    public IsolatedProfileSync(ProductIntegrationSettings settings, RelationshipProfileService profiles) {
        this.settings = settings;
        this.profiles = profiles;
    }

    public ProfileVersion projectStored(long userId) {
        if (!settings.isolatedTest() || !profiles.processingOpen(userId)) return null;
        int source = profiles.storedSourceVersion(userId);
        if (source < 1) return null;
        return profiles.save(userId, source, profiles.storedGeneration(userId), profiles.projectAuthoritative(userId));
    }
}
