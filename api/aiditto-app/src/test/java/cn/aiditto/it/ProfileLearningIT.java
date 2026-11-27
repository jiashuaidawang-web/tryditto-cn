package cn.aiditto.it;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningEngine;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal;
import cn.aiditto.domain.feedback.v4.learning.SelfDirectionReport;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.feedback.ProfileLearningStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fictional users only. This does not read production feedback or call a model. */
class ProfileLearningIT extends ApiIT {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired ProfileLearningStore learning;
    @Autowired RelationshipFeedbackStore feedback;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void releaseActors() {
        jdbc.update("DELETE FROM relationship_feedback_erasure WHERE user_id BETWEEN 82101 AND 82140");
    }

    @Test
    void proposalIsStoredBeforeItIsConfirmed() {
        ProfileLearningProposal proposal = ProfileLearningEngine.v1().hypotheses(List.of(
                new SelfDirectionReport("p1", 82101, "self.communication", "quieter", T0),
                new SelfDirectionReport("p2", 82101, "self.communication", "quieter", T0.plusSeconds(1)),
                new SelfDirectionReport("p3", 82101, "self.communication", "quieter", T0.plusSeconds(2))
        ), 82101, 0, 1, T0.plusSeconds(3)).get(0);
        learning.saveProposal(proposal);
        String status = jdbc.queryForObject(
                "SELECT status FROM profile_learning_proposal WHERE proposal_id = ?", String.class, proposal.proposalId());
        assertThat(status).isEqualTo("PENDING_CONFIRMATION");
        assertThat(profiles(82101)).isZero();
    }

