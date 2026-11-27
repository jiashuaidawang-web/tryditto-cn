package cn.aiditto.it;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fictional match ids only. This does not read production feedback or update a profile. */
class FeedbackEventIT extends ApiIT {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired RelationshipFeedbackStore store;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void releaseSharedActors() {
        jdbc.update("DELETE FROM relationship_feedback_erasure WHERE user_id IN (81001, 81002)");
    }

    @Test
    void sameKeyIsOneRow() {
        MatchFeedbackContext context = context(72001);
        FeedbackCommand publish = system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72001");
        RelationshipFeedbackEvent first = store.append(context, publish, T0);
        RelationshipFeedbackEvent second = store.append(context, publish, T0.plusSeconds(1));
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(count(72001)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO relationship_feedback_event (
                    event_id, schema_version, event_type, match_id, matching_run_id, matching_period,
                    actor_user_id, counterpart_user_id, direction, source_type, occurred_at, recorded_at,
                    idempotency_key, consent_scope, actor_profile_version_at_match,
                    counterpart_profile_version_at_match, compatibility_policy_version,
                    matching_objective_version, candidate_strategy_version, evidence_status
                ) VALUES (?, 'feedback-event-1', 'MATCH_PUBLISHED', 72001, 'run-f01', DATE '2026-11-23',
                          0, 0, 'JOINT', 'SYSTEM', ?, ?, 'it-pub-72001', 'SYSTEM', 7, 9,
                          'policy-1', 'objective-g2-1', 'strategy-e-1', 'SYSTEM_VERIFIED')
                """, "11111111-1111-1111-1111-111111111111", Timestamp.from(T0), Timestamp.from(T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replayMatchesTheStoredProjection() {
        MatchFeedbackContext context = context(72002);
        FeedbackLedger memory = new FeedbackLedger(context);
        memory.append(system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72002"), T0);
        memory.append(person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-a-72002"), T0.plusSeconds(1));
        memory.append(person(FeedbackEventType.MATCH_ACCEPTED, 81002, "it-acc-b-72002"), T0.plusSeconds(2));
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72002"), T0);
        store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-a-72002"), T0.plusSeconds(1));
        store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81002, "it-acc-b-72002"), T0.plusSeconds(2));
        FeedbackLedger loaded = store.load(context);
        assertThat(loaded.project().mutualAcceptance()).isTrue();
        assertThat(loaded.project().responseA()).isEqualTo(memory.project().responseA());
        assertThat(loaded.project().responseB()).isEqualTo(memory.project().responseB());
        assertThat(loaded.events()).hasSize(memory.events().size());
        assertThat(loaded.events().stream().map(RelationshipFeedbackEvent::eventType).toList())
                .isEqualTo(memory.events().stream().map(RelationshipFeedbackEvent::eventType).toList());
        FeedbackLedger again = store.load(context);
        assertThat(again.project()).isEqualTo(loaded.project());
    }

    @Test
    void eraseRemovesTheActorAndBlocksLearning() {
        MatchFeedbackContext context = context(72003);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72003"), T0);
        store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-a-72003"), T0.plusSeconds(1));
        store.append(context, person(FeedbackEventType.MATCH_DECLINED, 81002, "it-dec-b-72003"), T0.plusSeconds(2));
        store.eraseActor(81001, T0.plusSeconds(3));
        Integer actorRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_event WHERE match_id = 72003 AND actor_user_id = 81001",
                Integer.class);
        Integer counterpartBlocked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_event
                WHERE match_id = 72003 AND counterpart_user_id = 81001 AND learning_blocked = TRUE
                """, Integer.class);
        assertThat(actorRows).isZero();
        assertThat(counterpartBlocked).isEqualTo(1);
        assertThat(store.load(context).eligibility().matchFactEligible()).isFalse();
        assertThat(store.load(context).eligibility().reasons()).contains("ERASED");
        assertThat(store.load(context).events().stream().noneMatch(event -> event.actorUserId() == 81001)).isTrue();
    }

