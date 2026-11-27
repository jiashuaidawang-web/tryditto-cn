package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.SemanticSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class M02FinalTest {
    private final BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();

    @Test
    void pastPairsAndRevokedConsentBlockBeforeAnyProfileRow() {
        CompatibilitySubject left = matched(1);
        CompatibilitySubject right = matched(2);
        CompatibilityContext history = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                BidirectionalCompatibilityEngine.NOW, List.of(left, right), Set.of(PairKey.of(1, 2)), Set.of());
        PairCompatibilityResult blocked = engine.score(left, right, history);
        assertThat(blocked.status()).isEqualTo(CompatibilityStatus.HARD_VETO);
        assertThat(blocked.hardFilterResult()).contains("HARD_NEVER_MATCHED");
        assertThat(blocked.evidenceContributions()).isEmpty();
        assertThat(blocked.explanationAB().score()).isNull();
        CompatibilityDecision decision = CompatibilityDecision.from(blocked);
        assertThat(decision.reasoningEligible()).isFalse();
        assertThat(decision.recommendationEligible()).isFalse();

        CompatibilityContext open = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                BidirectionalCompatibilityEngine.NOW, List.of(left, right));
        CompatibilityContext revoked = new CompatibilityContext(open.input(), open.now(), open.applicants(),
                Map.of(1L, CompatibilityContext.granted(1, open.now()),
                        2L, new MatchingConsent(2, ConsentStatus.REVOKED, 1, open.now(), open.now(),
                                cn.aiditto.domain.consent.ConsentPolicy.POLICY_VERSION, open.now())));
        PairCompatibilityResult unauthorized = engine.score(left, right, revoked);
        assertThat(unauthorized.status()).isEqualTo(CompatibilityStatus.NOT_ELIGIBLE);
        assertThat(unauthorized.hardFilterResult()).contains("CONSENT_REVOKED");
        assertThat(unauthorized.evidenceContributions()).isEmpty();
    }

    @Test
    void provisionalTargetsStayInTheCoverageDenominator() {
        CompatibilitySubject chooser = subject(1, List.of(), List.of(
                pref("partner.pace", "slow"),
                pref("partner.communication", "quiet"),
                pref("partner.social.initiative", "low"),
                pref("partner.schedule", "early")));
        CompatibilitySubject target = subject(2, List.of(
                CompatibilitySubject.feature("self.pace", new FeatureValue.EnumOne("slow"), FeatureStatus.CONFIRMED),
                CompatibilitySubject.feature("self.communication", new FeatureValue.EnumOne("quiet"), FeatureStatus.PROVISIONAL),
                CompatibilitySubject.feature("self.social.initiative", new FeatureValue.EnumOne("low"), FeatureStatus.PROVISIONAL),
                CompatibilitySubject.feature("self.schedule", new FeatureValue.EnumOne("early"), FeatureStatus.PROVISIONAL)), List.of());
        PairCompatibilityResult result = fit(chooser, target);
        assertThat(result.scoreAB()).isEqualTo(100.0);
        assertThat(result.coverageAB()).isEqualTo(0.25);
        assertThat(result.evidenceContributions()).noneMatch(row -> "WITHHELD".equals(row.actualValue()));
        assertThat(result.explanationAB().evidence().unknownWeight()).isEqualTo(9.0);

        CompatibilitySubject invalidPreference = subject(3, List.of(), List.of(
                pref("partner.pace", "slow"),
                CompatibilitySubject.preference("partner.communication", Operator.EQ, new FeatureValue.EnumOne("quiet"),
                        Importance.SUPER, ConstraintType.SOFT, FeatureStatus.PROVISIONAL)));
        CompatibilitySubject quiet = subject(4, List.of(feature("self.pace", "slow"), feature("self.communication", "quiet")), List.of());
        PairCompatibilityResult dropped = fit(invalidPreference, quiet);
        assertThat(dropped.explanationAB().notAdmitted()).isEqualTo(1);
        assertThat(dropped.coverageAB()).isEqualTo(1.0);
        assertThat(dropped.evidenceContributions()).noneMatch(row -> "partner.communication".equals(row.preferenceCode()));
    }

    @Test
    void contestedAndUnevaluableStrongPreferencesCannotBeRecommended() {
        CompatibilitySubject chooser = subject(1, List.of(feature("self.pace", "slow"), feature("self.communication", "quiet")), List.of(
                pref("partner.pace", "slow"),
                pref("partner.communication", "quiet")));
        CompatibilitySubject target = subject(2, List.of(
                feature("self.pace", "slow"),
                CompatibilitySubject.feature("self.communication", null, FeatureStatus.CONTESTED),
                feature("self.social.initiative", "low"),
                feature("self.schedule", "early")), List.of(
                pref("partner.pace", "slow"),
                pref("partner.communication", "quiet"),
                pref("partner.social.initiative", "low"),
                pref("partner.schedule", "early")));
        PairCompatibilityResult result = fit(chooser, target);
        assertThat(result.scoreAB()).isEqualTo(100.0);
        assertThat(result.coverageAB()).isEqualTo(0.5);
        CompatibilityDecision decision = CompatibilityDecision.from(result);
        assertThat(decision.recommendationEligible()).isFalse();
        assertThat(decision.reasoningEligible()).isTrue();
        assertThat(decision.reasons()).contains("CONTESTED_PRESENT", "STRONG_PREFERENCE_UNEVALUABLE", "RECOMMENDATION_REFUSED");
        assertThat(decision.formulaVersion()).isEqualTo(CompatibilityFormula.VERSION);
        assertThat(decision.policyVersion()).isEqualTo("compat-decision-1");

        CompatibilitySubject hidden = subject(5, List.of(
                CompatibilitySubject.feature("self.communication", new FeatureValue.Withheld(), FeatureStatus.CONFIRMED)), List.of());
        CompatibilitySubject asking = subject(6, List.of(), List.of(pref("partner.communication", "quiet")));
        PairCompatibilityResult notPublic = fit(asking, hidden);
        assertThat(notPublic.evidenceContributions()).noneMatch(row -> row.actualValue() != null && row.actualValue().contains("WITHHELD"));
        assertThat(notPublic.coverageAB()).isEqualTo(0.0);
        assertThat(CompatibilityDecision.from(notPublic).recommendationEligible()).isFalse();
    }

    @Test
    void aWithheldTargetKeepsTheFrozenRankAndRefusesRecommendation() {
        CompatibilityPopulation.LabeledPair pair = CompatibilityPopulation.split("dev").stream()
                .filter(item -> item.slot() == CompatibilityPopulation.Slot.WITHHELD)
                .findFirst().orElseThrow();
        PairCompatibilityResult result = engine.score(pair.seeker(), pair.candidate(), CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                List.of(pair.seeker(), pair.candidate())));
        assertThat(result.scoreAB()).isEqualTo(100.0);
        assertThat(result.coverageAB()).isEqualTo(0.75);
        assertThat(result.harmonicWithCoverage()).isEqualTo(100.0);
        assertThat(result.evidenceContributions()).noneMatch(row -> "WITHHELD".equals(row.actualValue()));
        CompatibilityDecision decision = CompatibilityDecision.from(result);
        assertThat(decision.recommendationEligible()).isFalse();
        assertThat(decision.reasoningEligible()).isTrue();
        assertThat(decision.reasons()).contains("STRONG_PREFERENCE_UNEVALUABLE");
    }

    @Test
    void theFrozenBaselineStillSelectsB5() {
        CompatibilityAblation.Run run = CompatibilityAblation.run();
        assertThat(run.selected()).isEqualTo(CompatibilityAblation.Method.B5);
        assertThat(run.offlineGo()).isTrue();
        assertThat(report(run.dev()).pairwiseText()).isEqualTo("264/264");
        assertThat(report(run.holdout()).pairwiseText()).isEqualTo("99/99");
        assertThat(report(run.dev()).hardViolations()).isZero();
        assertThat(report(run.dev()).withheldLeaks()).isZero();
    }

    private static CompatibilityAblation.MethodReport report(List<CompatibilityAblation.MethodReport> reports) {
        return reports.stream().filter(item -> item.method() == CompatibilityAblation.Method.B5).findFirst().orElseThrow();
    }

    private PairCompatibilityResult fit(CompatibilitySubject left, CompatibilitySubject right) {
        return engine.score(left, right, CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, List.of(left, right)));
    }

    private static CompatibilitySubject matched(long id) {
        return subject(id, List.of(feature("self.pace", "slow")), List.of(pref("partner.pace", "slow")));
    }

    private static PreferenceTarget pref(String code, String value) {
        return CompatibilitySubject.preference(code, value, Importance.SUPER);
    }

    private static ProfileFeature feature(String code, String value) {
        return CompatibilitySubject.feature(code, new FeatureValue.EnumOne(value), FeatureStatus.CONFIRMED);
    }

    private static CompatibilitySubject subject(long id, List<ProfileFeature> self, List<PreferenceTarget> preference) {
        Candidate candidate = Candidate.builder(id).hobbies("reading").build();
        CompatibilitySubject person = CompatibilitySubject.synthetic(id, self, preference, candidate);
        return person.support("self.pace", SemanticSupport.USER_CONFIRMED, null);
    }
}
