package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;
import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.AttendanceLabel;
import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.Mutual;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration.Method;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration.Result;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration.Target;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyContractTest {
    @Test
    void syntheticObservationsKeepLabelsSeparateFromScores() throws Exception {
        List<MatchingObservation> rows = StrategyDataset.observations();
        assertThat(rows).hasSize(128);
        assertThat(rows.stream().filter(StrategyDataset::development)).hasSize(96);
        assertThat(rows.stream().filter(row -> !StrategyDataset.development(row))).hasSize(32);
        Set<Long> development = new HashSet<>();
        Set<Long> holdout = new HashSet<>();
        for (MatchingObservation row : rows) {
            Set<Long> side = StrategyDataset.development(row) ? development : holdout;
            side.add(row.userA());
            side.add(row.userB());
            assertThat(row.historical(row.profileVersionA() + 5, row.profileVersionB() + 5).canonical())
                    .isEqualTo(row.canonical());
            for (var component : MatchingObservation.PredictionFeatures.class.getRecordComponents()) {
                assertThat(component.getName()).doesNotContain("response").doesNotContain("attendance")
                        .doesNotContain("continue");
            }
        }
        assertThat(development).doesNotContainAnyElementsOf(holdout);
        assertThat(rows.stream().filter(row -> row.scoreAB() == 95).map(RelationshipLabels::of))
                .allMatch(labels -> labels.y1() == Mutual.BOTH_DECLINED);
        assertThat(rows.stream().filter(row -> row.scoreAB() == 12).map(RelationshipLabels::of))
                .allMatch(labels -> labels.y1() == Mutual.BOTH_ACCEPTED && labels.y3Learnable());
        assertThat(rows.stream().filter(row -> row.responseA() == Response.UNKNOWN && row.responseB() == Response.UNKNOWN
                        && !row.viewed() && row.observedAt().isBefore(row.responseDeadline())))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y1()).isEqualTo(Mutual.IMMATURE));
        assertThat(rows.stream().filter(row -> row.responseA() == Response.UNKNOWN && !row.viewed()
                        && row.observedAt().isAfter(row.responseDeadline())))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y1()).isEqualTo(Mutual.NO_RESPONSE));
        assertThat(rows.stream().filter(row -> row.responseA() == Response.EXPIRED))
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y1()).isEqualTo(Mutual.EXPIRED));
        assertThat(rows.stream().filter(row -> row.attendance() == Attendance.CONTESTED))
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y2()).isEqualTo(AttendanceLabel.CONTESTED));
        assertThat(rows.stream().filter(row -> row.feedbackAuthorization() == Access.REVOKED))
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y1()).isEqualTo(Mutual.WITHHELD));
        assertThat(rows.stream().filter(row -> row.responseB() == Response.UNKNOWN && row.responseA() == Response.ACCEPTED))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(RelationshipLabels.of(row).y1()).isEqualTo(Mutual.UNKNOWN));
        assertThat(rows.stream().filter(row -> row.sourceEventIds().size() > 1)).isNotEmpty()
                .allMatch(row -> row.sourceEventIds().stream().distinct().count() == row.sourceEventIds().size());
        assertThat(rows.stream().filter(row -> row.userA() == 91001).count()).isGreaterThan(1);
        assertThatThrownBy(() -> StrategyDataset.rejectDuplicate(rows, rows.get(0)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_MATCH");
        StrategyEval.Report report = StrategyEval.report();
        assertThat(report.leakage()).isZero();
        assertThat(report.withheldUsed()).isZero();
        assertThat(report.unknownAsFailure()).isZero();
        assertThat(report.duplicateMatches()).isZero();
        assertThat(report.realCalibration()).isEqualTo("NOT_RUN");
        assertThat(report.realLift()).isEqualTo("NOT_IDENTIFIED");
        assertThat(report.experiment()).isEqualTo("NOT_STARTED");
        assertThat(StrategyEval.limitations()).contains(
                "feedback exists only for published matches",
                "a replay has no counterfactual accept, meeting, or continue result");
        Path observations = Path.of("..", "..", "docs", "v4", "f03-observations.json");
        assertThat(Files.readString(observations)).isEqualTo(StrategyDataset.json());
    }

    @Test
    void funnelCountsEachStageOnTheWholePublishedSet() {
        List<MatchingObservation> rows = StrategyDataset.observations();
        List<GroupFunnel.Stage> stages = GroupFunnel.matchStages(rows);
        assertThat(stages).extracting(GroupFunnel.Stage::name).containsExactly(
                "published", "viewed", "responded", "bothResponded", "bothAccepted", "dateArranged",
                "oneReportedAttendance", "bothConfirmedAttendance", "oneContinue", "bothContinue");
        assertThat(stages).allSatisfy(stage -> {
            assertThat(stage.denominator()).isEqualTo(rows.size());
            assertThat(stage.authorizationCoverage()).isBetween(0.0, 1.0);
        });
        MatchingObservation arrangedWithoutAView = sample("odd", 88001, 10, Response.UNKNOWN, false, true);
        List<GroupFunnel.Stage> odd = GroupFunnel.matchStages(List.of(arrangedWithoutAView));
        assertThat(odd.stream().filter(stage -> stage.name().equals("viewed")).findFirst().orElseThrow().matches()).isZero();
        assertThat(odd.stream().filter(stage -> stage.name().equals("dateArranged")).findFirst().orElseThrow().matches()).isEqualTo(1);
        assertThat(GroupFunnel.matchStages(List.of())).allSatisfy(stage -> assertThat(stage.matches()).isZero());
    }

    @Test
    void calibrationRefusesEmptyAndSingleClassSamples() {
        assertThat(ScoreCalibration.run(List.of())).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo("INSUFFICIENT");
            assertThat(result.brier()).isNull();
        });
        List<MatchingObservation> accepted = StrategyDataset.observations().stream()
                .filter(row -> row.responseA() == Response.ACCEPTED && row.responseB() == Response.ACCEPTED
                        && row.feedbackAuthorization() == Access.OPEN)
                .toList();
        assertThat(ScoreCalibration.run(accepted)).allSatisfy(result -> assertThat(result.status()).isEqualTo("INSUFFICIENT"));
        List<Result> mixed = ScoreCalibration.run(List.of(
                sample("low", 88002, 0, Response.DECLINED, true, false),
                sample("high", 88003, 100, Response.ACCEPTED, true, false)));
        assertThat(mixed).filteredOn(result -> result.target() == Target.SCORE_AB_ACCEPT_A && result.method() != Method.ISOTONIC)
                .allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo("OK");
                    assertThat(result.brier()).isNotNull().isBetween(0.0, 1.0);
                    assertThat(result.logLoss()).isFinite();
                    assertThat(result.calibrationError()).isBetween(0.0, 1.0);
                });
        assertThat(mixed).filteredOn(result -> result.method() == Method.ISOTONIC && result.sample() >= 2)
                .allSatisfy(result -> assertThat(result.status()).isEqualTo("NOT_RUN"));
        assertThat(mixed).filteredOn(result -> result.sample() == 0)
                .allSatisfy(result -> assertThat(result.status()).isEqualTo("INSUFFICIENT"));
        assertThat(ScoreCalibration.REAL_QUALITY).isEqualTo("NOT_RUN");
        List<MatchingObservation> fit = List.of(
                sample("f1", 88101, 10, Response.DECLINED, true, false),
                sample("f2", 88102, 10, Response.DECLINED, true, false),
                sample("f3", 88103, 90, Response.ACCEPTED, true, false),
                sample("f4", 88104, 90, Response.ACCEPTED, true, false));
        List<MatchingObservation> eval = List.of(
                sample("e1", 88105, 10, Response.ACCEPTED, true, false),
                sample("e2", 88106, 90, Response.DECLINED, true, false));
        Result heldOut = ScoreCalibration.evaluate(fit, eval).stream()
                .filter(result -> result.target() == Target.SCORE_AB_ACCEPT_A && result.method() == Method.BINNING)
                .findFirst().orElseThrow();
        assertThat(heldOut.brier()).isGreaterThan(0.9);
        List<MatchingObservation> rows = StrategyDataset.observations();
        List<Result> split = ScoreCalibration.evaluate(
                rows.stream().filter(StrategyDataset::development).toList(),
                rows.stream().filter(row -> !StrategyDataset.development(row)).toList());
        assertThat(split).filteredOn(result -> result.target() == Target.ATTENDANCE)
                .allSatisfy(result -> assertThat(result.status()).isEqualTo("INSUFFICIENT"));
        assertThat(split).filteredOn(result -> result.target() == Target.SCORE_AB_ACCEPT_A && result.method() == Method.BINNING)
                .allSatisfy(result -> assertThat(result.status()).isEqualTo("OK"));
    }

    @Test
    void strategyComparisonDoesNotInventUnpublishedOutcomes() {
        List<StrategyComparison.Row> rows = StrategyComparison.compare(StrategyDataset.observations());
        assertThat(rows).extracting(StrategyComparison.Row::strategy)
                .containsExactly("E+G1", "E+G2", "E+G3", "E+G4", "F", "M02-B5");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.published()).isPositive();
            assertThat(row.counterfactual()).isFalse();
            assertThat(row.realLift()).isEqualTo("NOT_IDENTIFIED");
            assertThat(row.bothAccepted() + row.bothDeclined() + row.notBinary()).isEqualTo(row.published());
        });
        List<StrategyComparison.Row> withoutShortlist = StrategyComparison.compare(StrategyDataset.observations().stream()
                .filter(row -> !row.candidateStrategyVersion().equals("F")).toList());
        assertThat(withoutShortlist).filteredOn(row -> row.strategy().equals("F"))
                .allSatisfy(row -> assertThat(row.published()).isZero());
        assertThat(Objective.SELECTED).isEqualTo(Objective.G2_CARDINALITY_THEN_WEIGHT);
    }

    @Test
    void proposalStaysInactive() {
        StrategyLearningProposal proposal = StrategyEval.proposal();
        assertThat(proposal.status()).isEqualTo(StrategyLearningProposal.Status.EVALUATED);
        assertThat(proposal.proposedChange()).isEqualTo("no weight change");
        assertThat(proposal.basePolicyVersion()).isEqualTo(proposal.candidatePolicyVersion());
        assertThatThrownBy(proposal::activate)
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("STRATEGY_NOT_ACTIVATED");
        assertThat(StrategyLearningProposal.from(StrategyDataset.VERSION, 1, true, Instant.parse("2026-11-28T00:00:00Z")).status())
                .isEqualTo(StrategyLearningProposal.Status.INSUFFICIENT_DATA);
        assertThat(StrategyLearningProposal.from(StrategyDataset.VERSION, 40, false, Instant.parse("2026-11-28T00:00:00Z")).status())
                .isEqualTo(StrategyLearningProposal.Status.REJECTED);
        StrategyLearningProposal held = new StrategyLearningProposal("held", "global-objective-g2-1", "global-objective-g3-1",
                StrategyDataset.VERSION, "synthetic-published-matches", "Y1", "none", "none", "none",
                "no counterfactual", "pending", "unknown", StrategyLearningProposal.Status.APPROVAL_REQUIRED,
                Instant.parse("2026-11-28T00:00:00Z"));
        assertThatThrownBy(held::activate).isInstanceOf(FeedbackRejection.class);
    }

    @Test
    void labCasesStayReplayable() throws Exception {
        List<StrategyLab.Case> cases = StrategyLab.cases();
        assertThat(cases).hasSize(10);
        assertThat(RelationshipLabels.of(cases.get(3).observation()).y1()).isEqualTo(Mutual.IMMATURE);
        assertThat(RelationshipLabels.of(cases.get(4).observation()).y1()).isEqualTo(Mutual.NO_RESPONSE);
        assertThat(RelationshipLabels.of(cases.get(5).observation()).y1()).isEqualTo(Mutual.EXPIRED);
        assertThat(RelationshipLabels.of(cases.get(6).observation()).y2()).isEqualTo(AttendanceLabel.CONTESTED);
        assertThat(RelationshipLabels.of(cases.get(7).observation()).y1()).isEqualTo(Mutual.WITHHELD);
        assertThat(cases.get(7).observation().historical(9, 9).profileVersionA()).isEqualTo(5);
        assertThat(cases).anySatisfy(item -> assertThat(item.reason()).contains("窗口未到"));
        Path file = Path.of("..", "..", "docs", "v4", "f03-lab-cases.json");
        assertThat(Files.readString(file)).isEqualTo(StrategyLab.json());
    }

    private static MatchingObservation sample(String id, long match, int score, Response response, boolean viewed,
                                              boolean arranged) {
        Instant published = Instant.parse("2026-11-01T00:00:00Z");
        return new MatchingObservation(id, match, "boundary", LocalDate.of(2026, 11, 1),
                91001, 91040 + match, 2, 3, 1, 1, "E+G2", "m02-b5", "global-objective-g2-1", "B5",
                score, score, 1, 1, score, published, published.plusSeconds(86400), published.plusSeconds(172800),
                published.plusSeconds(259200), response, response, viewed, arranged, Attendance.NOT_APPLICABLE,
                Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, false, List.of("e-" + match),
                published.plusSeconds(864000));
    }
}
