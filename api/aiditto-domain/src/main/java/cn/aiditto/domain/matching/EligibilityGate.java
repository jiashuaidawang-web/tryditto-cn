package cn.aiditto.domain.matching;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * One publish-time decision for a pair. It calls {@link PoolGate} and {@link HardFilter}; it does not
 * reimplement their rules. Reason codes name the failed rule and never name which person failed.
 *
 * <p>The match being published is already a row in {@code match}, so it shows up as a past pair and as
 * an open match. Callers must leave {@code hasOpenMatch} false for that row, and this gate drops the
 * pair from {@link MatchingInput#pastPairs()} before {@link HardFilter} runs. A different open match
 * or an earlier pair still fails.
 */
public final class EligibilityGate {
    public static final String POLICY_VERSION = "eligibility-v1";
    /** The only code a caller may put in a user-visible error. */
    public static final String PUBLIC_CODE = "NOT_ELIGIBLE";

    private EligibilityGate() {}

    public record Facts(
            LocalDate weekStart,
            Candidate low,
            Candidate high,
            MatchingInput input,
            boolean forbiddenPair,
            PoolGate.Applicant lowGate,
            PoolGate.Applicant highGate,
            MatchingConsent lowConsent,
            MatchingConsent highConsent,
            boolean legacyAllowUnknown,
            Instant checkedAt) {

        public Facts {
            Objects.requireNonNull(weekStart, "weekStart");
            Objects.requireNonNull(lowConsent, "lowConsent");
            Objects.requireNonNull(highConsent, "highConsent");
            Objects.requireNonNull(checkedAt, "checkedAt");
        }
    }

    public record Result(boolean eligible, List<String> reasonCodes, Instant checkedAt, String policyVersion) {
        public Result {
            reasonCodes = List.copyOf(reasonCodes);
            Objects.requireNonNull(checkedAt, "checkedAt");
            Objects.requireNonNull(policyVersion, "policyVersion");
        }
    }

    public static Result evaluate(Facts facts) {
        Set<String> codes = new TreeSet<>();
        if (facts.lowGate() == null || facts.highGate() == null) {
            codes.add("ACCOUNT_MISSING");
        } else {
            pool(facts.lowGate(), facts, codes);
            pool(facts.highGate(), facts, codes);
        }
        boolean lowOk = ConsentPolicy.allows(facts.lowConsent(), facts.legacyAllowUnknown());
        boolean highOk = ConsentPolicy.allows(facts.highConsent(), facts.legacyAllowUnknown());
        if (!lowOk || !highOk) {
            boolean revoked = facts.lowConsent().consentStatus() == ConsentStatus.REVOKED
                    || facts.highConsent().consentStatus() == ConsentStatus.REVOKED;
            codes.add(revoked ? "CONSENT_REVOKED" : "CONSENT_UNKNOWN");
        }
        if (facts.forbiddenPair()) codes.add("FORBIDDEN_PAIR");
        if (facts.low() == null || facts.high() == null || facts.input() == null) {
            codes.add("PROFILE_INCOMPLETE");
        } else {
            MatchingInput input = withoutThisPair(facts.input(), facts.low().id(), facts.high().id());
            for (HardRule rule : HardFilter.violations(facts.low(), facts.high(), input)) {
                codes.add("HARD_" + rule.name());
            }
        }
        List<String> list = List.copyOf(codes);
        return new Result(list.isEmpty(), list, facts.checkedAt(), POLICY_VERSION);
    }

    private static void pool(PoolGate.Applicant applicant, Facts facts, Set<String> codes) {
        PoolGate.check(applicant, facts.weekStart(), facts.checkedAt())
                .ifPresent(reason -> codes.add("POOL_" + reason.name()));
    }

    /** This draft is not a previous match. Every other pair stays in the set. */
    static MatchingInput withoutThisPair(MatchingInput input, long a, long b) {
        if (a == b) return input;
        PairKey key = PairKey.of(a, b);
        if (!input.pastPairs().contains(key)) return input;
        Set<PairKey> past = new HashSet<>(input.pastPairs());
        past.remove(key);
        return input.withPastPairs(past);
    }
}
