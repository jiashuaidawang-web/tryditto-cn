package cn.aiditto.domain.compatibility.v4;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityEvaluationTest {
    @Test
    void labelsAreHandAssignedAndCoverBothSplits() {
        List<CompatibilityPopulation.LabeledPair> all = CompatibilityPopulation.all();
        assertThat(all).hasSizeGreaterThanOrEqualTo(100);
        assertThat(CompatibilityPopulation.split("dev").size()).isGreaterThan(CompatibilityPopulation.split("holdout").size());
        assertThat(all).extracting(CompatibilityPopulation.LabeledPair::grade)
                .contains(CompatibilityPopulation.Grade.STRONG, CompatibilityPopulation.Grade.WEAK,
                        CompatibilityPopulation.Grade.ONE_WAY, CompatibilityPopulation.Grade.CONFLICT,
                        CompatibilityPopulation.Grade.INSUFFICIENT, CompatibilityPopulation.Grade.HARD_VETO,
                        CompatibilityPopulation.Grade.CONTESTED);
        CompatibilityPopulation.LabeledPair devWeak = CompatibilityPopulation.split("dev").stream()
                .filter(pair -> pair.slot() == CompatibilityPopulation.Slot.WEAK).findFirst().orElseThrow();
        CompatibilityPopulation.LabeledPair holdoutWeak = CompatibilityPopulation.split("holdout").stream()
                .filter(pair -> pair.slot() == CompatibilityPopulation.Slot.WEAK).findFirst().orElseThrow();
        assertThat(devWeak.candidate().self().features()).anyMatch(feature -> "self.communication".equals(feature.featureCode())
                && "frequent".equals(((cn.aiditto.domain.relationship.FeatureValue.EnumOne) feature.value()).value()));
        assertThat(holdoutWeak.candidate().self().features()).anyMatch(feature -> "self.communication".equals(feature.featureCode())
                && "quiet".equals(((cn.aiditto.domain.relationship.FeatureValue.EnumOne) feature.value()).value()));
    }

    @Test
    void devSelectionUsesTheFrozenRuleAndDoesNotReadHoldout() {
        CompatibilityAblation.Run run = CompatibilityAblation.run();
        System.out.println(CompatibilityAblation.format(run));
        assertThat(run.selected()).isEqualTo(CompatibilityAblation.Method.B5);
        assertThat(run.offlineGo()).isTrue();
        CompatibilityAblation.MethodReport dev = report(run.dev(), CompatibilityAblation.Method.B5);
        CompatibilityAblation.MethodReport b0 = report(run.dev(), CompatibilityAblation.Method.B0);
        CompatibilityAblation.MethodReport b1 = report(run.dev(), CompatibilityAblation.Method.B1);
        CompatibilityAblation.MethodReport b2 = report(run.dev(), CompatibilityAblation.Method.B2);
        CompatibilityAblation.MethodReport b3 = report(run.dev(), CompatibilityAblation.Method.B3);
        assertThat(dev.pairwiseCorrect()).isGreaterThan(b0.pairwiseCorrect());
        assertThat(dev.pairwiseCorrect()).isGreaterThan(b2.pairwiseCorrect());
        assertThat(dev.pairwiseCorrect()).isGreaterThan(b3.pairwiseCorrect());
        assertThat(dev.oneWayFalse()).isLessThan(b1.oneWayFalse());
        assertThat(dev.hardViolations()).isZero();
        assertThat(dev.unknownMishandled()).isZero();
        assertThat(dev.withheldLeaks()).isZero();
        assertThat(dev.directionSeparated()).isEqualTo(dev.oneWayTotal());
        assertThat(report(run.holdout(), CompatibilityAblation.Method.B5).hardViolations()).isZero();
    }

    @Test
    void recallLossIsReportedSeparatelyFromScoring() {
        CompatibilityRecallStudy.Report report = CompatibilityRecallStudy.run();
        System.out.println("recall preferred=" + report.preferred()
                + " top10=" + report.ceiling(report.recalledAt10())
                + " top20=" + report.ceiling(report.recalledAt20())
                + " top50=" + report.ceiling(report.recalledAt50())
                + " fullScored=" + report.scoredInFullPool()
                + " top20Scored=" + report.top20PreferredScored());
        assertThat(report.scoredInFullPool()).isEqualTo(report.preferred());
        assertThat(report.recalledAt20()).isZero();
        assertThat(report.recalledAt50()).isPositive().isLessThan(report.preferred());
        assertThat(report.top20PreferredScored()).isZero();
        assertThat(report.note()).contains("7/45");
    }

    @Test
    void eightCasesKeepScoresCoverageAndTheVetoApart() {
        List<CompatibilityCases.Story> stories = CompatibilityCases.eight();
        assertThat(stories).extracting(CompatibilityCases.Story::id)
                .containsExactly("M02-C1", "M02-C2", "M02-C3", "M02-C4", "M02-C5", "M02-C6", "M02-C7", "M02-C8");
        assertThat(stories.get(0).result().scoreAB()).isEqualTo(100.0);
        assertThat(stories.get(2).result().scoreAB()).isEqualTo(100.0);
        assertThat(stories.get(2).result().scoreBA()).isEqualTo(25.0);
        assertThat(stories.get(4).result().strongPreferenceConflicts()).isNotEmpty();
        assertThat(stories.get(5).result().scoreAB()).isEqualTo(100.0);
        assertThat(stories.get(5).result().coverageAB()).isEqualTo(0.25);
        assertThat(stories.get(5).result().harmonicWithCoverage()).isNull();
        assertThat(stories.get(6).result().scoreAB()).isNull();
        assertThat(stories.get(7).result().status()).isEqualTo(CompatibilityStatus.HARD_VETO);
        assertThat(stories.get(7).result().scoreAB()).isNull();
        String json = CompatibilityCases.json();
        assertThat(json).contains("\"id\": \"M02-C8\"").doesNotContain("WITHHELD");
        assertThat(json).contains("\"status\": \"HARD_VETO\"");
    }

    private static CompatibilityAblation.MethodReport report(List<CompatibilityAblation.MethodReport> reports,
                                                             CompatibilityAblation.Method method) {
        return reports.stream().filter(report -> report.method() == method).findFirst().orElseThrow();
    }
}
