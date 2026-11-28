package cn.aiditto.it;

import cn.aiditto.account.AccountLifecycleCoordinator;
import cn.aiditto.account.AccountService;
import cn.aiditto.consent.MatchingConsentService;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.feedback.GroupEvaluationStore;
import cn.aiditto.feedback.ProfileLearningStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import cn.aiditto.onboarding.OnboardingService;
import cn.aiditto.relationship.ExtractionTaskStore;
import cn.aiditto.relationship.RelationshipProfileExtractor;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic users only. External erasure is a tracked task, not a live Qdrant call. */
class AccountLifecycleIT extends ApiIT {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired AccountService accounts;
    @Autowired AccountLifecycleCoordinator lifecycle;
    @Autowired RelationshipProfileService profiles;
    @Autowired RelationshipProfileExtractor extractor;
    @Autowired ExtractionTaskStore tasks;
    @Autowired MatchingConsentService consents;
    @Autowired RelationshipFeedbackStore feedback;
    @Autowired ProfileLearningStore learning;
    @Autowired GroupEvaluationStore observations;
    @Autowired OnboardingService onboarding;

    @DynamicPropertySource
    static void isolatedV4(DynamicPropertyRegistry registry) {
        registry.add("aiditto.integration.mode", () -> "V4_INTEGRATION_TEST");
        registry.add("aiditto.integration.environment", () -> "ci");
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
    }

