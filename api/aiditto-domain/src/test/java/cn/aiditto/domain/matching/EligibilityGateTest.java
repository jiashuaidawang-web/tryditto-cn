package cn.aiditto.domain.matching;

import static cn.aiditto.domain.matching.HardFilterTest.WEEK;
import static cn.aiditto.domain.matching.HardFilterTest.base;
import static org.assertj.core.api.Assertions.assertThat;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Publish eligibility. Reason codes stay internal; the public code names neither person. */
class EligibilityGateTest {
    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void bothGrantedAndWithinHardRulesAreEligible() {
        EligibilityGate.Result result = EligibilityGate.evaluate(facts(base(1).build(), base(2).build(), false));
        assertThat(result.eligible()).isTrue();
        assertThat(result.reasonCodes()).isEmpty();
        assertThat(result.checkedAt()).isEqualTo(NOW);
        assertThat(result.policyVersion()).isEqualTo(EligibilityGate.POLICY_VERSION);
    }

    @Test
    void thisDraftIsNotAPreviousMatchAndNotAnOpenMatch() {
        Candidate a = base(1).build();
        Candidate b = base(2).build();
        MatchingInput input = new MatchingInput(WEEK, List.of(a, b), Set.of(PairKey.of(1, 2)), Set.of(),
                Weights.W1, 1, true);
        EligibilityGate.Result result = EligibilityGate.evaluate(facts(a, b, input, granted(1), granted(2), false));
        assertThat(result.eligible()).isTrue();
        assertThat(result.reasonCodes()).doesNotContain("HARD_NEVER_MATCHED", "HARD_OPEN_MATCH");
    }

    @Test
    void unknownConsentCannotPublishInComplianceMode() {
        Candidate a = base(1).build();
        Candidate b = base(2).build();
        assertThat(EligibilityGate.evaluate(facts(a, b, ConsentPolicy.unknown(1), granted(2), false)).reasonCodes())
                .contains("CONSENT_UNKNOWN");
        assertThat(EligibilityGate.evaluate(facts(a, b, ConsentPolicy.unknown(1), granted(2), true)).eligible())
                .as("legacy hatch, not V4 compliance")
                .isTrue();
    }

