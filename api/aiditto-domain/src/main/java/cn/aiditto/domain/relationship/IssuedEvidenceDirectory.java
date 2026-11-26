package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Resolves only the tokens issued for one context, one user, and one input fingerprint. */
public final class IssuedEvidenceDirectory implements EvidenceSourceDirectory {
    private final Map<String, SourceFact> facts;

    public IssuedEvidenceDirectory(ProfileContext context) {
        Map<String, SourceFact> map = new LinkedHashMap<>();
        String version = context.fingerprintToken();
        for (ProfileContext.Item item : context.items()) {
            map.put(key(context.userId(), item.token(), version), new SourceFact(
                    context.userId(), item.token(), version, EvidenceSource.MODEL_INFERENCE,
                    OrdinarySnapshot.PROFILE_SCOPE, false, false));
        }
        this.facts = Map.copyOf(map);
    }

    @Override
    public Optional<SourceFact> find(long userId, String sourceRef, String sourceVersion) {
        return Optional.ofNullable(facts.get(key(userId, sourceRef, sourceVersion)));
    }

    private static String key(long userId, String sourceRef, String sourceVersion) {
        return userId + "|" + sourceRef + "|" + sourceVersion;
    }
}
