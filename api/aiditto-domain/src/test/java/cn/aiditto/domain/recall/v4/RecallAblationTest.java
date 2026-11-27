package cn.aiditto.domain.recall.v4;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecallAblationTest {
    @Test
    void selectedRoutesBeatTheLegalBaselineWithoutSafetyViolations() {
        RecallAblation.Report report = RecallAblation.run();
        RecallAblation.Stage baseline = report.stages().get(0);
        RecallAblation.Stage selected = report.selected();
        assertThat(selected.dev().recallAt20()).isGreaterThan(baseline.dev().recallAt20());
        assertThat(selected.dev().hardViolations()).isZero();
        assertThat(selected.dev().expiredReturned()).isZero();
        assertThat(selected.dev().withdrawnReturned()).isZero();
        assertThat(report.holdout().hardViolations()).isZero();
        assertThat(report.holdout().expiredReturned()).isZero();
        assertThat(report.holdout().withdrawnReturned()).isZero();
        assertThat(report.ceilingRecall()).isEqualTo(1.0);
        assertThat(report.holdoutGatePassed()).isTrue();
        System.out.println("ABLATION " + selected.name()
                + " dev20=" + selected.dev().recallAt20()
                + " holdout20=" + report.holdout().recallAt20()
                + " precision20=" + report.holdout().precisionAt20()
                + " ceiling=" + report.ceilingRecall());
        for (RecallAblation.Stage stage : report.stages()) {
            System.out.println(stage.name() + " recall20=" + stage.dev().recallAt20()
                    + " recall10=" + stage.dev().recallAt10()
                    + " recall50=" + stage.dev().recallAt50()
                    + " hit=" + stage.dev().hitRateAt20()
                    + " mrr=" + stage.dev().mrr()
                    + " ndcg=" + stage.dev().ndcgAt20()
                    + " p50=" + stage.dev().candidatesP50()
                    + " zero=" + stage.dev().zeroCandidateRate()
                    + " loss10=" + stage.dev().budgetLossAt10()
                    + " cover=" + stage.dev().candidateCoverage()
                    + " both=" + stage.dev().bidirectionalCoverage()
                    + " positives=" + stage.dev().eligiblePositives());
        }
    }
}
