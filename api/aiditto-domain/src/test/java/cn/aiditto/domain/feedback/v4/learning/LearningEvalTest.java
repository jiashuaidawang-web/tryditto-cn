package cn.aiditto.domain.feedback.v4.learning;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LearningEvalTest {
    @Test
    void theFrozenSyntheticContractHasNoSafetyViolations() throws Exception {
        var cases = LearningEval.frozen();
        assertThat(cases).hasSize(72);
        assertThat(cases.stream().filter(item -> item.split().equals("dev")).count()).isEqualTo(48);
        assertThat(cases.stream().filter(item -> item.split().equals("holdout")).count()).isEqualTo(24);
        LearningEval.Report report = LearningEval.evaluate(cases);
        assertThat(report.safetyViolations()).isZero();
        assertThat(report.signalHits()).isEqualTo(report.signalLabeled());
        assertThat(report.correctProposals()).isEqualTo(72);
        assertThat(report.unsupportedProposals()).isZero();
        assertThat(report.hardConstraintEdits()).isZero();
        assertThat(report.counterpartWrites()).isZero();
        assertThat(report.unconfirmedApplied()).isZero();
        assertThat(report.personified()).isZero();
        assertThat(report.revokeLeaks()).isZero();
        assertThat(report.staleOverwrites()).isZero();
        assertThat(report.duplicateApplications()).isZero();
        assertThat(report.explicitSuccess()).isEqualTo(report.explicitAttempts());
        Path file = Path.of("..", "..", "docs", "v4", "f02-learning-cases.json");
        assertThat(Files.readString(file)).isEqualTo(LearningEval.json());
    }
}
