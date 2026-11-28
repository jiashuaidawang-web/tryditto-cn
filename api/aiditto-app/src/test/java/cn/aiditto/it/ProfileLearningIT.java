package cn.aiditto.it;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.LearningActor;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningEngine;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal;
import cn.aiditto.domain.feedback.v4.learning.SelfDirectionReport;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.feedback.ProfileLearningStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import cn.aiditto.relationship.ExtractionTaskStore;
import cn.aiditto.relationship.ProfileInputLoader;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @DynamicPropertySource
    static void enableProfileProcessing(DynamicPropertyRegistry registry) {
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
    }

    @Autowired ProfileLearningStore learning;
    @Autowired RelationshipFeedbackStore feedback;
    @Autowired RelationshipProfileService profiles;
    @Autowired ProfileInputLoader inputs;
    @Autowired ExtractionTaskStore tasks;
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

    @Test
    void questionnaireEvidenceSurvivesOnePreferenceUpdate() {
        Prepared prepared = prepare(73012, 82125, 82126);
        seedQuestionnaire(82125);
        profiles.save(82125, 1, 1, profiles.projectAuthoritative(82125));
        learning.confirm(prepared.context(), LearningActor.synthetic(82125),
                preference(82125, "soft1", prepared.eventId(), 1));
        String evidence = currentEvidence(82125);
        assertThat(evidence).contains("lifestyle.hobbies", "reading", "relationship.goal", "serious");
        assertThat(evidence).contains("partner.education", "educationImportance");
        assertThat(evidence).contains("confirm.soft1", "quiet");
        assertThat(sourceVersion(82125)).isEqualTo(1);
        assertThat(currentSnapshotVersion(82125)).isEqualTo(2);
    }

    @Test
    void reprojectionKeepsTheConfirmedPreference() {
        Prepared prepared = prepare(73013, 82127, 82128);
        seedQuestionnaire(82127);
        profiles.save(82127, 1, 1, profiles.projectAuthoritative(82127));
        learning.confirm(prepared.context(), LearningActor.synthetic(82127),
                preference(82127, "soft2", prepared.eventId(), 1));
        profiles.grantModelExport(82127);
        long exportEpoch = jdbc.queryForObject(
                "SELECT model_export_epoch FROM relationship_profile_lifecycle WHERE user_id = 82127", Long.class);
        var context = ProfileContextBuilder.build(inputs.load(82127, 1, 1, true, true, exportEpoch));
        var claim = tasks.tryStart(82127, context.inputHash(), ProfileExtractionPrompts.VERSION, 1, exportEpoch);
        assertThat(claim.attempt()).isEqualTo(ExtractionTaskStore.Attempt.STARTED);
        profiles.commitProvisional(82127, 1, exportEpoch, context.inputHash(), claim.fence(), List.of());
        assertThat(currentEvidence(82127)).contains("confirm.soft2", "lifestyle.hobbies", "reading");
        jdbc.update("UPDATE profile SET version = 2 WHERE user_id = 82127");
        replaceAnswers(82127, questionnaire(List.of("reading", "cooking")));
        RelationshipProfileSnapshot again = profiles.projectAuthoritative(82127);
        profiles.save(82127, 2, 1, again);
        String evidence = currentEvidence(82127);
        assertThat(evidence).contains("confirm.soft2", "quiet", "cooking", "reading");
        assertThat(sourceVersion(82127)).isEqualTo(2);
    }

    @Test
    void revokedGenerationDoesNotUseTheCommandFlags() {
        Prepared prepared = prepare(73014, 82129, 82130);
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET generation_epoch = generation_epoch + 1
                WHERE user_id = 82129
                """);
        assertThat(learning.confirm(prepared.context(), LearningActor.synthetic(82129),
                command("staleg", 82129, 0, prepared.eventId(), true)).proposal().status().name())
                .isEqualTo("STALE_PROFILE");
        assertThat(profiles(82129)).isZero();
        profiles.revokeProcessing(82129);
        assertThatThrownBy(() -> learning.confirm(prepared.context(), LearningActor.synthetic(82129),
                command("revokeg", 82129, 0, prepared.eventId(), true)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_FEEDBACK_CONSENT");
        assertThat(profiles(82129)).isZero();
    }

    @Test
    void concurrentProfileWritesLeaveOneCurrentSnapshot() throws Exception {
        Prepared prepared = prepare(73015, 82131, 82132);
        seedQuestionnaire(82131);
        profiles.save(82131, 1, 1, profiles.projectAuthoritative(82131));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> projected = pool.submit(() -> {
            try {
                start.await();
                replaceAnswers(82131, questionnaire(List.of("reading", "music")));
                jdbc.update("UPDATE profile SET version = 2 WHERE user_id = 82131");
                profiles.save(82131, 2, 1, profiles.projectAuthoritative(82131));
                return null;
            } catch (DomainException rejected) {
                return rejected.code();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        });
        Future<?> confirmed = pool.submit(() -> confirmAfter(start, prepared, preference(82131, "racep", prepared.eventId(), 1)));
        start.countDown();
        projected.get(20, TimeUnit.SECONDS);
        confirmed.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        Integer current = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = 82131 AND is_current
                """, Integer.class);
        assertThat(current).isEqualTo(1);
        String evidence = currentEvidence(82131);
        assertThat(evidence).contains("lifestyle.hobbies");
        Integer confirms = jdbc.queryForObject("""
                SELECT COUNT(*) FROM profile_learning_confirmation
                WHERE command_id = 'racep' AND retracted = FALSE
                """, Integer.class);
        if (confirms != null && confirms == 1) assertThat(evidence).contains("confirm.racep");
    }

    @Test
    void storedProposalCannotBeExtendedOrRebound() {
        Prepared prepared = prepare(73016, 82133, 82134);
        ExplicitPreferenceCommand original = command("bound1", 82133, 0, prepared.eventId(), true);
        learning.saveProposal(ProfileLearningEngine.v1().propose(original));
        jdbc.update("UPDATE profile_learning_proposal SET expires_at = ? WHERE proposal_id = 'prop.bound1'",
                Timestamp.from(T0.plus(Duration.ofDays(1))));
        ExplicitPreferenceCommand late = new ExplicitPreferenceCommand("bound1", 82133, "self.communication", null, "quiet",
                Importance.NONE, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM, 0, 1, 1, true, true,
                List.of(prepared.eventId()), T0.plus(Duration.ofDays(10)));
        assertThat(learning.confirm(prepared.context(), LearningActor.synthetic(82133), late).proposal().status().name())
                .isEqualTo("EXPIRED");
        assertThat(profiles(82133)).isZero();
        learning.confirm(prepared.context(), LearningActor.synthetic(82133),
                command("bound2", 82133, 0, prepared.eventId(), true));
        ExplicitPreferenceCommand changed = commandFor("bound2", 82133, "self.communication", "direct", prepared.eventId(), 0, true);
        assertThatThrownBy(() -> learning.confirm(prepared.context(), LearningActor.synthetic(82133), changed))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(currentEvidence(82133)).contains("quiet").doesNotContain("\"direct\"");
        assertThatThrownBy(() -> learning.confirm(prepared.context(), LearningActor.synthetic(82134),
                command("bound3", 82133, 0, prepared.eventId(), true)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("CROSS_USER");
    }

    @Test
    void retractingTheSourceRemovesDerivedEvidence() {
        Prepared prepared = prepare(73017, 82135, 82136);
        seedQuestionnaire(82135);
        profiles.save(82135, 1, 1, profiles.projectAuthoritative(82135));
        RelationshipFeedbackEvent attend = feedback.append(prepared.context(),
                person(FeedbackEventType.DATE_REPORTED_ATTENDED, 82135, "met-73017"), T0.plusSeconds(1));
        RelationshipFeedbackEvent follow = feedback.append(prepared.context(),
                person(FeedbackEventType.CONTINUE_INTEREST_EXPRESSED, 82135, "go-73017"), T0.plusSeconds(2));
        learning.confirm(prepared.context(), LearningActor.synthetic(82135), new ExplicitPreferenceCommand(
                "pace1", 82135, "self.pace", null, "slow", Importance.NONE, Operator.EQ,
                ExplicitPreferenceCommand.Action.CONFIRM, 1, 1, 1, true, true, List.of(), T0));
        int version = currentSnapshotVersion(82135);
        List<SelfDirectionReport> reports = List.of(
                new SelfDirectionReport(prepared.eventId(), 82135, "self.communication", "quieter", T0),
                new SelfDirectionReport(attend.eventId(), 82135, "self.communication", "quieter", T0.plusSeconds(1)),
                new SelfDirectionReport(follow.eventId(), 82135, "self.communication", "quieter", T0.plusSeconds(2)));
        FeedbackLedger ledger = feedback.load(prepared.context());
        ProfileLearningProposal hypothesis = ProfileLearningEngine.v1()
                .hypotheses(ledger, reports, 82135, version, 1, T0.plusSeconds(3)).get(0);
        learning.saveProposal(hypothesis);
        learning.confirmStored(prepared.context(), LearningActor.synthetic(82135), hypothesis.proposalId(), "derived1", T0.plusSeconds(4));
        assertThat(currentEvidence(82135)).contains("confirm.derived1", "self.pace", "lifestyle.hobbies");
        feedback.append(prepared.context(), retract(82135, "undo-a", prepared.eventId()), T0.plusSeconds(5));
        feedback.append(prepared.context(), retract(82135, "undo-b", attend.eventId()), T0.plusSeconds(6));
        feedback.append(prepared.context(), retract(82135, "undo-c", follow.eventId()), T0.plusSeconds(7));
        learning.saveProposal(ProfileLearningEngine.v1().hypotheses(List.of(
                new SelfDirectionReport(prepared.eventId(), 82135, "partner.communication", "quieter", T0),
                new SelfDirectionReport("extra-2", 82135, "partner.communication", "quieter", T0.plusSeconds(1)),
                new SelfDirectionReport("extra-3", 82135, "partner.communication", "quieter", T0.plusSeconds(2))
        ), 82135, currentSnapshotVersion(82135), 1, T0.plusSeconds(8)).get(0));
        learning.retractUnsupported(prepared.context(), 82135, T0.plusSeconds(9));
        learning.expireDue(T0.plus(Duration.ofDays(40)));
        String evidence = currentEvidence(82135);
        assertThat(evidence).contains("lifestyle.hobbies", "reading", "self.pace", "slow");
        assertThat(evidence).contains("confirm.derived1", "RETRACTED");
        assertThat(jdbc.queryForObject(
                "SELECT retracted FROM profile_learning_confirmation WHERE command_id = 'derived1'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT retracted FROM profile_learning_confirmation WHERE command_id = 'pace1'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT self_profile::text FROM relationship_profile_version
                WHERE user_id = 82135 AND is_current
                """, String.class)).doesNotContain("self.communication");
        Integer revoked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_consumption
                WHERE processing_status = 'REVOKED' AND event_id IN (?, ?, ?)
                """, Integer.class, prepared.eventId(), attend.eventId(), follow.eventId());
        assertThat(revoked).isEqualTo(3);
        assertThatThrownBy(() -> learning.confirmStored(prepared.context(), LearningActor.synthetic(82135),
                hypothesis.proposalId(), "derived1", T0.plusSeconds(10)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("RETRACTED_SOURCE");
        assertThat(currentEvidence(82135)).contains("RETRACTED");
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
        ensureAccount(userA);
        ensureAccount(userB);
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

    private void ensureAccount(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM \"user\" WHERE id = ?", Integer.class, userId);
        if (count != null && count > 0) return;
        byte[] phone = new byte[32];
        for (int i = 0; i < 8; i++) phone[i] = (byte) (userId >>> (8 * i));
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO "user" (id, phone_hash, invite_code, status, is_synthetic, cohort, created_at, updated_at)
                VALUES (?, ?, ?, 'active', TRUE, 'synthetic', ?, ?)
                """, userId, phone, "f" + userId, now, now);
        jdbc.update("INSERT INTO profile (user_id, version, updated_at) VALUES (?, 1, ?)", userId, now);
        profiles.grantProcessing(userId);
    }

    private void seedQuestionnaire(long userId) {
        replaceAnswers(userId, questionnaire(List.of("reading")));
    }

    private static Map<String, Object> questionnaire(List<String> hobbies) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("hobbies", hobbies);
        answers.put("relationshipLooking", List.of("serious"));
        answers.put("matchingStyle", "steady");
        answers.put("educationImportance", "kinda");
        return answers;
    }

    private void replaceAnswers(long userId, Map<String, Object> answers) {
        jdbc.update("DELETE FROM onboarding_answer WHERE user_id = ?", userId);
        Timestamp now = Timestamp.from(Instant.now());
        for (var entry : answers.entrySet()) {
            jdbc.update("""
                    INSERT INTO onboarding_answer (user_id, question_id, value_json, updated_at)
                    VALUES (?, ?, ?::jsonb, ?)
                    """, userId, entry.getKey(), JSON.writeValueAsString(entry.getValue()), now);
        }
    }

    private static ExplicitPreferenceCommand preference(long userId, String commandId, String eventId, int version) {
        return new ExplicitPreferenceCommand(commandId, userId, "partner.communication", null, "quiet",
                Importance.KINDA, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                version, 1, 1, true, true, List.of(eventId), T0);
    }

    private static FeedbackCommand retract(long actor, String key, String eventId) {
        return new FeedbackCommand(FeedbackEventType.FEEDBACK_RETRACTED, actor, FeedbackFacts.SourceType.PARTICIPANT,
                key, T0, eventId, null, null);
    }

    private String currentEvidence(long userId) {
        return jdbc.queryForObject("""
                SELECT evidence::text FROM relationship_profile_version
                WHERE user_id = ? AND is_current
                """, String.class, userId);
    }

    private int sourceVersion(long userId) {
        return jdbc.queryForObject("""
                SELECT source_profile_version FROM relationship_profile_version
                WHERE user_id = ? AND is_current
                """, Integer.class, userId);
    }

    private int currentSnapshotVersion(long userId) {
        return jdbc.queryForObject("""
                SELECT version FROM relationship_profile_version
                WHERE user_id = ? AND is_current
                """, Integer.class, userId);
    }

    private record Prepared(MatchFeedbackContext context, String eventId) {}
}
