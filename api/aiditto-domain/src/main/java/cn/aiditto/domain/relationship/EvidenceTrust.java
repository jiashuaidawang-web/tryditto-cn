package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;

/** Checks an evidence row against a server-resolved source. The evidence fields are not the authority. */
public final class EvidenceTrust {
    private EvidenceTrust() {}

    public static void check(long userId, ProfileEvidence evidence, EvidenceSourceDirectory sources, FeatureRegistry registry) {
        if (evidence.subjectUserId() != userId) throw new IllegalArgumentException("evidence belongs to another user");
        FeatureDefinition definition = registry.require(evidence.featureCode());
        if (evidence.section() != RelationshipTypes.Section.MEMORY
                && !definition.allows(evidence.section(), evidence.sourceType())) {
            throw new IllegalArgumentException("source type cannot support this feature");
        }
        SourceFact fact = sources.find(userId, evidence.sourceRef(), evidence.sourceVersion())
                .orElseThrow(() -> new IllegalArgumentException("evidence source is not a stored record"));
        if (fact.userId() != userId || !fact.sourceRef().equals(evidence.sourceRef())
                || !fact.sourceVersion().equals(evidence.sourceVersion())) {
            throw new IllegalArgumentException("evidence source does not belong to this user and version");
        }
        if (fact.sourceType() != evidence.sourceType()) {
            throw new IllegalArgumentException("evidence source type does not match the stored record");
        }
        if (!OrdinarySnapshot.PROFILE_SCOPE.equals(fact.consentScope())
                || !OrdinarySnapshot.PROFILE_SCOPE.equals(evidence.consentScope())) {
            throw new IllegalArgumentException("evidence scope is not profile processing");
        }
        if (fact.simulated()) throw new IllegalArgumentException("simulated content cannot support a profile");
        if (fact.retracted() && evidence.status() != RelationshipTypes.EvidenceStatus.RETRACTED) {
            throw new IllegalArgumentException("a retracted source cannot support a current feature");
        }
        registry.checkValue(definition, evidence.claim());
    }
}
