package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic evidence is the base. A model candidate fills a gap only.
 * A user retraction or a live structured fact blocks the candidate even when its confidence is higher.
 */
public final class ProfileMerger {
    private ProfileMerger() {}

    public static RelationshipProfileSnapshot merge(long userId, List<ProfileEvidence> deterministic,
                                                    List<ProfileEvidence> provisional, FeatureRegistry registry) {
        List<ProfileEvidence> all = new ArrayList<>(deterministic);
        for (ProfileEvidence candidate : provisional) {
            if (candidate.sourceType() != RelationshipTypes.EvidenceSource.MODEL_INFERENCE) continue;
            if (candidate.section() == Section.MEMORY) continue;
            if (blocked(deterministic, candidate)) continue;
            all.add(candidate);
        }
        return RelationshipProfile.of(userId, all, registry).snapshot(registry);
    }

    private static boolean blocked(List<ProfileEvidence> deterministic, ProfileEvidence candidate) {
        for (ProfileEvidence fact : deterministic) {
            if (fact.featureCode().equals(candidate.featureCode()) && fact.section() == candidate.section()) return true;
        }
        return false;
    }
}
