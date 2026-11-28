package cn.aiditto.it;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.strategy.MatchScoreSnapshot;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation;
import cn.aiditto.domain.feedback.v4.strategy.ObservationWindow;
import cn.aiditto.domain.feedback.v4.strategy.StrategyLearningProposal;
import cn.aiditto.feedback.GroupEvaluationStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic matches only. This does not read a production database or enable a strategy. */
class StrategyEvaluationIT extends ApiIT {
    private static final Instant T0 = Instant.parse("2026-11-28T00:00:00Z");

    @Autowired GroupEvaluationStore groups;
    @Autowired RelationshipFeedbackStore feedback;
    @Autowired JdbcTemplate jdbc;

    @Test
    void publishedSnapshotStaysBoundAfterALaterProfile() {
        Prepared prepared = prepare(74001, 83101, 83102, true, true);
        groups.record(prepared.context(), observation("obs-74001", 74001, 83101, 83102, 4), "f03-it-snapshot",
                prepared.eventId(), "group-1", T0);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM matching_observation WHERE observation_id = 'obs-74001'", String.class);
        assertThat(payload).contains("pa=4").doesNotContain("pa=9");
        assertThatThrownBy(() -> groups.record(prepared.context(), observation("obs-later", 74001, 83101, 83102, 9),
                "f03-it-snapshot", prepared.eventId(), "group-1", T0))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("SNAPSHOT");
        assertThat(groups.legalPayloads("f03-it-snapshot")).hasSize(1);
    }

