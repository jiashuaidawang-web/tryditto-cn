package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.HardFilter;
import cn.aiditto.domain.matching.HardRule;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.matching.Weights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The offline scoring context. Callers supply the week and the historical blocks.
 * The engine does not invent an empty {@link MatchingInput}.
 */
public record CompatibilityContext(
        MatchingInput input,
        Instant now,
        Map<Long, PoolGate.Applicant> applicants,
        Map<Long, MatchingConsent> consents) {

    public CompatibilityContext {
        if (input == null || now == null) throw new IllegalArgumentException("context");
        applicants = Map.copyOf(applicants);
        consents = Map.copyOf(consents);
    }

    /**
     * An experiment that truthfully has no past pairs and no reports.
     * Passing an empty history here is the caller's statement, not a default inside the engine.
     */
    public static CompatibilityContext experiment(LocalDate week, Instant now, List<CompatibilitySubject> people) {
        return experiment(week, now, people, Set.of(), Set.of());
    }

    public static CompatibilityContext experiment(LocalDate week, Instant now, List<CompatibilitySubject> people,
                                                  Set<PairKey> pastPairs, Set<PairKey> reportedPairs) {
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        Map<Long, PoolGate.Applicant> applicants = new LinkedHashMap<>();
        Map<Long, MatchingConsent> consents = new LinkedHashMap<>();
        for (CompatibilitySubject person : people) {
            candidates.put(person.userId(), person.candidate());
            applicants.put(person.userId(), eligibleApplicant(person.candidate()));
            consents.put(person.userId(), granted(person.userId(), now));
        }
        MatchingInput input = new MatchingInput(week, List.copyOf(candidates.values()), pastPairs, reportedPairs,
                Weights.W1, 1, false, Map.of(), Map.of());
        return new CompatibilityContext(input, now, applicants, consents);
    }

    public static PoolGate.Applicant eligibleApplicant(Candidate candidate) {
        return new PoolGate.Applicant(candidate.birthday(), true, true, false, PoolGate.MIN_PHOTOS,
                false, false, null, false);
    }

    public static MatchingConsent granted(long userId, Instant now) {
        return new MatchingConsent(userId, ConsentStatus.GRANTED, 1, now, null, ConsentPolicy.POLICY_VERSION, now);
    }

    /**
     * Pool, consent, and hard constraints. Past pairs stay in the input.
     * {@link cn.aiditto.domain.matching.EligibilityGate} removes the pair being published; scoring must not do that.
     */
    public List<String> blockReasons(CompatibilitySubject left, CompatibilitySubject right) {
        List<String> codes = new ArrayList<>();
        if (!synthetic(left) || !synthetic(right)) codes.add("NOT_SYNTHETIC");
        if (!left.current() || !right.current()) codes.add("PROFILE_STALE");
        if (left.withdrawn() || right.withdrawn()) codes.add("PROFILE_WITHDRAWN");
        pool(left, codes);
        pool(right, codes);
        consent(left, codes);
        consent(right, codes);
        for (HardRule rule : HardFilter.violations(left.candidate(), right.candidate(), input)) {
            codes.add("HARD_" + rule.name());
        }
        return List.copyOf(codes);
    }

    private void pool(CompatibilitySubject person, List<String> codes) {
        PoolGate.Applicant applicant = applicants.get(person.userId());
        if (applicant == null) {
            codes.add("ACCOUNT_MISSING");
            return;
        }
        PoolGate.check(applicant, input.weekStart(), now).ifPresent(reason -> codes.add("POOL_" + reason.name()));
    }

    private void consent(CompatibilitySubject person, List<String> codes) {
        MatchingConsent consent = consents.get(person.userId());
        if (consent == null || consent.userId() != person.userId()) {
            codes.add("CONSENT_UNKNOWN");
            return;
        }
        if (!ConsentPolicy.allows(consent)) {
            codes.add(consent.consentStatus() == ConsentStatus.REVOKED ? "CONSENT_REVOKED" : "CONSENT_UNKNOWN");
        }
    }

    private static boolean synthetic(CompatibilitySubject person) {
        return person.fixtureSource() != null && person.fixtureSource().startsWith("synthetic-fixture:");
    }
}
