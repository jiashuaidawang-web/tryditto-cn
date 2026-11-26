package cn.aiditto.domain.relationship;

/**
 * The only snapshot a store may persist. Building a record by hand is not enough:
 * the canonical text, the hash, the evidence, and the registry are checked again here.
 */
public final class SnapshotAdmission {
    private SnapshotAdmission() {}

    public static RelationshipProfileSnapshot admit(RelationshipProfileSnapshot claimed, FeatureRegistry registry,
                                                    EvidenceSourceDirectory sources) {
        if (claimed.schemaVersion() != RelationshipProfileSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion");
        }
        if (!FeatureRegistry.VERSION.equals(claimed.featureRegistryVersion())) {
            throw new IllegalArgumentException("featureRegistryVersion");
        }
        for (ProfileEvidence evidence : claimed.evidence()) {
            EvidenceTrust.check(claimed.userId(), evidence, sources, registry);
        }
        RelationshipProfileSnapshot fresh = RelationshipProfile.of(claimed.userId(), claimed.evidence(), registry).snapshot(registry);
        if (!fresh.canonical().equals(claimed.canonical()) || !fresh.inputHash().equals(claimed.inputHash())) {
            throw new IllegalArgumentException("snapshot hash does not match its content");
        }
        if (!fresh.self().equals(claimed.self()) || !fresh.preference().equals(claimed.preference())
                || !fresh.behavior().equals(claimed.behavior()) || !fresh.memory().equals(claimed.memory())) {
            throw new IllegalArgumentException("snapshot sections do not match the evidence");
        }
        rejectCopiedText(fresh);
        return fresh;
    }

    private static void rejectCopiedText(RelationshipProfileSnapshot snapshot) {
        for (ProfileFeature feature : snapshot.self().features()) reject(feature.value());
        for (PreferenceTarget target : snapshot.preference().targets()) reject(target.targetValue());
        for (BehaviorRecord record : snapshot.behavior().records()) reject(record.value());
        for (ProfileEvidence evidence : snapshot.evidence()) reject(evidence.claim());
    }

    private static void reject(FeatureValue value) {
        if (value instanceof FeatureValue.Text) {
            throw new IllegalArgumentException("free text is not copied into an ordinary snapshot");
        }
        if (value instanceof FeatureValue.ControlledRef ref && !OrdinarySnapshot.allowedRef(ref.ref())) {
            throw new IllegalArgumentException("controlled ref");
        }
    }
}
