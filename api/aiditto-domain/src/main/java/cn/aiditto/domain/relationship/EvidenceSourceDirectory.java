package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;

import java.util.Optional;

/** A source the server has already resolved. A caller-supplied type or id is not a fact. */
public interface EvidenceSourceDirectory {
    Optional<SourceFact> find(long userId, String sourceRef, String sourceVersion);

    record SourceFact(
            long userId,
            String sourceRef,
            String sourceVersion,
            EvidenceSource sourceType,
            String consentScope,
            boolean retracted,
            boolean simulated) {}
}
