package cn.aiditto.domain.compatibility.v4;

import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipTypes.ConstraintType;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.SemanticSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BidirectionalCompatibilityTest {
    private final BidirectionalCompatibilityEngine engine = BidirectionalCompatibilityEngine.v1();

    @Test
    void directionsStaySeparateAndSwapKeepsTheAggregate() {
        CompatibilitySubject left = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "quiet", "low", "early"});
        CompatibilitySubject right = person(2, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "frequent", "high", "late"});
        PairCompatibilityResult forward = fit(left, right);
        PairCompatibilityResult backward = fit(right, left);
        assertThat(forward.scoreAB()).isEqualTo(100.0);
        assertThat(forward.scoreBA()).isEqualTo(25.0);
        assertThat(backward.scoreAB()).isEqualTo(forward.scoreBA());
        assertThat(backward.scoreBA()).isEqualTo(forward.scoreAB());
        assertThat(backward.harmonicMean()).isEqualTo(forward.harmonicMean());
        assertThat(backward.arithmeticMean()).isEqualTo(forward.arithmeticMean());
        assertThat(forward.harmonicMean()).isEqualTo(40.0);
        assertThat(forward.arithmeticMean()).isEqualTo(62.5);
        assertThat(forward.minDirection()).isEqualTo(25.0);
        assertThat(forward.geometricMean()).isEqualTo(50.0);
        assertThat(forward.profileVersionA()).isEqualTo(1);
        assertThat(forward.generationA()).isEqualTo(1);
        assertThat(forward.explanationAB().chooserId()).isEqualTo(1);
        assertThat(forward.explanationBA().chooserId()).isEqualTo(2);
    }

    @Test
    void importanceWeightsAreSuperThreeKindaTwoAndNoneOne() {
        CompatibilitySubject self = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        CompatibilitySubject chooser = subject(2, List.of(feature("self.pace", "slow")), List.of(
                CompatibilitySubject.preference("partner.pace", "slow", Importance.SUPER),
                CompatibilitySubject.preference("partner.communication", "frequent", Importance.NONE)));
        PairCompatibilityResult result = fit(chooser, self);
        assertThat(result.scoreAB()).isEqualTo(75.0);
        FeatureMatchContribution pace = row(result, "AB", "partner.pace");
        FeatureMatchContribution communication = row(result, "AB", "partner.communication");
        assertThat(pace.weight()).isEqualTo(3);
        assertThat(communication.weight()).isEqualTo(1);
        assertThat(communication.satisfaction()).isEqualTo(Satisfaction.UNSATISFIED);
    }

    @Test
    void unknownIsNotZeroOrFiftyAndAZeroDenominatorStaysUndefined() {
        CompatibilitySubject chooser = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "quiet", "low", "early"});
        CompatibilitySubject missing = subject(2, List.of(), List.of(CompatibilitySubject.preference("partner.pace", "slow", Importance.SUPER)));
        PairCompatibilityResult unknown = fit(chooser, missing);
        assertThat(unknown.scoreAB()).isNull();
        assertThat(unknown.coverageAB()).isEqualTo(0.0);
        assertThat(unknown.status()).isEqualTo(CompatibilityStatus.INSUFFICIENT_EVIDENCE);
        assertThat(row(unknown, "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.UNKNOWN);
        assertThat(row(unknown, "AB", "partner.pace").satisfactionValue()).isNull();

        CompatibilitySubject unbounded = subject(3, List.of(feature("self.pace", "slow")), List.of(
                new PreferenceTarget("partner.pace", Operator.EQ, new FeatureValue.Unbounded(), Importance.SUPER,
                        ConstraintType.UNBOUNDED, 1.0, List.of(), FeatureStatus.CONFIRMED)));
        PairCompatibilityResult open = fit(unbounded, chooser);
        assertThat(open.scoreAB()).isNull();
        assertThat(open.coverageAB()).isNull();
        assertThat(row(open, "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.NOT_APPLICABLE);
        assertThat(row(open, "AB", "partner.pace").reasonCode()).isEqualTo("UNBOUNDED");
    }

    @Test
    void withheldContestedAndNoPreferenceDoNotBecomeADefaultScore() {
        CompatibilitySubject chooser = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[]{"partner-only"});
        CompatibilitySubject withheld = subject(2, List.of(
                CompatibilitySubject.feature("self.pace", new FeatureValue.Withheld(), FeatureStatus.CONFIRMED),
                feature("self.communication", "quiet")), List.of());
        PairCompatibilityResult hidden = fit(chooser, withheld);
        assertThat(hidden.evidenceContributions()).noneMatch(row -> "WITHHELD".equals(row.actualValue()) || "WITHHELD".equals(row.expectedValue()));
        assertThat(row(hidden, "AB", "partner.pace").reasonCode()).isEqualTo("TARGET_NOT_PUBLIC");
        assertThat(row(hidden, "AB", "partner.pace").actualValue()).isNull();
        assertThat(hidden.explanationAB().withheldOmitted()).isZero();
        assertThat(hidden.coverageAB()).isEqualTo(0.0);

        CompatibilitySubject contested = subject(3, List.of(
                CompatibilitySubject.feature("self.pace", null, FeatureStatus.CONTESTED)), List.of());
        PairCompatibilityResult fight = fit(chooser, contested);
        assertThat(row(fight, "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.CONTESTED);
        assertThat(fight.scoreAB()).isNull();

        CompatibilitySubject empty = person(4, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        PairCompatibilityResult none = fit(empty, chooser);
        assertThat(none.explanationAB().status()).isEqualTo(CompatibilityStatus.NO_PREFERENCE);
        assertThat(none.scoreAB()).isNull();
        assertThat(none.scoreBA()).isEqualTo(100.0);
        assertThat(none.harmonicMean()).isNull();
    }

    @Test
    void statedNoneIsDifferentFromHavingNoPreference() {
        CompatibilitySubject target = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        CompatibilitySubject stated = subject(2, List.of(), List.of(
                CompatibilitySubject.preference("partner.pace", "slow", Importance.NONE)));
        PairCompatibilityResult result = fit(stated, target);
        assertThat(result.explanationAB().status()).isEqualTo(CompatibilityStatus.SCORED);
        assertThat(result.scoreAB()).isEqualTo(100.0);
        assertThat(row(result, "AB", "partner.pace").weight()).isEqualTo(1);
    }

    @Test
    void partialSatisfactionExistsOnlyOnRegisteredAdjacentScales() {
        CompatibilitySubject slow = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        CompatibilitySubject wantsSteady = subject(2, List.of(), List.of(
                CompatibilitySubject.preference("partner.pace", "steady", Importance.SUPER),
                CompatibilitySubject.preference("partner.communication", "direct", Importance.SUPER)));
        PairCompatibilityResult adjacent = fit(wantsSteady, slow);
        assertThat(row(adjacent, "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.PARTIALLY_SATISFIED);
        assertThat(row(adjacent, "AB", "partner.communication").satisfaction()).isEqualTo(Satisfaction.UNSATISFIED);

        CompatibilitySubject wantsFast = subject(3, List.of(), List.of(
                CompatibilitySubject.preference("partner.pace", "fast", Importance.SUPER)));
        assertThat(row(fit(wantsFast, slow), "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.UNSATISFIED);
    }

    @Test
    void anUnverifiedHypothesisAndAnIllegalValueAreNotMatches() {
        CompatibilitySubject frequent = person(1, new String[]{"fast", "frequent", "high", "late"}, new String[0]);
        CompatibilitySubject support = subject(2, List.of(), List.of(
                CompatibilitySubject.preference("partner.support", "close", Importance.SUPER)));
        PairCompatibilityResult hypothesis = fit(support, frequent);
        assertThat(row(hypothesis, "AB", "partner.support").reasonCode()).isEqualTo("NO_ALIGNMENT");
        assertThat(hypothesis.scoreAB()).isNull();
        assertThat(hypothesis.evidenceContributions()).noneMatch(row -> "self.communication".equals(row.targetFeatureCode())
                && row.satisfaction() == Satisfaction.SATISFIED);

        CompatibilitySubject illegal = subject(3, List.of(), List.of(
                CompatibilitySubject.preference("partner.pace", "nope", Importance.SUPER)));
        assertThat(row(fit(illegal, frequent), "AB", "partner.pace").reasonCode()).isEqualTo("ILLEGAL_VALUE");
    }

    @Test
    void ungatedModelSupportCannotEnterTheScore() {
        CompatibilitySubject target = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        CompatibilitySubject pending = subject(2, List.of(), List.of(
                CompatibilitySubject.preference("partner.pace", "slow", Importance.SUPER)))
                .support("partner.pace", null, SemanticSupport.SEMANTIC_SUPPORT_PENDING);
        PairCompatibilityResult result = fit(pending, target);
        assertThat(result.explanationAB().notAdmitted()).isEqualTo(1);
        assertThat(result.evidenceContributions()).isEmpty();
        assertThat(result.scoreAB()).isNull();
    }

    @Test
    void aHardConstraintRemovesTheRankableScore() {
        CompatibilitySubject young = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "quiet", "low", "early"});
        CompatibilitySubject older = person(2, new String[]{"slow", "quiet", "low", "early"}, new String[]{"slow", "quiet", "low", "early"});
        Candidate aged = older.candidate().toBuilder().birthday(LocalDate.of(1980, 1, 1)).ageRange(40, 60).build();
        CompatibilitySubject veto = new CompatibilitySubject(2, 2, 1, true, false, older.fixtureSource(),
                older.self(), older.preference(), older.selfSupport(), older.preferenceSupport(), aged);
        PairCompatibilityResult result = fit(young, veto);
        assertThat(result.status()).isEqualTo(CompatibilityStatus.HARD_VETO);
        assertThat(result.hardFilterResult()).contains("HARD_AGE");
        assertThat(result.scoreAB()).isNull();
        assertThat(result.harmonicMean()).isNull();
        assertThat(result.explanationAB().score()).isNull();
        assertThat(result.evidenceContributions()).isEmpty();
        CompatibilityDecision decision = CompatibilityDecision.from(result);
        assertThat(decision.recommendationEligible()).isFalse();
        assertThat(decision.reasoningEligible()).isFalse();
        assertThat(decision.policyVersion()).isEqualTo(CompatibilityPolicies.VERSION);
    }

    @Test
    void theSameInputProducesTheSameResult() {
        CompatibilitySubject left = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[]{"steady", "quiet", "low", "early"});
        CompatibilitySubject right = person(2, new String[]{"steady", "direct", "high", "flexible"}, new String[]{"slow", "quiet", "low", "early"});
        assertThat(fit(left, right)).isEqualTo(fit(left, right));
    }

    @Test
    void formulaBoundariesDoNotImputeAMissingDirection() {
        assertThat(CompatibilityAggregator.harmonic(0.0, 0.0)).isEqualTo(0.0);
        assertThat(CompatibilityAggregator.harmonic(100.0, 0.0)).isEqualTo(0.0);
        assertThat(CompatibilityAggregator.harmonic(null, 100.0)).isNull();
        assertThat(CompatibilityAggregator.harmonicWithCoverage(100.0, 100.0, 0.59, 1.0)).isNull();
        assertThat(CompatibilityAggregator.harmonicWithCoverage(100.0, 80.0, 0.60, 0.60)).isEqualTo(8000.0 / 90.0);
        assertThat(CompatibilityAggregator.harmonicWithCoverageAndStrength(100.0, 100.0, 1.0, 1.0, 0.59, 1.0)).isNull();
        assertThat(CompatibilityAggregator.lowest(null, 0.0)).isNull();
    }

    @Test
    void aStaleOrForeignProfileIsRefused() {
        CompatibilitySubject current = person(1, new String[]{"slow", "quiet", "low", "early"}, new String[0]);
        CompatibilitySubject withdrawn = new CompatibilitySubject(2, 1, 1, true, true, current.fixtureSource(),
                current.self(), current.preference(), current.selfSupport(), current.preferenceSupport(), Candidate.builder(2).build());
        assertThat(fit(current, withdrawn).status()).isEqualTo(CompatibilityStatus.NOT_CURRENT);
        assertThatThrownBy(() -> CompatibilitySubject.synthetic(3, List.of(), List.of(), Candidate.builder(4).build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompatibilitySubject(3, 1, 1, true, false, "production-user:3",
                current.self(), current.preference(), current.selfSupport(), current.preferenceSupport(), Candidate.builder(3).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operatorsAndRegistryAlignmentsAreExplicit() {
        FeatureAlignmentRegistry registry = FeatureAlignmentRegistry.v1();
        assertThat(registry.version()).isEqualTo("align-1");
        assertThat(registry.find("partner.communication").allowsPartial()).isFalse();
        assertThat(registry.find("partner.pace").ordinal()).containsExactly("slow", "steady", "fast");
        assertThat(registry.unverifiedHypotheses()).anyMatch(item -> item.preferenceCode().equals("partner.support")
                && item.selfCode().equals("self.communication"));
        assertThat(registry.find("partner.support")).isNull();

        CompatibilitySubject target = subject(1, List.of(CompatibilitySubject.feature("self.pace", new FeatureValue.EnumOne("steady"), FeatureStatus.CONFIRMED)), List.of());
        CompatibilitySubject chooser = subject(2, List.of(), List.of(
                new PreferenceTarget("partner.pace", Operator.IN, new FeatureValue.EnumMany(List.of("slow", "steady")),
                        Importance.KINDA, ConstraintType.SOFT, 1.0, List.of(), FeatureStatus.CONFIRMED),
                new PreferenceTarget("partner.age", Operator.ANY, new FeatureValue.Span(20, 30), Importance.SUPER,
                        ConstraintType.DOCUMENTED_EXISTING_RULE, 1.0, List.of(), FeatureStatus.CONFIRMED)));
        PairCompatibilityResult result = fit(chooser, target);
        assertThat(row(result, "AB", "partner.pace").satisfaction()).isEqualTo(Satisfaction.SATISFIED);
        assertThat(row(result, "AB", "partner.pace").weight()).isEqualTo(2);
        assertThat(row(result, "AB", "partner.age").reasonCode()).isEqualTo("DOCUMENTED_EXISTING_RULE");
    }

    private PairCompatibilityResult fit(CompatibilitySubject left, CompatibilitySubject right) {
        return engine.score(left, right, CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, java.util.List.of(left, right)));
    }

    private static CompatibilitySubject person(long id, String[] self, String[] preference) {
        String[] selfCodes = {"self.pace", "self.communication", "self.social.initiative", "self.schedule"};
        String[] prefCodes = {"partner.pace", "partner.communication", "partner.social.initiative", "partner.schedule"};
        List<ProfileFeature> features = new java.util.ArrayList<>();
        List<PreferenceTarget> targets = new java.util.ArrayList<>();
        for (int i = 0; i < self.length; i++) features.add(feature(selfCodes[i], self[i]));
        for (int i = 0; i < preference.length; i++) {
            if ("partner-only".equals(preference[0])) {
                targets.add(CompatibilitySubject.preference(prefCodes[i], "slow", Importance.SUPER));
                break;
            }
            targets.add(CompatibilitySubject.preference(prefCodes[i], preference[i], Importance.SUPER));
        }
        return CompatibilitySubject.synthetic(id, id, 1, true, false, "synthetic-fixture:v4-m02-test",
                features, targets, Candidate.builder(id).hobbies("reading").build());
    }

    private static CompatibilitySubject subject(long id, List<ProfileFeature> self, List<PreferenceTarget> preference) {
        return CompatibilitySubject.synthetic(id, self, preference, Candidate.builder(id).hobbies("reading").build());
    }

    private static ProfileFeature feature(String code, String value) {
        return CompatibilitySubject.feature(code, new FeatureValue.EnumOne(value), FeatureStatus.CONFIRMED);
    }

    private static FeatureMatchContribution row(PairCompatibilityResult result, String direction, String code) {
        return result.evidenceContributions().stream()
                .filter(row -> direction.equals(row.direction()) && code.equals(row.preferenceCode()))
                .findFirst().orElseThrow();
    }
}