    @Test
    void concurrentAcceptsKeepOne() throws Exception {
        MatchFeedbackContext context = context(72004);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72004"), T0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger duplicates = new AtomicInteger();
        Future<?> first = pool.submit(() -> acceptOnce(context, "it-race-a", start, duplicates));
        Future<?> second = pool.submit(() -> acceptOnce(context, "it-race-b", start, duplicates));
        start.countDown();
        first.get(20, TimeUnit.SECONDS);
        second.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        Integer accepts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_event
                WHERE match_id = 72004 AND event_type = 'MATCH_ACCEPTED' AND actor_user_id = 81001
                """, Integer.class);
        assertThat(accepts).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(1);
    }

    @Test
    void acceptAndRetractBothRemainAndLearningStops() throws Exception {
        MatchFeedbackContext context = context(72005);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72005"), T0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> accept = pool.submit(() -> {
            start.await();
            store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72005"), T0.plusSeconds(1));
            return null;
        });
        Future<?> retract = pool.submit(() -> {
            start.await();
            store.append(context, person(FeedbackEventType.FEEDBACK_RETRACTED, 81001, "it-retract-72005"), T0.plusSeconds(1));
            return null;
        });
        start.countDown();
        accept.get(20, TimeUnit.SECONDS);
        retract.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(count(72005)).isEqualTo(3);
        assertThat(store.load(context).project().retracted()).isTrue();
        assertThat(store.load(context).eligibility().matchFactEligible()).isFalse();
    }

    @Test
    void erasedUserCannotAppendLater() {
        MatchFeedbackContext context = context(72007);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72007"), T0);
        store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72007"), T0);
        store.eraseActor(81001, T0.plusSeconds(1));
        assertThatThrownBy(() -> store.append(context, person(FeedbackEventType.MATCH_VIEWED, 81001, "it-late-72007"), T0.plusSeconds(2)))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("ERASED");
    }

    @Test
    void failedClaimRetriesAndASecondPolicyIsSeparate() {
        MatchFeedbackContext context = context(72008);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72008"), T0);
        RelationshipFeedbackEvent accept = store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72008"), T0);
        store.markClaimFailed(accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING, "learn-1", T0);
        FeedbackConsumption retried = store.claim(context, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-1", T0.plusSeconds(1), "artifact-1");
        assertThat(retried.derivedArtifactId()).isEqualTo("artifact-1");
        store.claim(context, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-2", T0.plusSeconds(2), "artifact-2");
        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_consumption WHERE event_id = ?
                """, Integer.class, accept.eventId());
        assertThat(rows).isEqualTo(2);
        assertThatThrownBy(() -> store.claim(context, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-1", T0.plusSeconds(3), "artifact-3"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("DUPLICATE_CONSUMPTION");
    }

    @Test
    void claimRollsBackAndRevokeKeepsTheArtifactTrace() {
        MatchFeedbackContext context = context(72009);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72009"), T0);
        RelationshipFeedbackEvent accept = store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72009"), T0);
        assertThatThrownBy(() -> store.claimThenRollback(context, accept.eventId(), T0))
                .isInstanceOf(FeedbackRejection.class);
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_consumption WHERE event_id = ?", Integer.class, accept.eventId());
        assertThat(before).isZero();
        store.claim(context, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING, "learn-1", T0, "artifact-9");
        store.append(context, person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "it-revoke-72009"), T0.plusSeconds(1));
        assertThat(store.revokeDerived(81001, T0.plusSeconds(2))).isZero();
        String status = jdbc.queryForObject("""
                SELECT processing_status FROM relationship_feedback_consumption WHERE event_id = ?
                """, String.class, accept.eventId());
        String artifact = jdbc.queryForObject("""
                SELECT derived_artifact_id FROM relationship_feedback_consumption WHERE event_id = ?
                """, String.class, accept.eventId());
        assertThat(status).isEqualTo("REVOKED");
        assertThat(artifact).isEqualTo("artifact-9");
        assertThatThrownBy(() -> store.claim(context, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-9", T0.plusSeconds(3), "again"))
                .isInstanceOf(FeedbackRejection.class);
    }

    @Test
    void eraseRacesAppendAndRevokeRacesClaim() throws Exception {
        MatchFeedbackContext eraseContext = context(72010);
        store.append(eraseContext, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72010"), T0);
        store.append(eraseContext, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72010"), T0);
        CountDownLatch eraseStart = new CountDownLatch(1);
        ExecutorService erasePool = Executors.newFixedThreadPool(2);
        Future<?> erase = erasePool.submit(() -> {
            eraseStart.await();
            store.eraseActor(81001, T0.plusSeconds(3));
            return null;
        });
        Future<?> late = erasePool.submit(() -> {
            eraseStart.await();
            try {
                store.append(eraseContext, person(FeedbackEventType.MATCH_VIEWED, 81001, "it-late-72010"), T0.plusSeconds(3));
            } catch (FeedbackRejection rejection) {
                if (!"ERASED".equals(rejection.code())) throw rejection;
            }
            return null;
        });
        eraseStart.countDown();
        erase.get(20, TimeUnit.SECONDS);
        late.get(20, TimeUnit.SECONDS);
        erasePool.shutdownNow();
        Integer actorRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_event WHERE actor_user_id = 81001 AND match_id = 72010
                """, Integer.class);
        Integer erasures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_erasure WHERE user_id = 81001", Integer.class);
        assertThat(actorRows).isZero();
        assertThat(erasures).isEqualTo(1);

        MatchFeedbackContext revokeContext = context(72011);
        store.append(revokeContext, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72011"), T0);
        RelationshipFeedbackEvent accept = store.append(revokeContext,
                person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72011"), T0);
        CountDownLatch revokeStart = new CountDownLatch(1);
        ExecutorService revokePool = Executors.newFixedThreadPool(2);
        AtomicInteger refused = new AtomicInteger();
        Future<?> claim = revokePool.submit(() -> {
            revokeStart.await();
            try {
                store.claim(revokeContext, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                        "learn-race", T0.plusSeconds(2), "artifact-race");
            } catch (FeedbackRejection rejection) {
                refused.incrementAndGet();
            }
            return null;
        });
        Future<?> revoke = revokePool.submit(() -> {
            revokeStart.await();
            store.append(revokeContext, person(FeedbackEventType.FEEDBACK_PROCESSING_REVOKED, 81001, "it-revoke-72011"),
                    T0.plusSeconds(2));
            return null;
        });
        revokeStart.countDown();
        claim.get(20, TimeUnit.SECONDS);
        revoke.get(20, TimeUnit.SECONDS);
        revokePool.shutdownNow();
        Integer applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_feedback_consumption
                WHERE event_id = ? AND processing_status = 'APPLIED'
                """, Integer.class, accept.eventId());
        assertThat(applied).isZero();
        jdbc.update("UPDATE relationship_feedback_event SET learning_blocked = FALSE WHERE match_id = 72011");
        assertThatThrownBy(() -> store.claim(revokeContext, accept.eventId(), FeedbackConsumption.Consumer.PROFILE_LEARNING,
                "learn-open", T0.plusSeconds(4), "artifact-open"))
                .isInstanceOf(FeedbackRejection.class);
        assertThat(refused.get()).isBetween(0, 1);
    }

    @Test
    void secondConsumptionDoesNotClaimAgain() {
        MatchFeedbackContext context = context(72006);
        store.append(context, system(FeedbackEventType.MATCH_PUBLISHED, "it-pub-72006"), T0);
        RelationshipFeedbackEvent accept = store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, "it-acc-72006"), T0);
        assertThat(store.markConsumed(accept.eventId(), T0.plusSeconds(1))).isTrue();
        assertThat(store.markConsumed(accept.eventId(), T0.plusSeconds(2))).isFalse();
        assertThat(store.load(context).eligibility().reasons()).contains("ALREADY_CONSUMED");
        assertThat(store.load(context).eligibility().profileUpdateAllowed()).isFalse();
    }

    private void acceptOnce(MatchFeedbackContext context, String key, CountDownLatch start, AtomicInteger duplicates) {
        try {
            start.await();
            store.append(context, person(FeedbackEventType.MATCH_ACCEPTED, 81001, key), T0.plusSeconds(1));
        } catch (FeedbackRejection rejection) {
            if ("DUPLICATE_EFFECT".equals(rejection.code())) duplicates.incrementAndGet();
            else throw rejection;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private int count(long matchId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_event WHERE match_id = ?", Integer.class, matchId);
        return count == null ? 0 : count;
    }

    private static MatchFeedbackContext context(long matchId) {
        return MatchFeedbackContext.experiment(matchId, "run-f01", LocalDate.of(2026, 11, 23),
                81001, 81002, true, 7, 9, "policy-1", "objective-g2-1", "strategy-e-1", true, true);
    }

    private static FeedbackCommand system(FeedbackEventType type, String key) {
        return new FeedbackCommand(type, 0, FeedbackFacts.SourceType.SYSTEM, key, T0, null, null, null);
    }

    private static FeedbackCommand person(FeedbackEventType type, long actor, String key) {
        return new FeedbackCommand(type, actor, FeedbackFacts.SourceType.PARTICIPANT, key, T0, null, null, null);
    }
}
