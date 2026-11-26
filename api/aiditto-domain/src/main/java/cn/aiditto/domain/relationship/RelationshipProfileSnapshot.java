package cn.aiditto.domain.relationship;

import java.util.List;

/**
 * Immutable projection. {@code inputHash} covers the canonical document only.
 * It does not cover {@code createdAt}, a random id, or the source profile version.
 */
public record RelationshipProfileSnapshot(
        long userId,
        int schemaVersion,
        String featureRegistryVersion,
        SelfProfile self,
        PreferenceProfile preference,
        BehaviorProfile behavior,
        MemoryProfile memory,
        List<ProfileEvidence> evidence,
        String canonical,
        String inputHash) {

    public static final int SCHEMA_VERSION = 1;

    public RelationshipProfileSnapshot {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion");
        if (featureRegistryVersion == null || featureRegistryVersion.isBlank()) throw new IllegalArgumentException("registry");
        evidence = List.copyOf(evidence);
        if (canonical == null || inputHash == null || inputHash.length() != 64) throw new IllegalArgumentException("hash");
    }

    public static RelationshipProfileSnapshot capture(long userId, String featureRegistryVersion,
                                                      SelfProfile self, PreferenceProfile preference,
                                                      BehaviorProfile behavior, MemoryProfile memory,
                                                      List<ProfileEvidence> evidence) {
        String selfJson = Canonical.selfJson(self.features());
        String preferenceJson = Canonical.preferenceJson(preference.targets());
        String behaviorJson = Canonical.behaviorJson(behavior.records());
        String memoryJson = Canonical.memoryJson(memory.observations());
        String evidenceJson = Canonical.evidenceJson(evidence);
        String document = Canonical.document(userId, SCHEMA_VERSION, featureRegistryVersion,
                selfJson, preferenceJson, behaviorJson, memoryJson, evidenceJson);
        return new RelationshipProfileSnapshot(userId, SCHEMA_VERSION, featureRegistryVersion,
                self, preference, behavior, memory, evidence, document, EvidenceIds.sha256(document));
    }

    public String selfJson() {
        return Canonical.selfJson(self.features());
    }

    public String preferenceJson() {
        return Canonical.preferenceJson(preference.targets());
    }

    public String behaviorJson() {
        return Canonical.behaviorJson(behavior.records());
    }

    public String memoryJson() {
        return Canonical.memoryJson(memory.observations());
    }

    public String evidenceJson() {
        return Canonical.evidenceJson(evidence);
    }
}
