package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PoolGate;

import java.util.Map;
import java.util.Set;

/**
 * One person as recall may see them. Fixture subjects carry a synthetic source.
 * Production-shaped subjects use {@link RecallIngress#FUTURE_PRODUCTION} and are not loaded from a user database.
 */
public record RecallSubject(
        long userId,
        String split,
        long profileVersion,
        boolean current,
        boolean withdrawn,
        String fixtureSource,
        Candidate candidate,
        PoolGate.Applicant applicant,
        MatchingConsent consent,
        Map<String, String> selfEnums,
        Set<String> hobbies,
        Map<String, String> preferenceEnums,
        long generation,
        RecallIngress ingress) {

    public RecallSubject {
        if (fixtureSource == null || fixtureSource.isBlank()) throw new IllegalArgumentException("fixture source is required");
        if (generation < 1) throw new IllegalArgumentException("generation");
        if (ingress == null) throw new IllegalArgumentException("ingress");
        if (ingress == RecallIngress.SYNTHETIC_FIXTURE && !fixtureSource.startsWith("synthetic-fixture:")) {
            throw new IllegalArgumentException("synthetic fixture source is required");
        }
        if (ingress == RecallIngress.FUTURE_PRODUCTION && !fixtureSource.startsWith("recall-contract:")) {
            throw new IllegalArgumentException("production contract source is required");
        }
        RecallProfileContract.checkIdentity(userId, candidate, consent);
        selfEnums = Map.copyOf(selfEnums);
        hobbies = Set.copyOf(hobbies);
        preferenceEnums = Map.copyOf(preferenceEnums);
        RecallProfileContract.checkFeatures(selfEnums, hobbies, preferenceEnums);
    }

    public boolean searchable() {
        return current && !withdrawn;
    }

    public boolean sparse() {
        return selfEnums.isEmpty() && hobbies.isEmpty() && preferenceEnums.isEmpty();
    }
}