    @Test
    void missingLearningConsentStillRecordsTheGroup() {
        Prepared prepared = prepare(74002, 83103, 83104, true, false);
        groups.record(prepared.context(), observation("obs-74002", 74002, 83103, 83104, 4), "f03-it-consent",
                prepared.eventId(), "group-1", T0);
        assertThatThrownBy(() -> feedback.claim(prepared.context(), prepared.eventId(),
                FeedbackConsumption.Consumer.PROFILE_LEARNING, "learn-1", T0, "proposal-74002"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_LEARNING_CONSENT");
        assertThat(groups.legalPayloads("f03-it-consent")).containsExactly(
                jdbc.queryForObject("SELECT payload FROM matching_observation WHERE observation_id = 'obs-74002'", String.class));
    }

    @Test
    void profileLearningDoesNotBlockGroupEvaluation() {
        Prepared prepared = prepare(74003, 83105, 83106, true, true);
        groups.record(prepared.context(), observation("obs-74003", 74003, 83105, 83106, 4), "f03-it-both",
                prepared.eventId(), "group-1", T0);
        feedback.claim(prepared.context(), prepared.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-1", T0, "proposal-74003");
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_consumption
                WHERE event_id = ? AND processing_status = 'APPLIED'
                """, Integer.class, prepared.eventId());
        assertThat(applied).isEqualTo(2);
    }

    @Test
    void revokedSourceWithdrawsTheObservation() {
        Prepared prepared = prepare(74004, 83107, 83108, true, true);
        groups.record(prepared.context(), observation("obs-74004", 74004, 83107, 83108, 4), "f03-it-revoke",
                prepared.eventId(), "group-1", T0);
        groups.withdraw("obs-74004", prepared.eventId(), T0.plusSeconds(1));
        assertThat(groups.legalPayloads("f03-it-revoke")).isEmpty();
        String status = jdbc.queryForObject("""
                SELECT processing_status FROM relationship_feedback_consumption
                WHERE event_id = ? AND consumer_type = 'GROUP_EVALUATION'
                """, String.class, prepared.eventId());
        assertThat(status).isEqualTo("REVOKED");
    }

    @Test
    void secondClaimDoesNotRestoreAWithdrawnObservation() {
        Prepared prepared = prepare(74005, 83109, 83110, true, true);
        groups.record(prepared.context(), observation("obs-74005", 74005, 83109, 83110, 4), "f03-it-second",
                prepared.eventId(), "group-1", T0);
        groups.withdraw("obs-74005", prepared.eventId(), T0.plusSeconds(1));
        assertThatThrownBy(() -> feedback.claim(prepared.context(), prepared.eventId(),
                FeedbackConsumption.Consumer.GROUP_EVALUATION, "group-1", T0.plusSeconds(2), "obs-74005"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_CONSUMPTION");
        Boolean withdrawn = jdbc.queryForObject(
                "SELECT withdrawn FROM matching_observation WHERE observation_id = 'obs-74005'", Boolean.class);
        assertThat(withdrawn).isTrue();
        assertThat(groups.legalPayloads("f03-it-second")).isEmpty();
    }

    @Test
    void erasureDropsTheObservationFromTheLegalSet() {
        Prepared prepared = prepare(74006, 83111, 83112, true, true);
        groups.record(prepared.context(), observation("obs-74006", 74006, 83111, 83112, 4), "f03-it-erase",
                prepared.eventId(), "group-1", T0);
        feedback.eraseActor(83111, T0.plusSeconds(3));
        assertThat(groups.legalPayloads("f03-it-erase")).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT payload FROM matching_observation WHERE observation_id = 'obs-74006'", String.class))
                .isEqualTo("REDACTED");
        assertThatThrownBy(() -> feedback.claim(prepared.context(), prepared.eventId(),
                FeedbackConsumption.Consumer.GROUP_EVALUATION, "group-1", T0.plusSeconds(4), "obs-74006"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("UNKNOWN_EVENT");
    }

    @Test
    void proposalCannotBeActivated() {
        groups.saveProposal(StrategyLearningProposal.from("f03-it-proposal", 40, true, T0));
        assertThatThrownBy(() -> groups.activate("strategy-proposal-1"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("STRATEGY_NOT_ACTIVATED");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE strategy_learning_proposal SET activated = TRUE WHERE proposal_id = 'strategy-proposal-1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Boolean activated = jdbc.queryForObject(
                "SELECT activated FROM strategy_learning_proposal WHERE proposal_id = 'strategy-proposal-1'", Boolean.class);
        assertThat(activated).isFalse();
    }

    @Test
    void oneAcceptDoesNotCreateMutualLabels() {
        Prepared prepared = prepare(74101, 83201, 83202, true, true);
        MatchingObservation row = build(prepared, "obs-74101", "f03-it-one", eventIds(74101));
        assertThat(row.responseA()).isEqualTo(MatchingObservation.Response.ACCEPTED);
        assertThat(row.responseB()).isEqualTo(MatchingObservation.Response.UNKNOWN);
        assertThat(row.attendance()).isNotEqualTo(MatchingObservation.Attendance.BOTH_ATTENDED);
        assertThat(row.continueA()).isNotEqualTo(MatchingObservation.Continue.EXPRESSED);
        assertThat(row.continueB()).isNotEqualTo(MatchingObservation.Continue.EXPRESSED);
        String payload = payload("obs-74101");
        assertThat(payload).contains("ra=ACCEPTED").contains("rb=UNKNOWN").doesNotContain("att=BOTH_ATTENDED");
        Integer sources = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_observation_source WHERE observation_id = 'obs-74101'", Integer.class);
        assertThat(sources).isEqualTo(2);
    }

    @Test
    void correctedOrUnknownSourceIsRefused() {
        Prepared unknown = prepare(74102, 83203, 83204, true, true);
        List<String> wrong = new ArrayList<>(eventIds(74102));
        wrong.set(0, "00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> build(unknown, "obs-74102", "f03-it-source", wrong))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("SOURCE_MISMATCH");
        assertThat(countObservations(74102)).isZero();

        Prepared corrected = prepare(74109, 83217, 83218, true, true);
        feedback.append(corrected.context(), new FeedbackCommand(FeedbackEventType.FEEDBACK_CORRECTED, 83217,
                FeedbackFacts.SourceType.PARTICIPANT, "fix-74109", T0, corrected.eventId(), null, null), T0.plusSeconds(2));
        assertThatThrownBy(() -> build(corrected, "obs-74109-old", "f03-it-source", List.of(corrected.eventId())))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("SOURCE_MISMATCH");
        MatchingObservation replaced = build(corrected, "obs-74109", "f03-it-source", effectiveIds(74109));
        assertThat(replaced.responseA()).isEqualTo(MatchingObservation.Response.UNKNOWN);
        assertThat(payload("obs-74109")).doesNotContain("ra=ACCEPTED");

        Prepared retracted = prepare(74103, 83205, 83206, true, true);
        feedback.append(retracted.context(), new FeedbackCommand(FeedbackEventType.FEEDBACK_RETRACTED, 83205,
                FeedbackFacts.SourceType.PARTICIPANT, "undo-74103", T0, retracted.eventId(), null, null), T0.plusSeconds(2));
        assertThatThrownBy(() -> build(retracted, "obs-74103", "f03-it-source", eventIds(74103)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("RETRACTED_SOURCE");
        assertThat(countObservations(74103)).isZero();
    }

    @Test
    void oneConsentDoesNotAuthorizeBoth() {
        MatchFeedbackContext context = MatchFeedbackContext.experiment(74104, "run-f03", LocalDate.of(2026, 11, 28),
                83207, 83208, true, 4, 4, "m02-b5", "global-objective-g2-1", "E+G2", true, false);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-74104", T0, null, null, null), T0);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, 83207,
                FeedbackFacts.SourceType.PARTICIPANT, "acc-74104", T0, null, null, null), T0);
        assertThatThrownBy(() -> groups.recordBuilt(context, "f03-it-half", "group-1", scores(), window(),
                "obs-74104", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), eventIds(74104)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_FEEDBACK_CONSENT");
        assertThat(countObservations(74104)).isZero();
    }

    @Test
    void withdrawLeavesTheOtherObservation() {
        Prepared first = prepare(74105, 83209, 83210, true, true);
        Prepared second = prepare(74106, 83211, 83212, true, true);
        build(first, "obs-74105", "f03-it-withdraw", eventIds(74105));
        build(second, "obs-74106", "f03-it-withdraw", eventIds(74106));
        groups.withdrawLinked("obs-74105", T0.plusSeconds(30 * 86400L));
        assertThat(groups.legalPayloads("f03-it-withdraw")).hasSize(1);
        assertThat(payload("obs-74106")).contains("obs-74106");
        Boolean withdrawn = jdbc.queryForObject(
                "SELECT withdrawn FROM matching_observation WHERE observation_id = 'obs-74105'", Boolean.class);
        assertThat(withdrawn).isTrue();
        assertThatThrownBy(() -> feedback.claim(first.context(), first.eventId(),
                FeedbackConsumption.Consumer.GROUP_EVALUATION, "group-1", T0.plusSeconds(31 * 86400L), "obs-74105"))
                .isInstanceOf(FeedbackRejection.class);
        assertThatThrownBy(() -> groups.withdrawLinked("obs-74105", T0.plusSeconds(32 * 86400L)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("OBSERVATION");
        assertThatThrownBy(() -> build(first, "obs-74105-again", "f03-it-withdraw", eventIds(74105)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_MATCH");
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn FROM matching_observation WHERE observation_id = 'obs-74105'", Boolean.class)).isTrue();
        String secondStatus = jdbc.queryForObject("""
                SELECT processing_status FROM relationship_feedback_consumption
                WHERE event_id = ? AND consumer_type = 'GROUP_EVALUATION'
                """, String.class, second.eventId());
        assertThat(secondStatus).isEqualTo("APPLIED");
    }

    @Test
    void replayDoesNotDuplicateOrRestore() {
        Prepared prepared = prepare(74107, 83213, 83214, true, true);
        build(prepared, "obs-74107", "f03-it-replay", eventIds(74107));
        Integer sources = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_observation_source WHERE observation_id = 'obs-74107'", Integer.class);
        assertThatThrownBy(() -> build(prepared, "obs-74107-replay", "f03-it-replay", eventIds(74107)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_MATCH");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_observation_source WHERE observation_id = 'obs-74107'", Integer.class))
                .isEqualTo(sources);
        assertThat(countObservations(74107)).isEqualTo(1);
    }

    @Test
    void concurrentBuildsLeaveOneObservation() throws Exception {
        Prepared prepared = prepare(74108, 83215, 83216, true, true);
        List<String> sources = eventIds(74108);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> left = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                groups.recordBuilt(prepared.context(), "f03-it-race", "group-1", scores(), window(),
                        "obs-74108", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), sources);
                return null;
            });
            Future<?> right = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                groups.recordBuilt(prepared.context(), "f03-it-race", "group-1", scores(), window(),
                        "obs-74108", T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), sources);
                return null;
            });
            start.countDown();
            int duplicate = 0;
            for (Future<?> future : List.of(left, right)) {
                try {
                    future.get(20, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    assertThat(cause).isInstanceOf(FeedbackRejection.class);
                    assertThat(((FeedbackRejection) cause).code()).isEqualTo("DUPLICATE_MATCH");
                    duplicate++;
                }
            }
            assertThat(duplicate).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(countObservations(74108)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_observation_source WHERE observation_id = 'obs-74108'", Integer.class))
                .isEqualTo(sources.size());
    }

    @Test
    void datasetChangeInvalidatesProposalAndCalibration() {
        groups.saveProposal(proposal("strategy-it-invalidate", "f03-it-invalidate"));
        groups.saveProposal(proposal("strategy-it-keep", "f03-it-keep"));
        groups.saveCalibration("cal-it-invalidate", "f03-it-invalidate", T0);
        assertThat(groups.proposalValid("strategy-it-invalidate")).isTrue();
        assertThat(groups.invalidateDataset("f03-it-invalidate")).isEqualTo(1);
        assertThat(groups.proposalValid("strategy-it-invalidate")).isFalse();
        assertThat(groups.proposalValid("strategy-it-keep")).isTrue();
        Boolean calibration = jdbc.queryForObject(
                "SELECT valid FROM group_calibration_run WHERE run_id = 'cal-it-invalidate'", Boolean.class);
        assertThat(calibration).isFalse();
        assertThat(groups.invalidateDataset("f03-it-invalidate")).isZero();
        assertThat(groups.proposalValid("strategy-it-invalidate")).isFalse();
    }

    private Prepared prepare(long matchId, long userA, long userB, boolean feedbackConsent, boolean learningConsent) {
        MatchFeedbackContext context = MatchFeedbackContext.experiment(matchId, "run-f03", LocalDate.of(2026, 11, 28),
                userA, userB, true, 4, 4, "m02-b5", "global-objective-g2-1", "E+G2",
                feedbackConsent, feedbackConsent, learningConsent, learningConsent);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-" + matchId, T0, null, null, null), T0);
        RelationshipFeedbackEvent accept = feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED,
                userA, FeedbackFacts.SourceType.PARTICIPANT, "acc-" + matchId, T0, null, null, null), T0);
        return new Prepared(context, accept.eventId());
    }

    private static MatchingObservation observation(String id, long matchId, long userA, long userB, int profileA) {
        return new MatchingObservation(id, matchId, "run-f03", LocalDate.of(2026, 11, 28),
                userA, userB, profileA, 4, 1, 1, "E+G2", "m02-b5", "global-objective-g2-1", "B5",
                40, 55, 0.6, 0.7, 40, T0, T0.plusSeconds(7 * 86400L), T0.plusSeconds(14 * 86400L),
                T0.plusSeconds(21 * 86400L), MatchingObservation.Response.ACCEPTED, MatchingObservation.Response.ACCEPTED,
                true, true, MatchingObservation.Attendance.BOTH_ATTENDED, MatchingObservation.Continue.EXPRESSED,
                MatchingObservation.Continue.EXPRESSED, MatchingObservation.Access.OPEN, false, List.of("src-" + matchId),
                T0.plusSeconds(20 * 86400L));
    }

    private MatchingObservation build(Prepared prepared, String observationId, String dataset, List<String> sources) {
        return groups.recordBuilt(prepared.context(), dataset, "group-1", scores(), window(), observationId,
                T0.plusSeconds(10 * 86400L), T0.plusSeconds(20 * 86400L), sources);
    }

    private static MatchScoreSnapshot scores() {
        return new MatchScoreSnapshot(40, 55, 0.6, 0.7, 40, "B5");
    }

    private static ObservationWindow window() {
        return new ObservationWindow(T0.plusSeconds(7 * 86400L), T0.plusSeconds(14 * 86400L), T0.plusSeconds(21 * 86400L));
    }

    private List<String> eventIds(long matchId) {
        return jdbc.queryForList("""
                SELECT event_id FROM relationship_feedback_event
                WHERE match_id = ? ORDER BY event_seq
                """, String.class, matchId);
    }

    private List<String> effectiveIds(long matchId) {
        List<String> superseded = jdbc.queryForList("""
                SELECT supersedes_event_id FROM relationship_feedback_event
                WHERE match_id = ? AND supersedes_event_id IS NOT NULL
                """, String.class, matchId);
        return eventIds(matchId).stream().filter(id -> !superseded.contains(id)).toList();
    }

    private int countObservations(long matchId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_observation WHERE match_id = ?", Integer.class, matchId);
        return count == null ? 0 : count;
    }

    private String payload(String observationId) {
        return jdbc.queryForObject(
                "SELECT payload FROM matching_observation WHERE observation_id = ?", String.class, observationId);
    }

    private static StrategyLearningProposal proposal(String proposalId, String datasetVersion) {
        return new StrategyLearningProposal(proposalId, "global-objective-g2-1", "global-objective-g2-1",
                datasetVersion, "synthetic-published-matches", "Y1", "no weight change", "not estimated",
                "synthetic only", "no counterfactual outcomes", "hard constraints unchanged",
                "not measured on real users", StrategyLearningProposal.Status.EVALUATED, T0);
    }

    private record Prepared(MatchFeedbackContext context, String eventId) {}
}
