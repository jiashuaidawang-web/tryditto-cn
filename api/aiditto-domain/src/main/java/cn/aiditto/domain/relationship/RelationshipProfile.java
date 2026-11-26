package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * One user's evidence and the features that evidence currently supports.
 * Retracted evidence stays in the list and cannot support a current feature.
 * Disagreeing live evidence stays side by side as {@link FeatureStatus#CONTESTED}.
 */
public record RelationshipProfile(
        long userId,
        List<ProfileEvidence> evidence,
        SelfProfile self,
        PreferenceProfile preference,
        BehaviorProfile behavior,
        MemoryProfile memory) {

    public RelationshipProfile {
        evidence = List.copyOf(evidence);
    }

    public static RelationshipProfile of(long userId, List<ProfileEvidence> incoming, FeatureRegistry registry) {
        TreeMap<String, List<ProfileEvidence>> self = new TreeMap<>();
        TreeMap<String, List<ProfileEvidence>> preference = new TreeMap<>();
        TreeMap<String, List<ProfileEvidence>> behavior = new TreeMap<>();
        List<ProfileEvidence> memory = new ArrayList<>();
        List<ProfileEvidence> sorted = new ArrayList<>(incoming);
        sorted.sort(Comparator.comparing(ProfileEvidence::evidenceId));
        for (ProfileEvidence item : sorted) {
            if (item.subjectUserId() != userId) {
                throw new IllegalArgumentException("evidence belongs to another user");
            }
            FeatureDefinition definition = registry.require(item.featureCode());
            if (item.section() == Section.MEMORY) {
                if (!definition.sections().contains(Section.MEMORY)
                        || item.sourceType() != RelationshipTypes.EvidenceSource.FEEDBACK_OBSERVATION) {
                    throw new IllegalArgumentException("memory evidence is not a real observation of this feature");
                }
            } else if (!definition.allows(item.section(), item.sourceType())) {
                throw new IllegalArgumentException("evidence source is not allowed");
            }
            registry.checkValue(definition, item.claim());
            switch (item.section()) {
                case SELF -> self.computeIfAbsent(item.featureCode(), key -> new ArrayList<>()).add(item);
                case PREFERENCE -> preference.computeIfAbsent(item.featureCode(), key -> new ArrayList<>()).add(item);
                case BEHAVIOR -> behavior.computeIfAbsent(item.featureCode(), key -> new ArrayList<>()).add(item);
                case MEMORY -> memory.add(item);
            }
        }
        return new RelationshipProfile(userId, List.copyOf(sorted),
                new SelfProfile(foldSelf(self)),
                new PreferenceProfile(foldPreference(preference)),
                new BehaviorProfile(foldBehavior(behavior)),
                new MemoryProfile(foldMemory(memory)));
    }

    public RelationshipProfileSnapshot snapshot(FeatureRegistry registry) {
        return RelationshipProfileSnapshot.capture(userId, registry.version(), self, preference, behavior, memory, evidence);
    }

    private static List<ProfileFeature> foldSelf(TreeMap<String, List<ProfileEvidence>> grouped) {
        List<ProfileFeature> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<ProfileEvidence> live = live(entry.getValue());
            if (live.isEmpty()) continue;
            boolean same = live.stream().allMatch(item -> item.claim().equals(live.get(0).claim()));
            if (!same) out.add(new ProfileFeature(entry.getKey(), null, refs(live), FeatureStatus.CONTESTED));
            else out.add(new ProfileFeature(entry.getKey(), live.get(0).claim(), refs(live), statusOf(live)));
        }
        return out;
    }

    private static List<PreferenceTarget> foldPreference(TreeMap<String, List<ProfileEvidence>> grouped) {
        List<PreferenceTarget> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<ProfileEvidence> live = live(entry.getValue());
            if (live.isEmpty()) continue;
            ProfileEvidence first = live.get(0);
            boolean same = live.stream().allMatch(item -> item.claim().equals(first.claim())
                    && item.operator() == first.operator()
                    && item.importance() == first.importance()
                    && item.constraintType() == first.constraintType());
            if (!same) {
                out.add(new PreferenceTarget(entry.getKey(), null, null, null, ConstraintType.SOFT, null, refs(live), FeatureStatus.CONTESTED));
            } else {
                double confidence = live.stream().mapToDouble(ProfileEvidence::confidence).min().orElseThrow();
                out.add(new PreferenceTarget(entry.getKey(), first.operator(), first.claim(), first.importance(),
                        first.constraintType(), confidence, refs(live), statusOf(live)));
            }
        }
        return out;
    }

    private static List<BehaviorRecord> foldBehavior(TreeMap<String, List<ProfileEvidence>> grouped) {
        List<BehaviorRecord> out = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<ProfileEvidence> live = live(entry.getValue());
            if (live.isEmpty()) continue;
            boolean same = live.stream().allMatch(item -> item.claim().equals(live.get(0).claim())
                    && item.behaviorKind() == live.get(0).behaviorKind());
            if (!same) out.add(new BehaviorRecord(entry.getKey(), live.get(0).behaviorKind(), null, refs(live), FeatureStatus.CONTESTED));
            else out.add(new BehaviorRecord(entry.getKey(), live.get(0).behaviorKind(), live.get(0).claim(), refs(live), statusOf(live)));
        }
        return out;
    }

    private static List<MemoryObservation> foldMemory(List<ProfileEvidence> evidence) {
        List<MemoryObservation> out = new ArrayList<>();
        for (ProfileEvidence item : evidence) {
            if (item.status() == EvidenceStatus.RETRACTED) continue;
            out.add(new MemoryObservation(item.evidenceId(), item.featureCode(), item.sourceRef(),
                    item.sourceType().name(), item.polarity(), item.confidence(), List.of(item.evidenceId()), statusOf(List.of(item))));
        }
        return out;
    }

    private static List<ProfileEvidence> live(List<ProfileEvidence> evidence) {
        return evidence.stream().filter(item -> item.status() != EvidenceStatus.RETRACTED).toList();
    }

    private static List<String> refs(List<ProfileEvidence> evidence) {
        return evidence.stream().map(ProfileEvidence::evidenceId).sorted().toList();
    }

    private static FeatureStatus statusOf(List<ProfileEvidence> live) {
        boolean provisional = live.stream().anyMatch(item -> item.status() == EvidenceStatus.PROVISIONAL);
        return provisional ? FeatureStatus.PROVISIONAL : FeatureStatus.CONFIRMED;
    }
}
