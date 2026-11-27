package cn.aiditto.domain.matching.v4;

import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.matching.PairKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalMatchStudyTest {
    @Test
    void smallGraphsMatchTheExhaustiveOptimum() {
        assertOptimum(GlobalMatchFixture.localOptimum(), Objective.G1_MAX_WEIGHT);
        assertOptimum(GlobalMatchFixture.localOptimum(), Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertOptimum(GlobalMatchFixture.cardinality(), Objective.G1_MAX_WEIGHT);
        assertOptimum(GlobalMatchFixture.cardinality(), Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertOptimum(GlobalMatchFixture.triangle(), Objective.G1_MAX_WEIGHT);
        assertOptimum(GlobalMatchFixture.odd(), Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertOptimum(GlobalMatchFixture.truncation(), Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertOptimum(GlobalMatchFixture.qualityTier(), Objective.G3_QUALITY_TIER);
        assertOptimum(GlobalMatchFixture.fairness(), Objective.G4_FAIRNESS);
        GlobalMatchingResult greedy = V4GlobalMatchingEngine.solve(GlobalMatchFixture.localOptimum(), Objective.G0_GREEDY);
        GlobalMatchingResult best = V4GlobalMatchingEngine.solve(GlobalMatchFixture.localOptimum(), Objective.G1_MAX_WEIGHT);
        assertThat(greedy.totalFitCenti()).isLessThan(best.totalFitCenti());
        assertThat(greedy.safetyViolations()).isZero();
        GlobalMatchingResult heavy = V4GlobalMatchingEngine.solve(GlobalMatchFixture.cardinality(), Objective.G1_MAX_WEIGHT);
        GlobalMatchingResult many = V4GlobalMatchingEngine.solve(GlobalMatchFixture.cardinality(), Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertThat(heavy.pairs()).hasSize(1);
        assertThat(many.pairs()).hasSize(2);
        assertThat(many.totalFitCenti()).isLessThan(heavy.totalFitCenti());
    }

    @Test
    void encodingCannotLetFitOutrankCardinality() {
        int users = GlobalBudgets.MAX_USERS;
        long base = OptimizationWeight.cardinalityBase(users);
        long fewer = (users / 2 - 1L) * base + (users / 2 - 1L) * GlobalBudgets.FIT_CENTI_MAX;
        long more = (users / 2L) * base;
        assertThat(more).isGreaterThan(fewer);
        int pairs = users / 2;
        long fairBase = OptimizationWeight.g4Base(users);
        long fairMore = (long) pairs * (fairBase + 1L);
        long fairFewer = (long) (pairs - 1) * (fairBase + OptimizationWeight.g4SecondaryMax());
        assertThat(fairMore).isGreaterThan(fairFewer);
        assertThat(OptimizationWeight.encodingFits(users)).isTrue();
        assertThat(more).isLessThan(1L << 52);
        assertThat(fairMore).isLessThan(1L << 52);
    }

    @Test
    void refusedEdgesStayOutOfTheMatching() {
        GlobalMatchingGraph none = GlobalMatchingGraph.math("signs", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 2, 0), PreparedEdge.math(3, 4, -5), PreparedEdge.math(1, 3, 140)));
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(none, Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertThat(result.pairs()).isEmpty();
        assertThat(result.safetyViolations()).isZero();
        GlobalMatchingGraph mixed = GlobalMatchingGraph.math("mixed", List.of(1L, 2L, 3L, 4L), List.of(
                PreparedEdge.math(1, 2, 0), PreparedEdge.math(3, 4, 80)));
        GlobalMatchingResult kept = V4GlobalMatchingEngine.solve(mixed, Objective.G1_MAX_WEIGHT);
        assertThat(kept.pairs()).extracting(pair -> pair.lowId() + "-" + pair.highId()).containsExactly("3-4");
        assertThatThrownBy(() -> GlobalMatchingGraph.math("dup", List.of(1L, 2L), List.of(
                PreparedEdge.math(1, 2, 80), PreparedEdge.math(1, 2, 70))).solverByKey())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncationDropsAGloballyNeededEdge() {
        GlobalMatchingGraph full = GlobalMatchFixture.truncation();
        GlobalMatchingGraph top = GlobalCandidateGraphBuilder.topK(full, 1);
        GlobalMatchingResult fullResult = V4GlobalMatchingEngine.solve(full, Objective.G2_CARDINALITY_THEN_WEIGHT);
        GlobalMatchingResult topResult = V4GlobalMatchingEngine.solve(top, Objective.G2_CARDINALITY_THEN_WEIGHT);
        assertThat(fullResult.pairs()).hasSize(2);
        assertThat(topResult.pairs()).hasSize(1);
        assertThat(topResult.fullPopulationGraph()).isFalse();
        assertThat(top.solver()).noneMatch(edge -> edge.lowId() == 1 && edge.highId() == 2);
    }

    @Test
    void populationMatchingKeepsEligibleLabeledPairs() {
        checkPopulation(GlobalMatchFixture.dev(), 40_000);
        checkPopulation(GlobalMatchFixture.holdout(), 50_000);
    }

    @Test
    void orderAndSeedStayStable() {
        GlobalMatchingInput forward = GlobalMatchFixture.dev();
        List<CompatibilitySubject> reversed = new ArrayList<>(forward.eligibleUsers());
        reversed.sort(Comparator.comparingLong(CompatibilitySubject::userId).reversed());
        GlobalMatchingInput backward = GlobalMatchingInput.of(forward.matchingRunId(), forward.cohort(), reversed,
                forward.blockedPairs(), forward.forbiddenPairs(), forward.historicalRestrictions(),
                forward.syntheticUnmatchedWeeks(), forward.seed()).withConsent(40_043, "REVOKED");
        GlobalMatchingResult left = solvePopulation(forward);
        GlobalMatchingResult right = solvePopulation(backward);
        assertThat(keys(left)).isEqualTo(keys(right));
    }

    @Test
    void capDoesNotPretendToBeOptimal() {
        GlobalMatchingGraph capped = GlobalCandidateGraphBuilder.build(GlobalMatchFixture.dev(),
                GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL,
                System.nanoTime() + 60_000_000_000L, 1);
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(capped, Objective.SELECTED);
        assertThat(capped.status()).isEqualTo(GlobalMatchingGraph.CAP_REACHED);
        assertThat(result.optimalForThisGraph()).isFalse();
        assertThat(result.pairs()).isEmpty();
        assertThat(result.unmatchedReasons()).containsEntry("RUN_ABORTED", result.unmatched().size());
    }

    @Test
    void safetyAuditRejectsAPlantedRestriction() {
        GlobalMatchingGraph graph = GlobalMatchFixture.odd();
        var counts = GlobalSafetyAudit.audit(graph, List.of(PairKey.of(1, 3)));
        assertThat(GlobalSafetyAudit.total(counts)).isPositive();
    }

    @Test
    void scaleStopsAtTheFrozenWall() throws Exception {
        List<GlobalMatchStudy.ScalePoint> points = GlobalMatchStudy.scale();
        assertThat(points).isNotEmpty();
        boolean completed = false;
        for (GlobalMatchStudy.ScalePoint point : points) {
            System.out.println("scale users=" + point.users() + " considered=" + point.considered()
                    + " eligible=" + point.eligible() + " solver=" + point.solverEdges()
                    + " scoreMs=" + point.scoreMs() + " solveMs=" + point.solveMs()
                    + " status=" + point.status() + " " + point.detail());
            if (GlobalMatchingGraph.COMPLETE.equals(point.status())) {
                completed = true;
                assertThat(point.detail()).contains("safety=0");
                assertThat(point.solveMs()).isNotNull();
            } else {
                assertThat(point.detail()).doesNotContain("optimal=true");
            }
        }
        assertThat(completed).isTrue();
        String json = GlobalMatchStudy.labJson();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/m04-match-lab-cases.json"), json);
        assertThat(json).contains("local-optimum", "cardinality", "triangle", "odd-node", "empty", "topk-1", "quality-tier", "fairness", "dev-cohort");
    }

    private static void checkPopulation(GlobalMatchingInput input, int base) {
        GlobalMatchingGraph full = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL);
        GlobalMatchingGraph shortlist = GlobalCandidateGraphBuilder.build(input, GlobalCandidateGraphBuilder.Strategy.F_SHORTLIST);
        System.out.println(GlobalMatchStudy.graphLine(full));
        System.out.println(GlobalMatchStudy.graphLine(shortlist));
        for (Objective objective : Objective.values()) {
            GlobalMatchingResult result = V4GlobalMatchingEngine.solve(full, objective);
            System.out.println(input.cohort() + " " + GlobalMatchStudy.line(result));
            assertThat(result.safetyViolations()).isZero();
            assertThat(result.pairs()).allMatch(GlobalMatchingResult.MatchedPair::recommendationEligible);
        }
        GlobalMatchingResult selected = solvePopulation(input);
        int kept = GlobalMatchStudy.labeledKept(selected, GlobalMatchFixture.labeled(base));
        System.out.println(input.cohort() + " labeled=" + kept + "/" + GlobalMatchFixture.labeled(base).size());
        assertThat(kept).isEqualTo(GlobalMatchFixture.labeled(base).size());
        assertThat(keys(selected)).doesNotContain(PairKey.of(base + 51, base + 52));
        Set<Long> forbidden = Set.of((long) base + 41, (long) base + 42, (long) base + 43, (long) base + 91);
        for (var pair : selected.pairs()) {
            assertThat(forbidden).doesNotContain(pair.lowId(), pair.highId());
        }
        assertThat(reason(selected, base + 41)).isEqualTo("PROFILE_WITHDRAWN");
        assertThat(reason(selected, base + 43)).isEqualTo("CONSENT_REVOKED");
        GlobalMatchingGraph top = GlobalCandidateGraphBuilder.topK(full, 1);
        GlobalMatchingResult topResult = V4GlobalMatchingEngine.solve(top, Objective.SELECTED);
        System.out.println(input.cohort() + " top1 " + GlobalMatchStudy.line(topResult));
        assertThat(topResult.safetyViolations()).isZero();
    }

    private static void assertOptimum(GlobalMatchingGraph graph, Objective objective) {
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(graph, objective);
        ExhaustiveMatcher.Best best = ExhaustiveMatcher.best(graph.input(), graph.solver(), objective);
        assertThat(result.safetyViolations()).isZero();
        assertThat(keys(result)).isEqualTo(new HashSet<>(best.pairs()));
        System.out.println(GlobalMatchStudy.line(result));
    }

    private static GlobalMatchingResult solvePopulation(GlobalMatchingInput input) {
        return V4GlobalMatchingEngine.solve(GlobalCandidateGraphBuilder.build(input,
                GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL), Objective.SELECTED);
    }

    private static Set<PairKey> keys(GlobalMatchingResult result) {
        Set<PairKey> keys = new HashSet<>();
        for (var pair : result.pairs()) keys.add(pair.key());
        return keys;
    }

    private static String reason(GlobalMatchingResult result, long userId) {
        for (var person : result.unmatched()) if (person.userId() == userId) return person.finalReason();
        return "MATCHED";
    }
}