    @Test
    void deleteStopsProcessingAndDoesNotClaimFullErasure() {
        Ready ready = ready("删除前");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        tasks.tryStart(ready.userId, "a".repeat(64), "prompt-i01", ready.generation, 1);
        assertThat(call("DELETE", "/api/account", null, ready.token).status()).isEqualTo(204);
        assertThat(profileRows(ready.userId)).isZero();
        assertThat(lifecycle.fullyErased(ready.userId)).isFalse();
        assertThat(taskStatus(ready.userId)).isEqualTo("NOT_RUN");
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, ready.userId)).isEqualTo("deleting");
        assertThat(call("PUT", "/api/onboarding/answers/occupation", Map.of("value", "迟到的答案"), ready.token).status()).isEqualTo(401);
    }

    @Test
    void repeatedDeleteAndLateTasksDoNotRestore() {
        Ready ready = ready("重复删除");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        tasks.tryStart(ready.userId, "b".repeat(64), "prompt-i01", ready.generation, 1);
        accounts.requestDelete(ready.userId);
        accounts.requestDelete(ready.userId);
        assertThat(profileRows(ready.userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_erasure_task WHERE user_id = ?", Integer.class, ready.userId)).isEqualTo(1);
        assertThat(tasks.complete(ready.userId, "b".repeat(64), "prompt-i01", ready.generation, "c".repeat(64), 1, 1)).isZero();
        assertThat(tasks.tryStart(ready.userId, "d".repeat(64), "prompt-i01", 9, 1).attempt())
                .isEqualTo(ExtractionTaskStore.Attempt.EXHAUSTED);
        assertThatThrownBy(() -> onboarding.writeAnswer(ready.userId, "occupation", "注销后的答案"))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThatThrownBy(() -> extractor.extract(ready.userId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThatThrownBy(() -> learning.confirm(context(ready, true, true), command(ready, true)))
                .isInstanceOf(FeedbackRejection.class);
        assertThat(lifecycle.fullyErased(ready.userId)).isFalse();
        assertThat(taskStatus(ready.userId)).isEqualTo("NOT_RUN");
        assertThat(profileRows(ready.userId)).isZero();
        assertThat(lifecycle.recordExternalFailure(ready.userId, AccountLifecycleCoordinator.QDRANT, "retry")).isTrue();
        assertThat(lifecycle.fullyErased(ready.userId)).isFalse();
        assertThat(taskStatus(ready.userId)).isEqualTo("FAILED");
    }

    @Test
    void externalFailureStaysPartial() {
        Ready ready = ready("外部失败");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        accounts.requestDelete(ready.userId);
        assertThat(lifecycle.recordExternalFailure(ready.userId, AccountLifecycleCoordinator.QDRANT,
                "timeout for 13800000001 token=secret")).isTrue();
        assertThat(lifecycle.fullyErased(ready.userId)).isFalse();
        assertThat(taskStatus(ready.userId)).isEqualTo("FAILED");
        String reason = jdbc.queryForObject(
                "SELECT last_error FROM account_erasure_task WHERE user_id = ? AND store_name = 'QDRANT'",
                String.class, ready.userId);
        assertThat(reason).doesNotContain("13800000001").doesNotContain("secret");
        accounts.requestDelete(ready.userId);
        assertThat(taskStatus(ready.userId)).isEqualTo("FAILED");
        assertThat(profileRows(ready.userId)).isZero();
        assertThatThrownBy(() -> profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId)))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    void consentRevokesAreSeparate() {
        Ready ready = ready("授权");
        assertThat(consents.current(ready.userId).consentStatus()).isEqualTo(ConsentStatus.UNKNOWN);
        consents.grant(ready.userId);
        assertThat(consents.revoke(ready.userId).consentStatus()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(consents.allowsPool(ready.userId)).isFalse();
        profiles.grantProcessing(ready.userId);
        profiles.revokeProcessing(ready.userId);
        assertThat(profiles.current(ready.userId)).isEmpty();
        profiles.grantModelExport(ready.userId);
        profiles.revokeModelExport(ready.userId);
        assertThat(jdbc.queryForObject(
                "SELECT model_export_authorization FROM relationship_profile_lifecycle WHERE user_id = ?",
                String.class, ready.userId)).isEqualTo("revoked");
        MatchFeedbackContext learningClosed = context(ready, true, false);
        feedback.append(learningClosed, published(ready), T0);
        RelationshipFeedbackEvent accept = feedback.append(learningClosed, accepted(ready), T0);
        assertThatThrownBy(() -> feedback.claim(learningClosed, accept.eventId(),
                FeedbackConsumption.Consumer.PROFILE_LEARNING, "profile-learning-v1", T0, "artifact"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_LEARNING_CONSENT");
        Ready group = ready("群体");
        MatchFeedbackContext groupOpen = context(group, true, true);
        feedback.append(groupOpen, published(group), T0);
        RelationshipFeedbackEvent groupAccept = feedback.append(groupOpen, accepted(group), T0);
        MatchFeedbackContext groupClosed = context(group, false, false);
        assertThatThrownBy(() -> feedback.claim(groupClosed, groupAccept.eventId(),
                FeedbackConsumption.Consumer.GROUP_EVALUATION, "group-eval-v1", T0, "group-artifact"))
                .isInstanceOf(FeedbackRejection.class)
                .extracting(error -> ((FeedbackRejection) error).code())
                .isEqualTo("NO_FEEDBACK_CONSENT");
    }

    @Test
    void anotherUserIsUntouched() {
        Ready first = ready("甲");
        Ready second = ready("乙");
        profiles.save(first.userId, 1, first.generation, profiles.projectAuthoritative(first.userId));
        var kept = profiles.save(second.userId, 1, second.generation, profiles.projectAuthoritative(second.userId));
        accounts.requestDelete(first.userId);
        assertThat(profileRows(first.userId)).isZero();
        assertThat(profiles.current(second.userId).orElseThrow().inputHash()).isEqualTo(kept.inputHash());
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, second.userId)).isNotEqualTo("deleting");
    }

    @Test
    void revokeRacesAProfileSave() throws Exception {
        Ready ready = ready("并发撤回");
        var stored = profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                start.await();
                try {
                    profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
                } catch (RuntimeException ignored) {
                    // Revoke may win the user lock first.
                }
                return null;
            });
            pool.submit(() -> {
                start.await();
                profiles.revokeProcessing(ready.userId);
                return null;
            });
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        profiles.revokeProcessing(ready.userId);
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """, Integer.class, ready.userId)).isZero();
        assertThat(stored.userId()).isEqualTo(ready.userId);
    }

    @Test
    void deleteRacesLearningConfirm() throws Exception {
        Ready ready = ready("锁确认");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        race(() -> accounts.requestDelete(ready.userId), () -> {
            try {
                learning.confirm(context(ready, true, true), command(ready, true));
            } catch (FeedbackRejection | DomainException ignored) {
                // The loser sees the closed account or a missing source.
            }
        });
        assertClosed(ready.userId);
        assertThatThrownBy(() -> learning.confirm(context(ready, true, true), command(ready, true)))
                .isInstanceOf(FeedbackRejection.class);
        assertThat(profileRows(ready.userId)).isZero();
    }

    @Test
    void deleteRacesFeedbackAppend() throws Exception {
        Ready ready = ready("锁反馈");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        race(() -> accounts.requestDelete(ready.userId), () -> {
            try {
                feedback.append(context(ready, true, true), accepted(ready), T0);
            } catch (FeedbackRejection ignored) {
                // Deletion won the user lock.
            }
        });
        assertClosed(ready.userId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_event WHERE actor_user_id = ?",
                Integer.class, ready.userId)).isZero();
    }

    @Test
    void deleteRacesGroupObservation() throws Exception {
        Ready ready = ready("锁群体");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        jdbc.update("""
                INSERT INTO matching_observation (
                    observation_id, match_id, matching_run_id, dataset_version,
                    user_a, user_b, profile_version_a, profile_version_b, payload, withdrawn, created_at
                ) VALUES ('obs.lock', ?, 'run.lock', 'set.lock', ?, ?, 1, 1, 'REDACTED', FALSE, ?)
                """, ready.userId, Math.min(ready.userId, ready.otherId), Math.max(ready.userId, ready.otherId),
                Timestamp.from(T0));
        race(() -> accounts.requestDelete(ready.userId), () -> observations.withdrawActor(ready.userId, T0));
        assertClosed(ready.userId);
        assertThat(jdbc.queryForObject(
                "SELECT withdrawn FROM matching_observation WHERE observation_id = 'obs.lock'",
                Boolean.class)).isTrue();
        assertThat(profileRows(ready.userId)).isZero();
    }

    @Test
    void bothUsersDeleteTogether() throws Exception {
        Ready first = ready("甲锁");
        Ready second = ready("乙锁");
        profiles.save(first.userId, 1, first.generation, profiles.projectAuthoritative(first.userId));
        profiles.save(second.userId, 1, second.generation, profiles.projectAuthoritative(second.userId));
        race(() -> accounts.requestDelete(first.userId), () -> accounts.requestDelete(second.userId));
        assertClosed(first.userId);
        assertClosed(second.userId);
        assertThat(profileRows(first.userId)).isZero();
        assertThat(profileRows(second.userId)).isZero();
    }

    @Test
    void deleteRacesExtraction() throws Exception {
        Ready ready = ready("锁提取");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        String hash = "e".repeat(64);
        race(() -> accounts.requestDelete(ready.userId),
                () -> tasks.tryStart(ready.userId, hash, "prompt-i01", ready.generation, 1));
        assertClosed(ready.userId);
        assertThat(tasks.complete(ready.userId, hash, "prompt-i01", ready.generation, "f".repeat(64), 1, 1)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_extraction
                WHERE user_id = ? AND status = 'running'
                """, Integer.class, ready.userId)).isZero();
        assertThat(profileRows(ready.userId)).isZero();
    }

    @Test
    void consentRevokeRacesProfileUpdate() throws Exception {
        Ready ready = ready("锁授权");
        profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
        race(() -> {
            try {
                profiles.save(ready.userId, 1, ready.generation, profiles.projectAuthoritative(ready.userId));
            } catch (RuntimeException ignored) {
                // Revoke can win the user row.
            }
        }, () -> profiles.revokeProcessing(ready.userId));
        profiles.revokeProcessing(ready.userId);
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, Integer.class, ready.userId)).isZero();
    }

    private void race(Runnable left, Runnable right) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                left.run();
                return null;
            });
            Future<?> second = pool.submit(() -> {
                barrier.await(15, TimeUnit.SECONDS);
                right.run();
                return null;
            });
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertClosed(long userId) {
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId)).isEqualTo("deleting");
        assertThat(lifecycle.fullyErased(userId)).isFalse();
        assertThat(taskStatus(userId)).isEqualTo("NOT_RUN");
    }

    private Ready ready(String occupation) {
        String token = login(randomPhone());
        answer(token, "hobbies", List.of("reading"));
        answer(token, "relationshipLooking", List.of("serious"));
        answer(token, "occupation", occupation);
        long userId = Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
        long otherId = Long.parseLong(call("GET", "/api/me", null, login(randomPhone())).body().get("id").asString());
        jdbc.update("INSERT INTO profile (user_id, version, updated_at) VALUES (?, 1, ?)",
                userId, Timestamp.from(Instant.now()));
        long generation = profiles.grantProcessing(userId);
        return new Ready(token, userId, otherId, generation);
    }

    private MatchFeedbackContext context(Ready ready, boolean feedbackConsent, boolean learningConsent) {
        return MatchFeedbackContext.experiment(ready.userId, "run-life-" + ready.userId,
                LocalDate.of(2026, 11, 23), ready.userId, ready.otherId, true, 1, 1,
                "policy-1", "objective-g2-1", "strategy-e-1", feedbackConsent, feedbackConsent,
                learningConsent, learningConsent);
    }

    private ExplicitPreferenceCommand command(Ready ready, boolean learning) {
        return new ExplicitPreferenceCommand("late.task", ready.userId, "partner.communication", null, "quiet",
                Importance.KINDA, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                1, ready.generation, 1, true, learning, List.of("missing-event"), T0);
    }

    private FeedbackCommand published(Ready ready) {
        return new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-" + ready.userId, T0, null, null, null);
    }

    private FeedbackCommand accepted(Ready ready) {
        return new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED, ready.userId, FeedbackFacts.SourceType.PARTICIPANT,
                "acc-" + ready.userId, T0, null, null, null);
    }

    private int profileRows(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ?", Integer.class, userId);
    }

    private String taskStatus(long userId) {
        return jdbc.queryForObject(
                "SELECT status FROM account_erasure_task WHERE user_id = ? AND store_name = 'QDRANT'",
                String.class, userId);
    }

    private record Ready(String token, long userId, long otherId, long generation) {}
}
