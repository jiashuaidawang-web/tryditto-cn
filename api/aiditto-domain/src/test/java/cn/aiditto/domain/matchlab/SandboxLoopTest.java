package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxLoopTest {
    @Test
    void sandboxSolveKeepsIneligibleEdgesOutAndMathPlansCannotOpenFeedback() {
        LabWorkbench lab = new LabWorkbench();
        SandboxLoop.GlobalView live = lab.loop().solve("G2", List.of(), "test");
        assertThat(live.executionMode()).isEqualTo("ENGINE_EXECUTED");
        assertThat(live.usablePlan()).isTrue();
        assertThat(live.safetyViolations()).isZero();
        assertThat(live.edges()).noneMatch(edge -> edge.solver() && (edge.lowId() == 71006 || edge.highId() == 71006));
        SandboxLoop.GlobalView again = lab.loop().solve("G2", List.of(), "test");
        assertThat(again.pairs()).extracting(SandboxLoop.PairView::key)
                .containsExactlyElementsOf(live.pairs().stream().map(SandboxLoop.PairView::key).toList());
        SandboxLoop.GlobalView math = lab.loop().compare("test");
        assertThat(math.executionMode()).isEqualTo("MATH_COMPARE");
        assertThat(math.usablePlan()).isFalse();
        assertThat(math.pairs()).extracting(SandboxLoop.PairView::key)
                .isNotEqualTo(math.comparison().pairs().stream().map(SandboxLoop.PairView::key).toList());
        assertThatThrownBy(() -> lab.loop().open(math.runId(), 1, 3))
                .isInstanceOf(LabWorkbench.LabRejection.class);
    }

    @Test
    void feedbackCasesFollowTheLedgerAndDoNotInventAPreference() {
        LabWorkbench lab = new LabWorkbench();
        assertThat(lab.loop().runCase("B", "test").observed()).isTrue();
        assertThat(lab.loop().runCase("C", "test").observed()).isTrue();
        assertThat(lab.loop().runCase("D", "test").observed()).isTrue();
        SandboxLoop.GlobalView live = lab.loop().solve("G2", List.of(), "test");
        SandboxLoop.PairView pair = live.pairs().get(0);
        SandboxLoop.FeedbackView opened = lab.loop().open(live.runId(), pair.lowId(), pair.highId());
        SandboxLoop.FeedbackView accepted = lab.loop().act(opened.ledgerKey(), "accept", pair.lowId(), null);
        String acceptId = accepted.events().stream().filter(event -> event.type().equals("MATCH_ACCEPTED")).findFirst().orElseThrow().eventId();
        SandboxLoop.FeedbackView corrected = lab.loop().act(opened.ledgerKey(), "correct", pair.lowId(), acceptId);
        assertThat(corrected.events()).anyMatch(event -> event.eventId().equals(acceptId) && event.superseded());
        assertThat(corrected.projection().responseA()).isNotEqualTo("ACCEPTED");
        assertThatThrownBy(() -> lab.loop().act(opened.ledgerKey(), "accept", 71007, null))
                .isInstanceOf(LabWorkbench.LabRejection.class);
    }

    @Test
    void confirmationChangesOneFeatureAndRetractionDoesNotRestoreTheOldProfile() {
        LabWorkbench lab = new LabWorkbench();
        SandboxLoop.CaseView learned = lab.loop().runCase("E", "test");
        assertThat(learned.observed()).isTrue();
        LabWorkbench.LabRun child = lab.runs().stream().filter(run -> learned.rightRunId().equals(run.runId())).findFirst().orElseThrow();
        assertThat(child.parentRunId()).isEqualTo(learned.leftRunId());
        assertThat(child.inputSnapshot()).isNotBlank();
        assertThat(child.inputHash()).hasSize(64);
        SandboxLoop.CaseView retracted = lab.loop().runCase("F", "test");
        assertThat(retracted.observed()).isTrue();
        SandboxLoop.LearningView proposal = lab.loop().propose(71002, "partner.pace", "steady", "");
        assertThatThrownBy(() -> lab.loop().decide(proposal.proposal().proposalId(), "confirm", 71001))
                .isInstanceOf(FeedbackRejection.class);
    }
}
