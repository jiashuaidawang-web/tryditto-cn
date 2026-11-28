package cn.aiditto.it;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.LearningActor;
import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.relationship.OnboardingFieldMap;
import cn.aiditto.domain.relationship.PreferenceTarget;
import cn.aiditto.domain.relationship.ProfileFeature;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.feedback.ProfileLearningStore;
import cn.aiditto.feedback.RelationshipFeedbackStore;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic accounts only. This uses the product onboarding API, not an admin-built profile. */
class ProductProfileIntegrationIT extends ApiIT {
    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired RelationshipProfileService profiles;
    @Autowired QuestionCatalog catalog;
    @Autowired ProfileLearningStore learning;
    @Autowired RelationshipFeedbackStore feedback;

    @DynamicPropertySource
    static void isolatedV4(DynamicPropertyRegistry registry) {
        registry.add("aiditto.integration.mode", () -> "V4_INTEGRATION_TEST");
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
    }

    @Test
    void projectsOnboardingIntoTheAuthoritativeSnapshot() {
        Ready ready = onboard("阅读和音乐");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_consent WHERE user_id = ?", Integer.class, ready.userId))
                .isZero();
        RelationshipProfileSnapshot snapshot = profiles.projectAuthoritative(ready.userId);
        var stored = profiles.save(ready.userId, ready.source, ready.generation, snapshot);
        var again = profiles.save(ready.userId, ready.source, ready.generation, profiles.projectAuthoritative(ready.userId));
        assertThat(again.version()).isEqualTo(stored.version());
        assertThat(again.inputHash()).isEqualTo(stored.inputHash());
        assertThat(snapshot.self().features()).extracting(ProfileFeature::featureCode)
                .contains("lifestyle.hobbies", "lifestyle.music", "lifestyle.reading", "relationship.goal",
                        "matching.pace", "life.student", "education.year", "education.level", "occupation.text");
        assertThat(snapshot.preference().targets()).extracting(PreferenceTarget::featureCode)
                .contains("partner.age", "partner.height", "partner.education", "partner.gender");
        assertThat(snapshot.behaviorJson()).contains("opening.message");
        assertThat(snapshot.memory().observations()).isEmpty();
        assertThat(snapshot.evidenceJson()).contains("WITHHELD").doesNotContain("religion", "dealbreakers", "小明");
        assertThat(snapshot.preferenceJson()).contains("UNBOUNDED");
        assertThat(text(ready.userId, "self_profile")).doesNotContain("partner.communication");
    }

