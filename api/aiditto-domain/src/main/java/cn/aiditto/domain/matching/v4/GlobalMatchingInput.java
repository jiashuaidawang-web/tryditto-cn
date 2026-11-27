package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilityPolicies;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One offline run. A legal edge cannot be built from two ids and a score alone:
 * the snapshot, consent, and restriction sets have to be present.
 */
public record GlobalMatchingInput(
        String matchingRunId,
        LocalDate matchingPeriod,
        String cohort,
        List<CompatibilitySubject> eligibleUsers,
        Map<Long, Long> profileSnapshotVersions,
        Map<Long, Long> lifecycleGenerations,
        Map<Long, String> consentSnapshot,
        Set<PairKey> blockedPairs,
        Set<PairKey> forbiddenPairs,
        Set<PairKey> historicalRestrictions,
        String candidateStrategyVersion,
        String compatibilityPolicyVersion,
        String objectiveVersion,
        Map<Long, Integer> syntheticUnmatchedWeeks,
        long seed) {

    public GlobalMatchingInput {
        if (matchingRunId == null || matchingRunId.isBlank()) throw new IllegalArgumentException("run id");
        if (matchingPeriod == null || cohort == null || cohort.isBlank()) throw new IllegalArgumentException("period");
        if (eligibleUsers == null || eligibleUsers.isEmpty()) throw new IllegalArgumentException("users");
        eligibleUsers = List.copyOf(eligibleUsers);
        profileSnapshotVersions = Map.copyOf(profileSnapshotVersions);
        lifecycleGenerations = Map.copyOf(lifecycleGenerations);
        consentSnapshot = Map.copyOf(consentSnapshot);
        blockedPairs = Set.copyOf(blockedPairs);
        forbiddenPairs = Set.copyOf(forbiddenPairs);
        historicalRestrictions = Set.copyOf(historicalRestrictions);
        syntheticUnmatchedWeeks = Map.copyOf(syntheticUnmatchedWeeks);
        if (candidateStrategyVersion == null || compatibilityPolicyVersion == null || objectiveVersion == null) {
            throw new IllegalArgumentException("version");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (CompatibilitySubject user : eligibleUsers) {
            if (!seen.add(user.userId())) throw new IllegalArgumentException("duplicate user " + user.userId());
            if (!user.fixtureSource().startsWith("synthetic-fixture:")) throw new IllegalArgumentException("synthetic only");
            if (!profileSnapshotVersions.containsKey(user.userId())) throw new IllegalArgumentException("version snapshot");
            if (!lifecycleGenerations.containsKey(user.userId())) throw new IllegalArgumentException("generation snapshot");
            if (!consentSnapshot.containsKey(user.userId())) throw new IllegalArgumentException("consent snapshot");
        }
    }

    public static GlobalMatchingInput of(String runId, String cohort, List<CompatibilitySubject> users,
                                         Set<PairKey> blocked, Set<PairKey> forbidden, Set<PairKey> historical,
                                         Map<Long, Integer> unmatchedWeeks, long seed) {
        Map<Long, Long> versions = new LinkedHashMap<>();
        Map<Long, Long> generations = new LinkedHashMap<>();
        Map<Long, String> consent = new LinkedHashMap<>();
        for (CompatibilitySubject user : users) {
            versions.put(user.userId(), user.profileVersion());
            generations.put(user.userId(), user.generation());
            consent.put(user.userId(), "GRANTED");
        }
        return new GlobalMatchingInput(runId, LocalDate.of(2026, 11, 25), cohort, users, versions, generations, consent,
                blocked, forbidden, historical, "recall-rank-1", CompatibilityPolicies.VERSION,
                Objective.SELECTED_VERSION, unmatchedWeeks, seed);
    }

    public GlobalMatchingInput withConsent(long userId, String status) {
        Map<Long, String> consent = new LinkedHashMap<>(consentSnapshot);
        consent.put(userId, status);
        return new GlobalMatchingInput(matchingRunId, matchingPeriod, cohort, eligibleUsers, profileSnapshotVersions,
                lifecycleGenerations, consent, blockedPairs, forbiddenPairs, historicalRestrictions,
                candidateStrategyVersion, compatibilityPolicyVersion, objectiveVersion, syntheticUnmatchedWeeks, seed);
    }

    public GlobalMatchingInput withSnapshotVersion(long userId, long version) {
        Map<Long, Long> versions = new LinkedHashMap<>(profileSnapshotVersions);
        versions.put(userId, version);
        return new GlobalMatchingInput(matchingRunId, matchingPeriod, cohort, eligibleUsers, versions,
                lifecycleGenerations, consentSnapshot, blockedPairs, forbiddenPairs, historicalRestrictions,
                candidateStrategyVersion, compatibilityPolicyVersion, objectiveVersion, syntheticUnmatchedWeeks, seed);
    }

    public int unmatchedWeeks(long userId) {
        return syntheticUnmatchedWeeks.getOrDefault(userId, 0);
    }
}
