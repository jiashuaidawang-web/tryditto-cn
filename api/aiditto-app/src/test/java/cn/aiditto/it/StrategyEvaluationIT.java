package cn.aiditto.it;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation;
import cn.aiditto.domain.feedback.v4.strategy.StrategyLearningProposal;
import cn.aiditto.feedback.GroupEvaluationStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    private record Prepared(MatchFeedbackContext context, String eventId) {}
}
