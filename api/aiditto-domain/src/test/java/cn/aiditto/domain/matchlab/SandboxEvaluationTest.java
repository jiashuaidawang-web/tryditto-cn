package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxEvaluationTest {
    @Test
    void frozenCountsStaySeparateAndSandboxDoesNotTrainAModel() {
        LabWorkbench lab = new LabWorkbench();
        SandboxEvaluation.Report frozen = lab.loop().strategy("FROZEN_EXPERIMENT");
        assertThat(frozen.observations()).isEqualTo(128);
        assertThat(frozen.labelVersion()).isEqualTo("f03-labels-2");
        assertThat(frozen.counts()).anyMatch(count -> count.metric().equals("未知或过期") && count.numerator() > 0);
        assertThat(frozen.strategies()).allMatch(row -> row.realLift().equals("NOT_IDENTIFIED"));
        assertThatThrownBy(() -> lab.loop().rejectStrategyActivation()).isInstanceOf(FeedbackRejection.class);

        SandboxLoop.GlobalView first = lab.loop().solve("G1", java.util.List.of(), "test");
        SandboxLoop.GlobalView second = lab.loop().solve("G2", java.util.List.of(), "test");
        SandboxEvaluation.Report sandbox = lab.loop().strategy("SYNTHETIC_SANDBOX");
        assertThat(sandbox.calibration()).isNotEmpty().allMatch(row -> row.status().equals("INSUFFICIENT"));
        SandboxLoop.PairView pair = first.pairs().get(0);
        lab.loop().open(first.runId(), pair.lowId(), pair.highId());
        SandboxEvaluation.ReplayResult replay = lab.loop().replay(first.runId(), "test");
        assertThat(replay.executionMode()).isEqualTo("CURRENT_ENGINE_REPLAY");
        assertThat(replay.semanticMatch()).isTrue();
        assertThat(lab.runs().stream().filter(run -> run.runId().equals(first.runId())).count()).isEqualTo(1);
        SandboxEvaluation.CompareResult compared = lab.loop().compareRuns(first.runId(), second.runId());
        assertThat(compared.samePolicy()).isFalse();
        assertThat(compared.note()).contains("不能写成策略增益");
        assertThat(lab.loop().safety().production()).isEqualTo("NO-GO");
        assertThat(lab.loop().safety().risks()).anyMatch(risk -> risk.riskId().equals("R-RELATIONSHIP-QUALITY"));
        assertThat(lab.loop().exportRun(first.runId())).contains("sandbox-export-1");
        assertThatThrownBy(() -> lab.loop().replay("run-missing", "test"))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("HISTORY_UNAVAILABLE");
    }
}