    @Test
    void revokedConsentCannotPassTheLegacySwitch() {
        EligibilityGate.Result result = EligibilityGate.evaluate(
                facts(base(1).build(), base(2).build(), granted(1), revoked(2), true));
        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCodes()).containsExactly("CONSENT_REVOKED");
    }

    @Test
    void eitherDirectionBlockIsInternalAndDoesNotNameASide() {
        Candidate a = base(1).blockedHashes(Set.of("h2")).build();
        Candidate b = base(2).contactHashes(Set.of("h2")).build();
        EligibilityGate.Result result = EligibilityGate.evaluate(facts(a, b, false));
        assertThat(result.eligible()).isFalse();
        assertThat(result.reasonCodes()).contains("HARD_BLOCKLIST");
        assertThat(result.reasonCodes()).noneMatch(code -> code.contains("1") || code.contains("2"));
        assertPublic(result);
    }

    @Test
    void forbiddenPairIsSeparateFromAReport() {
        Candidate a = base(1).build();
        Candidate b = base(2).build();
        MatchingInput input = new MatchingInput(WEEK, List.of(a, b), Set.of(), Set.of(), Weights.W1, 1, false);
        EligibilityGate.Facts facts = new EligibilityGate.Facts(WEEK, a, b, input, true,
                applicant(a), applicant(b), granted(1), granted(2), false, NOW);
        EligibilityGate.Result result = EligibilityGate.evaluate(facts);
        assertThat(result.reasonCodes()).contains("FORBIDDEN_PAIR").doesNotContain("HARD_REPORTED");
    }

    @Test
    void under18InactiveAndHardAgeEachFailClosed() {
        Candidate child = base(1).birthday(LocalDate.of(2015, 1, 1)).build();
        Candidate adult = base(2).build();
        assertThat(EligibilityGate.evaluate(facts(child, adult, false)).reasonCodes())
                .contains("POOL_UNDER_18", "HARD_ADULT");

        PoolGate.Applicant inactive = new PoolGate.Applicant(adult.birthday(), false, true, false, 2, false, false, null, false);
        EligibilityGate.Facts stopped = new EligibilityGate.Facts(WEEK, adult, base(3).build(),
                input(adult, base(3).build()), false, inactive, applicant(base(3).build()),
                granted(2), granted(3), false, NOW);
        assertThat(EligibilityGate.evaluate(stopped).reasonCodes()).contains("POOL_NOT_ACTIVE");

        Candidate narrow = base(2).ageRange(30, 40).build();
        assertThat(EligibilityGate.evaluate(facts(base(1).build(), narrow, false)).reasonCodes()).contains("HARD_AGE");
    }

    @Test
    void anotherOpenMatchStillFailsAManualRun() {
        Candidate a = base(1).hasOpenMatch(true).build();
        Candidate b = base(2).build();
        MatchingInput input = new MatchingInput(WEEK, List.of(a, b), Set.of(), Set.of(), Weights.W1, 1, true);
        assertThat(EligibilityGate.evaluate(facts(a, b, input, granted(1), granted(2), false)).reasonCodes())
                .contains("HARD_OPEN_MATCH");
    }

    @Test
    void userVisibleFailureDoesNotCarryPrivateReasonCodes() {
        EligibilityGate.Result result = EligibilityGate.evaluate(
                facts(base(1).build(), base(2).build(), ConsentPolicy.unknown(1), revoked(2), false));
        assertThat(result.reasonCodes()).isNotEmpty();
        assertPublic(result);
        DomainException shown = new DomainException(ErrorCode.MATCH_STATE);
        assertThat(shown.getMessage()).isEqualTo("现在不能做这个操作");
        assertThat(shown.fields()).isEmpty();
        for (String code : result.reasonCodes()) {
            assertThat(shown.getMessage()).doesNotContain(code);
        }
    }

    private static void assertPublic(EligibilityGate.Result result) {
        assertThat(EligibilityGate.PUBLIC_CODE).isEqualTo("NOT_ELIGIBLE");
        assertThat(EligibilityGate.PUBLIC_CODE).doesNotContain("CONSENT", "BLOCK", "REVOKED");
        assertThat(result.policyVersion()).doesNotContain("UNKNOWN");
    }

    private static EligibilityGate.Facts facts(Candidate a, Candidate b, boolean legacyAllowUnknown) {
        return facts(a, b, granted(a.id()), granted(b.id()), legacyAllowUnknown);
    }

    private static EligibilityGate.Facts facts(Candidate a, Candidate b, MatchingConsent left, MatchingConsent right,
                                              boolean legacyAllowUnknown) {
        return facts(a, b, input(a, b), left, right, legacyAllowUnknown);
    }

    private static EligibilityGate.Facts facts(Candidate a, Candidate b, MatchingInput input,
                                              MatchingConsent left, MatchingConsent right, boolean legacyAllowUnknown) {
        return new EligibilityGate.Facts(WEEK, a, b, input, false, applicant(a), applicant(b),
                left, right, legacyAllowUnknown, NOW);
    }

    private static MatchingInput input(Candidate a, Candidate b) {
        return new MatchingInput(WEEK, List.of(a, b), Set.of(), Set.of(), Weights.W1, 1, false);
    }

    private static PoolGate.Applicant applicant(Candidate c) {
        return new PoolGate.Applicant(c.birthday(), true, true, false, 2, false, false, null, c.hasOpenMatch());
    }

    private static MatchingConsent granted(long userId) {
        return new MatchingConsent(userId, ConsentStatus.GRANTED, 1, NOW, null, ConsentPolicy.POLICY_VERSION, NOW);
    }

    private static MatchingConsent revoked(long userId) {
        return new MatchingConsent(userId, ConsentStatus.REVOKED, 2, NOW, NOW, ConsentPolicy.POLICY_VERSION, NOW);
    }
}