    @Test
    void confirmationWritesOneProfileVersion() {
        Prepared prepared = prepare(73001, 82102, 82103);
        learning.confirm(prepared.context(), command("quiet2", 82102, 0, prepared.eventId(), true));
        assertThat(profiles(82102)).isEqualTo(1);
        String hash = jdbc.queryForObject("""
                SELECT input_hash FROM relationship_profile_version WHERE user_id = 82102 AND is_current
                """, String.class);
        assertThat(hash).hasSize(64);
        Integer claims = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_consumption
                WHERE consumer_type = 'PROFILE_LEARNING' AND processing_status = 'APPLIED' AND event_id = ?
                """, Integer.class, prepared.eventId());
        assertThat(claims).isEqualTo(1);
    }

    @Test
    void rejectionAndExpiryDoNotEnterTheProfile() {
        Prepared prepared = prepare(73002, 82104, 82105);
        ExplicitPreferenceCommand reject = new ExplicitPreferenceCommand("reject2", 82104, "self.communication", null,
                "quiet", Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.REJECT,
                0, 1, 1, true, true, List.of(prepared.eventId()), T0);
        assertThat(learning.confirm(prepared.context(), reject).snapshot()).isNull();
        learning.saveProposal(ProfileLearningEngine.v1().propose(command("old2", 82104, 0, prepared.eventId(), true))
                .withStatus(ProfileLearningProposal.Status.PENDING_CONFIRMATION, "UNCONFIRMED"));
        jdbc.update("UPDATE profile_learning_proposal SET expires_at = ? WHERE proposal_id = 'prop.old2'",
                java.sql.Timestamp.from(T0.minusSeconds(1)));
        assertThat(learning.expireDue(T0)).isEqualTo(1);
        assertThat(profiles(82104)).isZero();
    }

    @Test
    void pendingProposalIsNotReadableByMatching() {
        ProfileLearningProposal proposal = ProfileLearningEngine.v1().hypotheses(List.of(
                new SelfDirectionReport("m1", 82106, "self.communication", "quieter", T0),
                new SelfDirectionReport("m2", 82106, "self.communication", "quieter", T0.plusSeconds(1)),
                new SelfDirectionReport("m3", 82106, "self.communication", "quieter", T0.plusSeconds(2))
        ), 82106, 0, 1, T0.plusSeconds(3)).get(0);
        assertThat(ProfileLearningEngine.matchingMayRead(proposal)).isFalse();
        learning.saveProposal(proposal);
        assertThat(profiles(82106)).isZero();
    }

    @Test
    void illegalFeatureAndHardConstraintAreRejected() {
        Prepared prepared = prepare(73003, 82107, 82108);
        assertThatThrownBy(() -> learning.confirm(prepared.context(), commandFor("badf", 82107, "not.a.feature", "quiet", prepared.eventId())))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ILLEGAL_FEATURE");
        assertThatThrownBy(() -> learning.confirm(prepared.context(), commandFor("badh", 82107, "partner.age", "30", prepared.eventId())))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ILLEGAL_HARD_CONSTRAINT");
        assertThat(profiles(82107)).isZero();
    }

    @Test
    void crossUserAndMissingConsentAreRejected() {
        Prepared prepared = prepare(73004, 82109, 82110);
        ExplicitPreferenceCommand stranger = command("strng", 82110, 0, prepared.eventId(), true);
        ExplicitPreferenceCommand other = new ExplicitPreferenceCommand("strng", 82999, "self.communication", null, "quiet",
                Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM, 0, 1, 1, true, true,
                List.of(prepared.eventId()), T0);
        assertThatThrownBy(() -> learning.confirm(prepared.context(), other))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("CROSS_USER");
        assertThatThrownBy(() -> learning.confirm(prepared.context(), command("nocns", 82109, 0, prepared.eventId(), false)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_LEARNING_CONSENT");
        assertThat(stranger.userId()).isEqualTo(82110);
        assertThat(profiles(82109)).isZero();
    }

    @Test
    void retractedSourceDoesNotWrite() {
        Prepared prepared = prepare(73005, 82111, 82112);
        feedback.append(prepared.context(), person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 82111, "revoke-73005"), T0.plusSeconds(1));
        assertThat(learning.confirm(prepared.context(), command("revoked", 82111, 0, prepared.eventId(), true)).snapshot()).isNull();
        String status = jdbc.queryForObject(
                "SELECT status FROM profile_learning_proposal WHERE proposal_id = 'prop.revoked'", String.class);
        assertThat(status).isEqualTo("INVALID_EVIDENCE");
        assertThat(profiles(82111)).isZero();
    }

    @Test
    void secondConfirmDoesNotApplyTwice() {
        Prepared prepared = prepare(73006, 82113, 82114);
        ExplicitPreferenceCommand command = command("twice", 82113, 0, prepared.eventId(), true);
        learning.confirm(prepared.context(), command);
        assertThat(learning.confirm(prepared.context(), command).decision().kind().name()).isEqualTo("KEEP_CURRENT");
        assertThat(profiles(82113)).isEqualTo(1);
        Integer claims = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_consumption WHERE event_id = ?", Integer.class, prepared.eventId());
        assertThat(claims).isEqualTo(1);
    }

    @Test
    void oldVersionDoesNotOverwrite() {
        Prepared prepared = prepare(73007, 82115, 82116);
        RelationshipFeedbackEvent second = feedback.append(prepared.context(),
                person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 82115, "go-73007"), T0.plusSeconds(1));
        learning.confirm(prepared.context(), command("first", 82115, 0, prepared.eventId(), true));
        assertThat(learning.confirm(prepared.context(), command("second", 82115, 0, second.eventId(), true)).proposal().status().name())
                .isEqualTo("STALE_PROFILE");
        assertThat(profiles(82115)).isEqualTo(1);
        learning.confirm(prepared.context(), command("third", 82115, 1, second.eventId(), true));
        assertThat(profiles(82115)).isEqualTo(2);
        String pace = jdbc.queryForObject("""
                SELECT self_profile::text FROM relationship_profile_version
                WHERE user_id = 82115 AND is_current
                """, String.class);
        assertThat(pace).contains("self.communication");
    }

    @Test
    void concurrentConfirmsLeaveOneCurrentVersion() throws Exception {
        Prepared prepared = prepare(73008, 82117, 82118);
        RelationshipFeedbackEvent second = feedback.append(prepared.context(),
                person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 82117, "go-73008"), T0.plusSeconds(1));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> first = pool.submit(() -> confirmAfter(start, prepared, command("racea", 82117, 0, prepared.eventId(), true)));
        Future<?> other = pool.submit(() -> confirmAfter(start, prepared, command("raceb", 82117, 0, second.eventId(), true)));
        start.countDown();
        first.get(20, TimeUnit.SECONDS);
        other.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        Integer current = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = 82117 AND is_current
                """, Integer.class);
        assertThat(current).isEqualTo(1);
        assertThat(profiles(82117)).isEqualTo(1);
    }

    @Test
    void eraseRacesConfirm() throws Exception {
        Prepared prepared = prepare(73009, 82119, 82120);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> erase = pool.submit(() -> {
            start.await();
            feedback.eraseActor(82119, T0.plusSeconds(2));
            return null;
        });
        Future<?> confirm = pool.submit(() -> confirmAfter(start, prepared, command("erace", 82119, 0, prepared.eventId(), true)));
        start.countDown();
        erase.get(20, TimeUnit.SECONDS);
        confirm.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThatThrownBy(() -> learning.confirm(prepared.context(), command("later", 82119, 0, prepared.eventId(), true)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ERASED");
        assertThat(profiles(82119)).isBetween(0, 1);
    }

    @Test
    void confirmRollsBackWithTheConsumption() {
        Prepared prepared = prepare(73010, 82121, 82122);
        assertThatThrownBy(() -> learning.confirmThenRollback(prepared.context(),
                command("roll", 82121, 0, prepared.eventId(), true)))
                .isInstanceOf(FeedbackRejection.class);
        assertThat(profiles(82121)).isZero();
        Integer claims = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_consumption WHERE event_id = ?", Integer.class, prepared.eventId());
        Integer confirms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_learning_confirmation WHERE command_id = 'roll'", Integer.class);
        assertThat(claims).isZero();
        assertThat(confirms).isZero();
    }

    @Test
    void replayReadsTheStoredProfile() {
        Prepared prepared = prepare(73011, 82123, 82124);
        learning.confirm(prepared.context(), command("replay", 82123, 0, prepared.eventId(), true));
        String evidence = jdbc.queryForObject("""
                SELECT evidence::text FROM relationship_profile_version WHERE user_id = 82123 AND is_current
                """, String.class);
        assertThat(evidence).contains("confirm.replay").contains("quiet");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_learning_confirmation WHERE command_id = 'replay' AND retracted = FALSE",
                Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    private Object confirmAfter(CountDownLatch start, Prepared prepared, ExplicitPreferenceCommand command) {
        try {
            start.await();
            return learning.confirm(prepared.context(), command);
        } catch (FeedbackRejection rejection) {
            return rejection.code();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private int profiles(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private Prepared prepare(long matchId, long userA, long userB) {
        MatchFeedbackContext context = MatchFeedbackContext.experiment(matchId, "run-f02", LocalDate.of(2026, 11, 23),
                userA, userB, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-" + matchId, T0, null, null, null), T0);
        RelationshipFeedbackEvent accept = feedback.append(context, person(FeedbackEventType.MATCH_ACCEPTED, userA,
                "acc-" + matchId), T0);
        return new Prepared(context, accept.eventId());
    }

    private static ExplicitPreferenceCommand command(String id, long userId, int version, String eventId, boolean learning) {
        return commandFor(id, userId, "self.communication", "quiet", eventId, version, learning);
    }

    private static ExplicitPreferenceCommand commandFor(String id, long userId, String feature, String value, String eventId) {
        return commandFor(id, userId, feature, value, eventId, 0, true);
    }

    private static ExplicitPreferenceCommand commandFor(String id, long userId, String feature, String value,
                                                        String eventId, int version, boolean learning) {
        boolean preference = feature.startsWith("partner.");
        return new ExplicitPreferenceCommand(id, userId, feature, null, value,
                preference ? Importance.KINDA : Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                version, 1, 1, true, learning, List.of(eventId), T0);
    }

    private static FeedbackCommand person(FeedbackEventType type, long actor, String key) {
        return new FeedbackCommand(type, actor, FeedbackFacts.SourceType.PARTICIPANT, key, T0, null, null, null);
    }

    private record Prepared(MatchFeedbackContext context, String eventId) {}
}
