package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PoolGate;

import java.util.Map;
import java.util.Set;

/**
 * One synthetic or fixture person as recall may see them.
 * The fixture source is required so a bench profile cannot be mistaken for a production confirmation.
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
        Map<String, String> preferenceEnums) {

    public RecallSubject {
        if (fixtureSource == null || fixtureSource.isBlank()) {
            throw new IllegalArgumentException("fixture source is required");
        }
        selfEnums = Map.copyOf(selfEnums);
        hobbies = Set.copyOf(hobbies);
        preferenceEnums = Map.copyOf(preferenceEnums);
    }

    public boolean searchable() {
        return current && !withdrawn;
    }
}
