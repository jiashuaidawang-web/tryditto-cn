package cn.aiditto.it;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.MockLlmModel;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.ProfileContext;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Statement;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.relationship.ProfileInputLoader;
import cn.aiditto.relationship.RelationshipProfileExtractor;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic users only. Extraction is not called by matching or by a scheduler. */
class ProfileExtractionIT extends ApiIT {
    private static final List<Statement> SLOW = List.of(new Statement("SELF", "我这个人比较慢热"));

    @Autowired RelationshipProfileService profiles;
    @Autowired RelationshipProfileExtractor extractor;
    @Autowired ProfileInputLoader loader;
    @Autowired LlmGateway gateway;
    @Autowired @Qualifier("primaryLlmModel") MockLlmModel model;

    @DynamicPropertySource
    static void enableProcessingForTests(DynamicPropertyRegistry registry) {
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
        registry.add("aiditto.llm.retry-delay-ms", () -> "0");
        registry.add("aiditto.llm.max-attempts", () -> "2");
    }

    @BeforeEach
    void resetModel() {
        model.clearScripts();
        model.clearFaults();
        model.clearCalls();
        gateway.resetCircuit();
    }

    @Test
    void provisionalFeatureIsStoredAsARevision() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        String before = currentHash(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        var run = extractor.extract(ready.userId, SLOW);
        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.accepted()).isEqualTo(1);
        assertThat(model.calls()).hasSize(1);
        assertThat(model.calls().get(0).user()).contains("数据开始", "我这个人比较慢热")
                .doesNotContain(Long.toString(ready.userId));
        assertThat(count(ready.userId)).isEqualTo(2);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
        assertThat(currentHash(ready.userId)).isNotEqualTo(before);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(ready.source);
        String self = text(ready.userId, "self_profile");
        String evidence = text(ready.userId, "evidence");
        assertThat(self).contains("self.pace", "slow", "PROVISIONAL");
        assertThat(evidence).contains("MODEL_INFERENCE", "PROVISIONAL").doesNotContain("我这个人比较慢热");
        assertThat(text(ready.userId, "memory_profile")).isEqualTo("[]");
        assertThat(taskStatus(ready, context.inputHash())).isEqualTo("completed");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'relationship_profile_extraction'
                """, String.class)).doesNotContain("prompt", "content", "context", "user_text");
    }

    @Test
    void modelExportAbsentDoesNotCallTheModel() {
        Ready ready = stored();
        String before = currentHash(ready.userId);
        var run = extractor.extract(ready.userId, SLOW);
        assertThat(run.status()).isEqualTo("export-denied");
        assertThat(model.calls()).isEmpty();
        assertThat(currentHash(ready.userId)).isEqualTo(before);
        assertThat(count(ready.userId)).isEqualTo(1);
    }

    @Test
    void processingGrantIsRequired() {
        long userId = user();
        assertThatThrownBy(() -> extractor.extract(userId, SLOW))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(model.calls()).isEmpty();
        assertThat(count(userId)).isZero();
    }

    @Test
    void revokedModelExportDiscardsTheInFlightResult() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        ProfileContext context = context(ready, SLOW);
        String before = currentHash(ready.userId);
        long generation = generation(ready.userId);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        model.beforeCall(() -> profiles.revokeModelExport(ready.userId));
        var run = extractor.extract(ready.userId, SLOW);
        assertThat(run.status()).isEqualTo("discarded");
        assertThat(model.calls()).hasSize(1);
        assertThat(currentHash(ready.userId)).isEqualTo(before);
        assertThat(count(ready.userId)).isEqualTo(1);
        assertThat(generation(ready.userId)).isEqualTo(generation);
        assertThat(jdbc.queryForObject("""
                SELECT processing_authorization FROM relationship_profile_lifecycle WHERE user_id = ?
                """, String.class, ready.userId)).isEqualTo("granted");
        assertThat(jdbc.queryForObject("""
                SELECT model_export_authorization FROM relationship_profile_lifecycle WHERE user_id = ?
                """, String.class, ready.userId)).isEqualTo("revoked");
        assertThat(taskStatus(ready, context.inputHash())).isEqualTo("discarded");
        var again = extractor.extract(ready.userId, SLOW);
        assertThat(again.status()).isEqualTo("export-denied");
        assertThat(model.calls()).hasSize(1);
        assertThat(currentHash(ready.userId)).isEqualTo(before);
    }

    @Test
    void revokedProcessingDiscardsTheInFlightResult() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        model.beforeCall(() -> profiles.revokeProcessing(ready.userId));
        var run = extractor.extract(ready.userId, SLOW);
        assertThat(run.status()).isEqualTo("discarded");
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThatThrownBy(() -> profiles.commitProvisional(ready.userId, ready.generation, context.inputHash(), SLOW,
                List.of(pace(token(context, "SELF_STATEMENT")))))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND retracted_at IS NULL AND superseded_at IS NULL
                """, Number.class, ready.userId).intValue()).isZero();
    }

    @Test
    void changedSourceDiscardsTheModelResult() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        String before = currentHash(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        model.beforeCall(() -> jdbc.update("UPDATE profile SET version = version + 1 WHERE user_id = ?", ready.userId));
        var run = extractor.extract(ready.userId, SLOW);
        assertThat(run.status()).isEqualTo("discarded");
        assertThat(currentHash(ready.userId)).isEqualTo(before);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(ready.source);
        assertThat(taskStatus(ready, context.inputHash())).isEqualTo("discarded");
    }

    @Test
    void sameInputDoesNotCallTheModelTwice() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        var first = extractor.extract(ready.userId, SLOW);
        String hash = currentHash(ready.userId);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPaceOther(token(context, "SELF_STATEMENT")));
        var second = extractor.extract(ready.userId, SLOW);
        assertThat(first.status()).isEqualTo("completed");
        assertThat(second.status()).isEqualTo("skipped");
        assertThat(model.calls()).hasSize(1);
        assertThat(currentHash(ready.userId)).isEqualTo(hash);
        assertThat(count(ready.userId)).isEqualTo(2);
        assertThat(taskAttempts(ready, context.inputHash())).isEqualTo(1);
    }

    @Test
    void invalidJsonOrTimeoutLeavesTheDeterministicSnapshot() {
        Ready broken = stored();
        profiles.grantModelExport(broken.userId);
        String brokenHash = currentHash(broken.userId);
        model.respond(ProfileExtractionPrompts.CALL_POINT, "not json");
        var invalid = extractor.extract(broken.userId, SLOW);
        assertThat(invalid.status()).isEqualTo("degraded");
        assertThat(currentHash(broken.userId)).isEqualTo(brokenHash);
        assertThat(count(broken.userId)).isEqualTo(1);
        assertThat(taskStatus(broken, context(broken, SLOW).inputHash())).isEqualTo("degraded");

        model.clearCalls();
        Ready timed = stored();
        profiles.grantModelExport(timed.userId);
        String timedHash = currentHash(timed.userId);
        model.enqueue(MockLlmModel.Fault.TIMEOUT, MockLlmModel.Fault.TIMEOUT);
        var timeout = extractor.extract(timed.userId, SLOW);
        assertThat(timeout.status()).isEqualTo("degraded");
        assertThat(model.calls()).hasSize(2);
        assertThat(currentHash(timed.userId)).isEqualTo(timedHash);
        assertThat(count(timed.userId)).isEqualTo(1);
        assertThat(text(timed.userId, "self_profile")).doesNotContain("self.pace");
    }

    @Test
    void degradedAttemptCanBeRetried() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, "{\"mock\":true}");
        var failed = extractor.extract(ready.userId, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        var retried = extractor.extract(ready.userId, SLOW);
        assertThat(failed.status()).isEqualTo("degraded");
        assertThat(retried.status()).isEqualTo("completed");
        assertThat(retried.accepted()).isEqualTo(1);
        assertThat(model.calls()).hasSize(2);
        assertThat(taskStatus(ready, context.inputHash())).isEqualTo("completed");
        assertThat(taskAttempts(ready, context.inputHash())).isEqualTo(2);
    }

    @Test
    void deletingUserCannotWrite() {
        Ready blocked = stored();
        profiles.grantModelExport(blocked.userId);
        jdbc.update("UPDATE \"user\" SET status = 'deleting' WHERE id = ?", blocked.userId);
        assertThatThrownBy(() -> extractor.extract(blocked.userId, SLOW))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThat(model.calls()).isEmpty();
        assertThat(count(blocked.userId)).isEqualTo(1);

        Ready inflight = stored();
        profiles.grantModelExport(inflight.userId);
        String before = currentHash(inflight.userId);
        ProfileContext context = context(inflight, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        model.beforeCall(() -> jdbc.update("UPDATE \"user\" SET status = 'deleting' WHERE id = ?", inflight.userId));
        var run = extractor.extract(inflight.userId, SLOW);
        assertThat(run.status()).isEqualTo("discarded");
        assertThat(currentHash(inflight.userId)).isEqualTo(before);
        assertThat(count(inflight.userId)).isEqualTo(1);
        assertThatThrownBy(() -> profiles.commitProvisional(inflight.userId, inflight.generation, context.inputHash(),
                SLOW, List.of(pace(token(context, "SELF_STATEMENT")))))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    void concurrentExtractsCallTheModelOnce() throws Exception {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        ProfileContext context = context(ready, SLOW);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace(token(context, "SELF_STATEMENT")));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> one = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return extractor.extract(ready.userId, SLOW).status();
            });
            Future<String> two = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return extractor.extract(ready.userId, SLOW).status();
            });
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .contains("completed")
                    .allMatch(status -> "completed".equals(status) || "busy".equals(status) || "skipped".equals(status));
        } finally {
            pool.shutdownNow();
        }
        assertThat(model.calls()).hasSize(1);
        assertThat(count(ready.userId)).isEqualTo(2);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
        assertThat(text(ready.userId, "self_profile")).contains("self.pace");
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(ready.source);
    }

    @Test
    void forgedEvidenceDoesNotChangeTheCurrentHash() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        long other = user();
        insertScenario(other, "B", "另一个人的情景题");
        String before = currentHash(ready.userId);
        model.respond(ProfileExtractionPrompts.CALL_POINT, selfPace("ev-nope"));
        var forged = extractor.extract(ready.userId, SLOW);
        assertThat(forged.status()).isEqualTo("completed");
        assertThat(forged.accepted()).isZero();
        assertThat(currentHash(ready.userId)).isEqualTo(before);
        assertThat(count(ready.userId)).isEqualTo(1);
        assertThat(model.calls()).hasSize(1);
        assertThat(model.calls().get(0).user()).doesNotContain("另一个人的情景题", Long.toString(other), Long.toString(ready.userId));
        assertThat(text(ready.userId, "self_profile")).doesNotContain("self.pace");
    }

    @Test
    void scenarioChoiceCanSupportASignalWithoutExportingThePrompt() {
        Ready ready = stored();
        profiles.grantModelExport(ready.userId);
        long other = user();
        insertScenario(ready.userId, "A", "这道情景题不能外发");
        insertScenario(other, "B", "另一个人的情景题");
        ProfileContext context = context(ready, List.of());
        assertThat(context.exportCanonical()).doesNotContain("这道情景题不能外发", "另一个人的情景题", Long.toString(ready.userId));
        model.respond(ProfileExtractionPrompts.CALL_POINT, scenario(token(context, "SCENARIO")));
        var run = extractor.extract(ready.userId, List.of());
        assertThat(run.status()).isEqualTo("completed");
        assertThat(run.accepted()).isEqualTo(1);
        assertThat(model.calls()).hasSize(1);
        assertThat(model.calls().get(0).user()).contains("\"value\":\"A\"")
                .doesNotContain("这道情景题不能外发", "另一个人的情景题", Long.toString(other));
        assertThat(text(ready.userId, "behavior_profile")).contains("behavior.scenario.signal", "SCENARIO_RESPONSE", "high");
        assertThat(text(ready.userId, "memory_profile")).isEqualTo("[]");
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(ready.source);
    }

    private ProfileContext context(Ready ready, List<Statement> statements) {
        return ProfileContextBuilder.build(loader.load(ready.userId, ready.source, ready.generation, true, true, statements));
    }

    private static String token(ProfileContext context, String kind) {
        return context.items().stream().filter(item -> kind.equals(item.kind())).map(ProfileContext.Item::token)
                .findFirst().orElseThrow();
    }

    private static ExtractedFeature pace(String token) {
        return new ExtractedFeature("SELF", "self.pace", "slow", "EQ", "NONE", List.of(token), 0.6, "STATED", "", false);
    }

    private static String selfPace(String token) {
        return featureJson("SELF", "self.pace", "slow", token, "");
    }

    private static String selfPaceOther(String token) {
        return featureJson("SELF", "self.pace", "fast", token, "");
    }

    private static String scenario(String token) {
        return featureJson("BEHAVIOR", "behavior.scenario.signal", "high", token, "SCENARIO_RESPONSE");
    }

    private static String featureJson(String section, String code, String value, String token, String behavior) {
        return "{\"features\":[{\"section\":\"" + section + "\",\"featureCode\":\"" + code
                + "\",\"proposedValue\":\"" + value + "\",\"targetOperator\":\"EQ\",\"importance\":\"NONE\","
                + "\"evidenceRefs\":[\"" + token + "\"],\"confidence\":0.6,\"reasonCode\":\"STATED\","
                + "\"behaviorKind\":\"" + behavior + "\",\"hardConstraint\":false}]}";
    }

    private void insertScenario(long userId, String choice, String prompt) {
        long sessionId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        long questionId = sessionId + 1;
        long answerId = sessionId + 2;
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO voice_session
                    (id, user_id, state, prompt_version, step_count, started_at, created_at, updated_at)
                VALUES (?, ?, 'completed', 'it', 1, ?, ?, ?)
                """, sessionId, userId, now, now, now);
        jdbc.update("""
                INSERT INTO voice_question
                    (id, session_id, step, scenario, options_json, prompt_version, created_at)
                VALUES (?, ?, 1, ?, ?::jsonb, 'it', ?)
                """, questionId, sessionId, prompt, "[{\"key\":\"" + choice + "\",\"text\":\"keep\"}]", now);
        jdbc.update("""
                INSERT INTO voice_answer
                    (id, question_id, session_id, user_id, chosen_key, custom_text_enc, created_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?)
                """, answerId, questionId, sessionId, userId, choice, now);
    }

    private Ready stored() {
        long userId = user();
        long generation = profiles.grantProcessing(userId);
        int source = 4;
        setSource(userId, source);
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("hobbies", List.of("reading"));
        answers.put("relationshipLooking", List.of("serious"));
        replaceAnswers(userId, answers);
        profiles.save(userId, source, generation,
                RelationshipProfileProjector.project(userId, answers, FeatureRegistry.v1()));
        return new Ready(userId, generation, source);
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

    private void setSource(long userId, int version) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM profile WHERE user_id = ?", Number.class, userId).intValue();
        if (existing == 0) {
            jdbc.update("INSERT INTO profile (user_id, version, updated_at) VALUES (?, ?, ?)",
                    userId, version, Timestamp.from(Instant.now()));
        } else {
            jdbc.update("UPDATE profile SET version = ? WHERE user_id = ?", version, userId);
        }
    }

    private long user() {
        return Long.parseLong(call("GET", "/api/me", null, login(randomPhone())).body().get("id").asString());
    }

    private String currentHash(long userId) {
        return jdbc.queryForObject("""
                SELECT input_hash FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """, String.class, userId).trim();
    }

    private String text(long userId, String column) {
        return jdbc.queryForObject("""
                SELECT %s::text FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """.formatted(column), String.class, userId);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ?",
                Number.class, userId).intValue();
    }

    private int currentCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ? AND is_current
                """, Number.class, userId).intValue();
    }

    private long generation(long userId) {
        return jdbc.queryForObject("SELECT generation_epoch FROM relationship_profile_lifecycle WHERE user_id = ?",
                Number.class, userId).longValue();
    }

    private String taskStatus(Ready ready, String hash) {
        return jdbc.queryForObject("""
                SELECT status FROM relationship_profile_extraction
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                """, String.class, ready.userId, hash, ProfileExtractionPrompts.VERSION, ready.generation);
    }

    private int taskAttempts(Ready ready, String hash) {
        return jdbc.queryForObject("""
                SELECT attempts FROM relationship_profile_extraction
                WHERE user_id = ? AND input_hash = ? AND prompt_version = ? AND generation_epoch = ?
                """, Number.class, ready.userId, hash, ProfileExtractionPrompts.VERSION, ready.generation).intValue();
    }

    private record Ready(long userId, long generation, int source) {}
}
