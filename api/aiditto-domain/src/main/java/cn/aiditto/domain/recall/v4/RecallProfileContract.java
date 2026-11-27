package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.MatchingFeatureGate;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Sensitivity;
import cn.aiditto.domain.relationship.RelationshipTypes.ValueType;
import cn.aiditto.domain.relationship.SemanticSupport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The only admission path for recall features. It uses {@link FeatureRegistry} and
 * {@link MatchingFeatureGate}. It does not create a second profile model and it does not read a user database.
 */
public final class RecallProfileContract {
    public record Claim(String code, String value, SemanticSupport support, boolean withheld, boolean unbounded,
                        EvidenceSource source) {}

    public record Admitted(Map<String, String> selfEnums, Set<String> hobbies, Map<String, String> preferenceEnums,
                           int dropped, List<String> reasons) {
        public Admitted {
            selfEnums = Map.copyOf(selfEnums);
            hobbies = Set.copyOf(hobbies);
            preferenceEnums = Map.copyOf(preferenceEnums);
            reasons = List.copyOf(reasons);
        }
    }

    private RecallProfileContract() {}

    public static Admitted admit(List<Claim> claims, Set<String> hobbyValues, SemanticSupport hobbySupport) {
        FeatureRegistry registry = FeatureRegistry.v1();
        Map<String, List<Claim>> grouped = new LinkedHashMap<>();
        int dropped = 0;
        List<String> reasons = new ArrayList<>();
        for (Claim claim : claims) {
            if (claim == null || claim.code() == null || claim.code().isBlank()) {
                dropped++;
                reasons.add("BLANK");
                continue;
            }
            grouped.computeIfAbsent(claim.code(), ignored -> new ArrayList<>()).add(claim);
        }
        Map<String, String> self = new TreeMap<>();
        Map<String, String> preference = new TreeMap<>();
        for (List<Claim> group : grouped.values()) {
            List<Claim> kept = new ArrayList<>();
            for (Claim claim : group) {
                String reason = reject(registry, claim);
                if (reason != null) {
                    dropped++;
                    reasons.add(claim.code() + ":" + reason);
                } else {
                    kept.add(claim);
                }
            }
            Set<String> values = new TreeSet<>();
            for (Claim claim : kept) values.add(claim.value());
            if (values.size() > 1) {
                dropped += kept.size();
                reasons.add(group.get(0).code() + ":CONTRADICTION");
                continue;
            }
            if (kept.isEmpty()) continue;
            Claim claim = kept.get(0);
            FeatureDefinition definition = registry.require(claim.code());
            if (definition.sections().contains(Section.SELF)) self.put(claim.code(), claim.value());
            else preference.put(claim.code(), claim.value());
        }
        Set<String> hobbies = new TreeSet<>();
        FeatureDefinition hobbyDefinition = registry.require("lifestyle.hobbies");
        if (hobbyValues != null && MatchingFeatureGate.eligibleForOfficialScore(hobbySupport)) {
            for (String hobby : hobbyValues) {
                if (hobby != null && hobbyDefinition.allowedValues().contains(hobby)) hobbies.add(hobby);
                else if (hobby != null && !hobby.isBlank()) {
                    dropped++;
                    reasons.add("lifestyle.hobbies:VALUE");
                }
            }
        } else if (hobbyValues != null && !hobbyValues.isEmpty()) {
            dropped++;
            reasons.add("lifestyle.hobbies:SUPPORT");
        }
        return new Admitted(self, hobbies, preference, dropped, reasons);
    }

    public static void checkIdentity(long userId, Candidate candidate, MatchingConsent consent) {
        if (candidate == null || consent == null) throw new IllegalArgumentException("identity");
        if (candidate.id() != userId || consent.userId() != userId) {
            throw new IllegalArgumentException("user, candidate, and consent ids differ");
        }
    }

    public static void checkFeatures(Map<String, String> self, Set<String> hobbies, Map<String, String> preference) {
        FeatureRegistry registry = FeatureRegistry.v1();
        for (Map.Entry<String, String> entry : self.entrySet()) checkOne(registry, entry.getKey(), entry.getValue(), Section.SELF);
        for (Map.Entry<String, String> entry : preference.entrySet()) {
            checkOne(registry, entry.getKey(), entry.getValue(), Section.PREFERENCE);
        }
        FeatureDefinition hobbyDefinition = registry.require("lifestyle.hobbies");
        for (String hobby : hobbies) {
            if (!hobbyDefinition.allowedValues().contains(hobby)) {
                throw new IllegalArgumentException("hobby outside the registry: " + hobby);
            }
        }
    }

    public static boolean allowsVectorSync(RecallSubject subject, LocalDate week, Instant now) {
        if (subject.generation() < 1 || !subject.searchable()) return false;
        if (subject.ingress() == RecallIngress.SYNTHETIC_FIXTURE) {
            if (subject.fixtureSource() == null || !subject.fixtureSource().startsWith("synthetic-fixture:")) return false;
        } else if (subject.fixtureSource() == null || !subject.fixtureSource().startsWith("recall-contract:")) {
            return false;
        }
        if (subject.consent().consentStatus() != ConsentStatus.GRANTED) return false;
        return PoolGate.check(subject.applicant(), week, now).isEmpty();
    }

    private static void checkOne(FeatureRegistry registry, String code, String value, Section section) {
        FeatureDefinition definition = registry.require(code);
        if (!definition.sections().contains(section)) throw new IllegalArgumentException("section mismatch " + code);
        if (definition.valueType() != ValueType.ENUM || definition.sensitivity() != Sensitivity.ORDINARY) {
            throw new IllegalArgumentException("recall feature is not an ordinary enum " + code);
        }
        if (value == null || !definition.allowedValues().contains(value)) {
            throw new IllegalArgumentException("value outside the registry " + code);
        }
    }

    private static String reject(FeatureRegistry registry, Claim claim) {
        if (claim.withheld()) return "WITHHELD";
        if (claim.unbounded()) return "UNBOUNDED";
        if (claim.value() == null || claim.value().isBlank() || "UNKNOWN".equals(claim.value())) return "UNKNOWN";
        FeatureDefinition definition;
        try {
            definition = registry.require(claim.code());
        } catch (IllegalArgumentException e) {
            return "CODE";
        }
        if (definition.sensitivity() != Sensitivity.ORDINARY || definition.valueType() != ValueType.ENUM) return "TYPE";
        if (!definition.sections().contains(Section.SELF) && !definition.sections().contains(Section.PREFERENCE)) return "SECTION";
        if (definition.sections().contains(Section.BEHAVIOR) || definition.sections().contains(Section.MEMORY)) {
            if (!definition.sections().contains(Section.SELF)) return "SECTION";
        }
        if (!definition.allowedValues().contains(claim.value())) return "VALUE";
        if (!MatchingFeatureGate.eligibleForOfficialScore(claim.support())) return "SUPPORT";
        EvidenceSource source = claim.source() == null ? EvidenceSource.SELF_REPORTED : claim.source();
        if (source == EvidenceSource.MODEL_INFERENCE && claim.support() != SemanticSupport.SEMANTIC_SUPPORTED) return "INFERENCE";
        if (source == EvidenceSource.FEEDBACK_OBSERVATION) return "FEEDBACK";
        if (!definition.allows(sectionOf(definition), source) && !definition.allowedSources().contains(source)) return "SOURCE";
        return null;
    }

    private static Section sectionOf(FeatureDefinition definition) {
        if (definition.sections().contains(Section.PREFERENCE)) return Section.PREFERENCE;
        return Section.SELF;
    }
}
