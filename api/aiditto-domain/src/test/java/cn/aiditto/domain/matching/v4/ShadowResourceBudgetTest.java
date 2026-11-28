package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.BusinessFeatureCoverage;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.OnboardingFieldMap;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Resource and contract checks. The fixture solve is not a business-profile result. */
class ShadowResourceBudgetTest {
    @Test
    void defaultBudgetRefusesUnprotectedScaleAndARaisedEdgeCap() {
        ShadowResourceBudget budget = ShadowResourceBudget.isolatedDefault();
        assertThat(budget.maxUsers()).isEqualTo(200);
        assertThat(budget.maxQualifiedEdges()).isEqualTo(GlobalBudgets.MAX_SOLVER_EDGES);
        assertThat(budget.refuseUsers(20)).isNull();
        assertThat(budget.refuseUsers(201)).isEqualTo("USER_CAP");
        assertThat(budget.refuseUsers(500)).isEqualTo("USER_CAP");
        assertThat(budget.refuseUsers(5_000)).isEqualTo("UNPROTECTED_SCALE");
        assertThat(budget.refuseUsers(10_000)).isEqualTo("UNPROTECTED_SCALE");
        ShadowResourceBudget wider = new ShadowResourceBudget(500, GlobalBudgets.MAX_SOLVER_EDGES,
                GlobalBudgets.MAX_SOLVER_EDGES, 20_000L, 1, 0, 1_048_576L);
        assertThat(wider.refuseUsers(500)).isEqualTo("CANDIDATE_CAP");
        assertThatThrownBy(() -> new ShadowResourceBudget(200, 50_000, 50_001, 20_000L, 1, 0, 1_048_576L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShadowResourceBudget(5_000, 50_000, 50_000, 20_000L, 1, 0, 1_048_576L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredDeadlineDiscardsTheResultAndAMathFixtureIsNotABusinessPlan() {
        GlobalMatchingGraph graph = GlobalCandidateGraphBuilder.build(
                GlobalMatchFixture.scale(4), GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL);
        GlobalMatchingResult late = V4GlobalMatchingEngine.solve(graph, Objective.SELECTED, System.nanoTime() - 1);
        assertThat(late.pairs()).isEmpty();
        assertThat(late.executablePlan()).isFalse();
        assertThat(late.statusDetail()).contains("wall budget");

        GlobalMatchingResult math = V4GlobalMatchingEngine.solve(GlobalMatchFixture.localOptimum(), Objective.SELECTED);
        assertThat(math.pairs()).isNotEmpty();
        assertThat(math.executablePlan()).isFalse();
        Map<String, Integer> audit = ShadowPlanAudit.audit(GlobalMatchFixture.localOptimum(), math.pairs(),
                Map.of(1L, new ShadowPlanAudit.Live(1, true, "GRANTED", 1, 1, "abc")), Map.of());
        assertThat(ShadowPlanAudit.total(audit)).isPositive();
    }

    @Test
    void mappedOnboardingDoesNotOverlapTheFrozenAlignmentTargets() {
        BusinessFeatureCoverage.ContractGap gap = BusinessFeatureCoverage.onboardingContract();
        assertThat(gap.mappedFeatures()).isEqualTo(OnboardingFieldMap.mappedCount()).isEqualTo(22);
        assertThat(gap.alignmentPreferences()).isEqualTo(4);
        assertThat(gap.overlap()).isZero();
        var snapshot = RelationshipProfileProjector.project(7, Map.of(
                "hobbies", List.of("reading"),
                "readingGenres", List.of("scifi"),
                "relationshipLooking", List.of("serious"),
                "matchingStyle", "steady",
                "educationLevel", "preferNot",
                "expectedAgeRange", List.of(22, 30),
                "heightRange", Map.of("any", true),
                "educationImportance", "none",
                "expectedGender", List.of("women"),
                "occupation", "工程师"), FeatureRegistry.v1());
        BusinessFeatureCoverage.Report report = BusinessFeatureCoverage.of(snapshot);
        assertThat(report.alignmentTargets()).isEqualTo(4);
        assertThat(report.scoreableAlignments()).isZero();
        assertThat(report.legalUnscored()).isPositive();
        assertThat(report.noAlignmentTarget()).isPositive();
        assertThat(snapshot.preferenceJson()).doesNotContain("partner.communication", "partner.pace");
    }
}
