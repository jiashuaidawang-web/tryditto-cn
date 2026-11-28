package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.relationship.EvidenceSourceDirectory;
import cn.aiditto.domain.relationship.EvidenceSourceDirectory.SourceFact;
import cn.aiditto.domain.relationship.OrdinarySnapshot;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Sources the server has stored. A caller-supplied type is not a fact. */
public final class ConfirmationSources implements EvidenceSourceDirectory {
    private final Map<String, SourceFact> facts = new LinkedHashMap<>();
    private EvidenceSourceDirectory fallback;

    public void put(SourceFact fact) {
        facts.put(key(fact.userId(), fact.sourceRef(), fact.sourceVersion()), fact);
    }

    /** Questionnaire rows stay in their own directory. This map does not re-type them. */
    public void delegate(EvidenceSourceDirectory directory) {
        this.fallback = directory;
    }

    public static SourceFact explicit(long userId, String sourceRef, boolean retracted) {
        return new SourceFact(userId, sourceRef, ProfileLearningEngine.POLICY, EvidenceSource.SELF_REPORTED,
                OrdinarySnapshot.PROFILE_SCOPE, retracted, false);
    }

    public static SourceFact seed(long userId, String sourceRef) {
        return new SourceFact(userId, sourceRef, ProfileLearningEngine.SEED_VERSION, EvidenceSource.SELF_REPORTED,
                OrdinarySnapshot.PROFILE_SCOPE, false, false);
    }

    public void retract(ProfileEvidence evidence) {
        put(new SourceFact(evidence.subjectUserId(), evidence.sourceRef(), evidence.sourceVersion(),
                evidence.sourceType(), evidence.consentScope(), true, false));
    }

    @Override
    public Optional<SourceFact> find(long userId, String sourceRef, String sourceVersion) {
        SourceFact local = facts.get(key(userId, sourceRef, sourceVersion));
        if (local != null) return Optional.of(local);
        return fallback == null ? Optional.empty() : fallback.find(userId, sourceRef, sourceVersion);
    }

    private static String key(long userId, String sourceRef, String sourceVersion) {
        return userId + "|" + sourceRef + "|" + sourceVersion;
    }
}