    @Test
    void answerEditKeepsOtherEvidenceAndConfirmedPreference() {
        Ready ready = onboard("确认偏好");
        profiles.save(ready.userId, ready.source, ready.generation, profiles.projectAuthoritative(ready.userId));
        long matchId = ready.userId;
        MatchFeedbackContext context = MatchFeedbackContext.experiment(matchId, "run-i01", LocalDate.of(2026, 11, 23),
                ready.userId, ready.otherId, true, 1, 1, "policy-1", "objective-g2-1", "strategy-e-1", true, true);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-" + matchId, T0, null, null, null), T0);
        RelationshipFeedbackEvent accept = feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED,
                ready.userId, FeedbackFacts.SourceType.PARTICIPANT, "acc-" + matchId, T0, null, null, null), T0);
        int snapshotVersion = profiles.current(ready.userId).orElseThrow().version();
        learning.confirm(context, new ExplicitPreferenceCommand("keep.edit", ready.userId, "partner.communication",
                null, "quiet", Importance.KINDA, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                snapshotVersion, ready.generation, 1, true, true, List.of(accept.eventId()), T0));
        answer(ready.token, "hobbies", List.of("music"));
        int source = jdbc.queryForObject("SELECT version FROM profile WHERE user_id = ?", Integer.class, ready.userId);
        assertThat(source).isGreaterThan(ready.source);
        RelationshipProfileSnapshot next = profiles.projectAuthoritative(ready.userId);
        profiles.save(ready.userId, source, ready.generation, next);
        String evidence = text(ready.userId, "evidence");
        assertThat(evidence).contains("lifestyle.music", "partner.age", "partner.communication");
        assertThat(evidence).doesNotContain("lifestyle.reading");
        assertThat(text(ready.userId, "preference_profile")).contains("partner.age");
        var repeat = profiles.save(ready.userId, source, ready.generation, profiles.projectAuthoritative(ready.userId));
        assertThat(repeat.inputHash()).isEqualTo(profiles.current(ready.userId).orElseThrow().inputHash());
    }

    @Test
    void revokeAndRegrantFollowTheLifecycle() {
        Ready ready = onboard("撤回后再授权");
        profiles.save(ready.userId, ready.source, ready.generation, profiles.projectAuthoritative(ready.userId));
        profiles.revokeProcessing(ready.userId);
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThatThrownBy(() -> profiles.save(ready.userId, ready.source, ready.generation,
                profiles.projectAuthoritative(ready.userId)))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        long generation = profiles.grantProcessing(ready.userId);
        assertThat(generation).isGreaterThan(ready.generation);
        RelationshipProfileSnapshot restored = profiles.projectAuthoritative(ready.userId);
        profiles.save(ready.userId, ready.source, generation, restored);
        assertThat(profiles.current(ready.userId)).isPresent();
        assertThat(restored.selfJson()).contains("lifestyle.hobbies");
        assertThat(restored.evidenceJson()).doesNotContain("religion");
    }

    @Test
    void catalogCoverageStaysExplicit() {
        List<OnboardingFieldMap.Row> rows = OnboardingFieldMap.cover(catalog);
        assertThat(rows).hasSize(catalog.all().size());
        assertThat(rows.stream().filter(row -> "MAPPED".equals(row.support()))).hasSize(OnboardingFieldMap.mappedCount());
        assertThat(rows.stream().filter(row -> "UNMAPPED".equals(row.support())))
                .allMatch(row -> row.featureCode().isEmpty() && !row.matchUse());
        assertThat(rows.stream().map(OnboardingFieldMap.Row::questionCode))
                .containsAll(RelationshipProfileProjector.STORED_QUESTIONS);
        assertThat(OnboardingFieldMap.row("religion").support()).isEqualTo("UNMAPPED");
        assertThat(OnboardingFieldMap.row("expectedAgeRange").hardConstraint()).isTrue();
        assertThat(OnboardingFieldMap.row("expectedAgeRange").kind()).isEqualTo("PARTNER_PREFERENCE");
        assertThat(OnboardingFieldMap.row("firstMessage").kind()).isEqualTo("BEHAVIOR_FACT");
    }

    @Test
    void correctionRacesAProfileSaveWithoutSplittingTheReadableSnapshot() throws Exception {
        Ready ready = onboard("并发修正");
        profiles.save(ready.userId, ready.source, ready.generation, profiles.projectAuthoritative(ready.userId));
        long matchId = ready.userId;
        MatchFeedbackContext context = MatchFeedbackContext.experiment(matchId, "run-i01-race", LocalDate.of(2026, 11, 23),
                ready.userId, ready.otherId, true, 1, 1, "policy-1", "objective-g2-1", "strategy-e-1", true, true);
        feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_PUBLISHED, 0, FeedbackFacts.SourceType.SYSTEM,
                "pub-race-" + matchId, T0, null, null, null), T0);
        RelationshipFeedbackEvent accept = feedback.append(context, new FeedbackCommand(FeedbackEventType.MATCH_ACCEPTED,
                ready.userId, FeedbackFacts.SourceType.PARTICIPANT, "acc-race-" + matchId, T0, null, null, null), T0);
        int snapshotVersion = profiles.current(ready.userId).orElseThrow().version();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> confirm = pool.submit(() -> {
                start.await();
                try {
                    learning.confirm(context, LearningActor.synthetic(ready.userId), new ExplicitPreferenceCommand(
                            "race.edit", ready.userId, "partner.communication", null, "quiet",
                            Importance.KINDA, Operator.EQ, ExplicitPreferenceCommand.Action.CONFIRM,
                            snapshotVersion, ready.generation, 1, true, true, List.of(accept.eventId()), T0));
                } catch (RuntimeException ignored) {
                    // The loser of the lock reports a stale snapshot. The readable row is checked below.
                }
                return null;
            });
            Future<?> save = pool.submit(() -> {
                start.await();
                try {
                    profiles.save(ready.userId, ready.source, ready.generation, profiles.projectAuthoritative(ready.userId));
                } catch (RuntimeException ignored) {
                    // A projection taken before the confirmation is rejected instead of replacing it.
                }
                return null;
            });
            start.countDown();
            confirm.get(30, TimeUnit.SECONDS);
            save.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        var current = profiles.current(ready.userId).orElseThrow();
        assertThat(current.inputHash()).isEqualTo(profiles.projectAuthoritative(ready.userId).inputHash());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """, Integer.class, ready.userId)).isEqualTo(1);
    }

    private Ready onboard(String occupation) {
        String phone = randomPhone();
        String otherPhone = randomPhone();
        String token = login(phone);
        String email = "i01" + phone.substring(3) + "@pku.edu.cn";
        answer(token, "isStudent", "yes");
        answer(token, "name", "小明");
        answer(token, "gender", "man");
        answer(token, "expectedGender", List.of("women"));
        answer(token, "birthday", "2004-05-01");
        answer(token, "hobbies", List.of("music", "reading"));
        answer(token, "musicSound", List.of("indie"));
        answer(token, "readingGenres", List.of("scifi"));
        answer(token, "location", Map.of("placeId", "wudaokou", "mode", "transit", "minutes", 45));
        answer(token, "relationshipLooking", List.of("serious"));
        answer(token, "matchingStyle", "steady");
        answer(token, "schoolYear", "junior");
        answer(token, "schoolEmail", email);
        answer(token, "religion", List.of("buddhist"));
        answer(token, "expectedAgeRange", List.of(22, 30));
        answer(token, "heightRange", Map.of("any", true, "min", 150, "max", 190));
        answer(token, "educationLevel", "preferNot");
        answer(token, "educationImportance", "none");
        answer(token, "occupation", occupation);
        answer(token, "firstMessage", "周末有空的话可以见面");
        assertThat(call("POST", "/api/auth/email/verify", Map.of("email", email, "code", DEMO_CODE), token).body().get("verified").asBoolean()).isTrue();
        answer(token, "emailVerify", DEMO_CODE);
        uploadPhoto(token, "profile", 800, 1000);
        uploadPhoto(token, "profile", 1000, 800);
        assertThat(call("POST", "/api/onboarding/complete", null, token).status()).isEqualTo(204);
        long userId = Long.parseLong(call("GET", "/api/me", null, token).body().get("id").asString());
        long otherId = Long.parseLong(call("GET", "/api/me", null, login(otherPhone)).body().get("id").asString());
        int source = jdbc.queryForObject("SELECT version FROM profile WHERE user_id = ?", Integer.class, userId);
        long generation = profiles.grantProcessing(userId);
        return new Ready(token, userId, otherId, source, generation);
    }

    private String text(long userId, String column) {
        return jdbc.queryForObject("""
                SELECT %s::text FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """.formatted(column), String.class, userId);
    }

    private record Ready(String token, long userId, long otherId, int source, long generation) {}
}
