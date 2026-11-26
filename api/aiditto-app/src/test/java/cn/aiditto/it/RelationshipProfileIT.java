package cn.aiditto.it;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic users only. The new profile is not fed to the matcher. */
class RelationshipProfileIT extends ApiIT {
    @Autowired RelationshipProfileService profiles;

    @DynamicPropertySource
    static void enableProfileProcessing(DynamicPropertyRegistry registry) {
        registry.add("aiditto.relationship-profile.processing-enabled", () -> "true");
    }

    @Test
    void persistsAndReadsTheSnapshot() {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 3, List.of("reading"), true);
        var stored = profiles.save(ready.userId, 3, ready.generation, snapshot);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.inputHash()).isEqualTo(snapshot.inputHash());
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(snapshot.inputHash());
        String self = text(ready.userId, 1, "self_profile");
        String preference = text(ready.userId, 1, "preference_profile");
        String evidence = text(ready.userId, 1, "evidence");
        assertThat(self).contains("reading");
        assertThat(preference).contains("UNBOUNDED").doesNotContain("150", "men");
        assertThat(evidence).doesNotContain("测试姓名", "secret@example.com", "place-secret", "13800138000", "不能接受异地");
        assertThat(text(ready.userId, 1, "memory_profile")).isEqualTo("[]");
    }

    @Test
    void sameInputDoesNotCreateAnotherVersion() {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 1, List.of("reading"), false);
        profiles.save(ready.userId, 1, ready.generation, snapshot);
        var again = profiles.save(ready.userId, 1, ready.generation, snapshot);
        RelationshipProfileSnapshot sameLater = stage(ready.userId, 5, List.of("reading"), false);
        var laterSource = profiles.save(ready.userId, 5, ready.generation, sameLater);
        assertThat(again.version()).isEqualTo(1);
        assertThat(laterSource.version()).isEqualTo(1);
        assertThat(laterSource.inputHash()).isEqualTo(snapshot.inputHash());
        assertThat(count(ready.userId)).isEqualTo(1);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(5);
    }

    @Test
    void differentInputCreatesTheNextVersion() {
        Ready ready = ready();
        var first = save(ready, 1, List.of("reading"), false);
        var second = save(ready, 2, List.of("reading", "music"), false);
        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(first.inputHash()).isNotEqualTo(second.inputHash());
        assertThat(profiles.current(ready.userId).orElseThrow().version()).isEqualTo(2);
        assertThat(count(ready.userId)).isEqualTo(2);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
    }

    @Test
    void olderSourceCannotOverwrite() {
        Ready ready = ready();
        RelationshipProfileSnapshot first = stage(ready.userId, 1, List.of("reading"), false);
        profiles.save(ready.userId, 1, ready.generation, first);
        RelationshipProfileSnapshot second = stage(ready.userId, 2, List.of("gym"), false);
        profiles.save(ready.userId, 2, ready.generation, second);
        RelationshipProfileSnapshot late = stage(ready.userId, 1, List.of("reading"), false);
        assertThatThrownBy(() -> profiles.save(ready.userId, 1, ready.generation, late))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(second.inputHash());
        assertThat(count(ready.userId)).isEqualTo(2);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(2);
    }

    @Test
    void uniqueCurrentAndHashConstraints() {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 1, List.of("art"), false);
        profiles.save(ready.userId, 1, ready.generation, snapshot);
        assertThatThrownBy(() -> insertCopy(ready.userId, 9, snapshot.inputHash(), false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCopy(ready.userId, 8, "a".repeat(64), true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
    }

    @Test
    void legacyProfileAndTasteStayReadable() {
        Ready ready = ready();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO profile (user_id, occupation, hobbies, updated_at) VALUES (?, ?, ?::jsonb, ?)",
                ready.userId, "旧职业", "[\"reading\"]", now);
        jdbc.update("""
                INSERT INTO user_taste (user_id, version, persona_text, taste_text, embedding_version, created_at)
                VALUES (?, 1, ?, ?, ?, ?)
                """, ready.userId, "旧分身", "旧口味", "v3", now);
        save(ready, 1, List.of("cooking"), false);
        assertThat(jdbc.queryForObject("SELECT occupation FROM profile WHERE user_id = ?", String.class, ready.userId)).isEqualTo("旧职业");
        assertThat(jdbc.queryForObject("SELECT hobbies::text FROM profile WHERE user_id = ?", String.class, ready.userId)).contains("reading");
        assertThat(jdbc.queryForObject("SELECT taste_text FROM user_taste WHERE user_id = ?", String.class, ready.userId)).isEqualTo("旧口味");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'profile' AND column_name = 'expected_genders_enc'
                """, Number.class).intValue()).isEqualTo(1);
    }

    @Test
    void deletingUserIsRefusedAndEraseRemovesRows() {
        Ready ready = ready();
        save(ready, 1, List.of("movies"), false);
        jdbc.update("UPDATE \"user\" SET status = 'deleting' WHERE id = ?", ready.userId);
        assertThatThrownBy(() -> profiles.current(ready.userId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThatThrownBy(() -> save(ready, 2, List.of("art"), false))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThat(count(ready.userId)).isEqualTo(1);
        profiles.erase(ready.userId);
        assertThat(count(ready.userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, ready.userId)).isEqualTo("deleting");
        assertThatThrownBy(() -> profiles.current(ready.userId))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
    }

    @Test
    void delayedSourceCannotReplaceAHigherWatermark() {
        Ready ready = ready();
        RelationshipProfileSnapshot contentA = stage(ready.userId, 1, List.of("reading"), false);
        profiles.save(ready.userId, 1, ready.generation, contentA);
        RelationshipProfileSnapshot sameContent = stage(ready.userId, 5, List.of("reading"), false);
        var kept = profiles.save(ready.userId, 5, ready.generation, sameContent);
        assertThat(kept.version()).isEqualTo(1);
        assertThat(kept.inputHash()).isEqualTo(contentA.inputHash());
        assertThat(count(ready.userId)).isEqualTo(1);
        RelationshipProfileSnapshot delayed = stage(ready.userId, 3, List.of("gym"), false);
        assertThatThrownBy(() -> profiles.save(ready.userId, 3, ready.generation, delayed))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(contentA.inputHash());
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(5);
        assertThat(count(ready.userId)).isEqualTo(1);
    }

    @Test
    void returningToAnEarlierHashCreatesAnotherVersion() {
        Ready ready = ready();
        var first = save(ready, 1, List.of("reading"), false);
        var second = save(ready, 2, List.of("gym"), false);
        var third = save(ready, 3, List.of("reading"), false);
        assertThat(third.version()).isEqualTo(3);
        assertThat(third.inputHash()).isEqualTo(first.inputHash());
        assertThat(third.inputHash()).isNotEqualTo(second.inputHash());
        assertThat(count(ready.userId)).isEqualTo(3);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(first.inputHash());
        assertThat(liveCount(ready.userId, first.inputHash())).isEqualTo(1);
    }

    @Test
    void claimedSourceMustMatchTheStoredProfileVersion() {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 4, List.of("reading"), false);
        assertThatThrownBy(() -> profiles.save(ready.userId, 9, ready.generation, snapshot))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(count(ready.userId)).isZero();
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isZero();
        profiles.save(ready.userId, 4, ready.generation, snapshot);
        jdbc.update("UPDATE profile SET version = 2 WHERE user_id = ?", ready.userId);
        assertThatThrownBy(() -> profiles.save(ready.userId, 2, ready.generation, snapshot))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(4);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(snapshot.inputHash());
    }

    @Test
    void sameSourceDifferentContentIsADeterministicConflict() {
        Ready ready = ready();
        var stored = save(ready, 4, List.of("reading"), false);
        RelationshipProfileSnapshot other = stage(ready.userId, 4, List.of("gym"), false);
        assertThatThrownBy(() -> profiles.save(ready.userId, 4, ready.generation, other))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(stored.inputHash());
        assertThat(count(ready.userId)).isEqualTo(1);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(4);
    }

    @Test
    void modelRevisionIsRefused() {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 4, List.of("reading"), false);
        profiles.save(ready.userId, 4, ready.generation, snapshot);
        RelationshipProfileSnapshot other = stage(ready.userId, 4, List.of("gym"), false);
        assertThatThrownBy(() -> profiles.saveModelRevision(ready.userId, 4, ready.generation, other))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(snapshot.inputHash());
        assertThat(count(ready.userId)).isEqualTo(1);
    }

    @Test
    void revokedGenerationCannotWriteAndANewGenerationCan() {
        Ready ready = ready();
        save(ready, 1, List.of("reading"), false);
        profiles.revokeProcessing(ready.userId);
        RelationshipProfileSnapshot late = stage(ready.userId, 2, List.of("gym"), false);
        assertThatThrownBy(() -> profiles.save(ready.userId, 2, ready.generation, late))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(profiles.current(ready.userId)).isEmpty();
        long renewed = profiles.grantProcessing(ready.userId);
        assertThat(renewed).isNotEqualTo(ready.generation);
        var stored = profiles.save(ready.userId, 2, renewed, late);
        assertThat(stored.inputHash()).isEqualTo(late.inputHash());
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(late.inputHash());
        assertThatThrownBy(() -> profiles.save(ready.userId, 2, ready.generation, late))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(late.inputHash());
    }

    @Test
    void matchingConsentAndModelExportDoNotGrantProcessing() {
        Ready userOnly = new Ready(user(), 0);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO matching_consent
                    (user_id, consent_status, consent_version, granted_at, policy_version, updated_at)
                VALUES (?, 'granted', 1, ?, 'v4', ?)
                """, userOnly.userId, now, now);
        profiles.grantModelExport(userOnly.userId);
        RelationshipProfileSnapshot snapshot = stage(userOnly.userId, 1, List.of("reading"), false);
        assertThatThrownBy(() -> profiles.save(userOnly.userId, 1, 1, snapshot))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(count(userOnly.userId)).isZero();
        long generation = profiles.grantProcessing(userOnly.userId);
        profiles.save(userOnly.userId, 1, generation, snapshot);
        assertThat(profiles.current(userOnly.userId).orElseThrow().inputHash()).isEqualTo(snapshot.inputHash());
        assertThat(jdbc.queryForObject(
                "SELECT consent_status FROM matching_consent WHERE user_id = ?", String.class, userOnly.userId)).isEqualTo("granted");
        assertThat(jdbc.queryForObject(
                "SELECT model_export_authorization FROM relationship_profile_lifecycle WHERE user_id = ?",
                String.class, userOnly.userId)).isEqualTo("granted");
    }

    @Test
    void offeredSnapshotMustMatchTheServerProjection() {
        Ready ready = ready();
        stage(ready.userId, 1, List.of("reading"), false);
        RelationshipProfileSnapshot offered = RelationshipProfileProjector.project(ready.userId, Map.of(
                "hobbies", List.of("gym"),
                "relationshipLooking", List.of("serious")), FeatureRegistry.v1());
        assertThatThrownBy(() -> profiles.save(ready.userId, 1, ready.generation, offered))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(count(ready.userId)).isZero();
    }

    @Test
    void concurrentSavesKeepASingleCurrentRow() throws Exception {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 1, List.of("reading"), false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> profiles.save(ready.userId, 1, ready.generation, snapshot));
            var two = pool.submit(() -> profiles.save(ready.userId, 1, ready.generation, snapshot));
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(ready.userId)).isEqualTo(1);
        assertThat(currentCount(ready.userId)).isEqualTo(1);
        assertThat(profiles.latestProcessedSourceVersion(ready.userId)).isEqualTo(1);
    }

    @Test
    void retractCurrentBlocksTheOldGeneration() throws Exception {
        Ready ready = ready();
        save(ready, 1, List.of("reading"), false);
        RelationshipProfileSnapshot next = stage(ready.userId, 2, List.of("gym"), false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var retract = pool.submit(() -> profiles.retractCurrent(ready.userId));
            var late = pool.submit(() -> {
                try {
                    profiles.save(ready.userId, 2, ready.generation, next);
                    return "saved";
                } catch (DomainException error) {
                    return error.code().name();
                }
            });
            retract.get(20, TimeUnit.SECONDS);
            late.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThatThrownBy(() -> profiles.save(ready.userId, 2, ready.generation, next))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        long renewed = profiles.grantProcessing(ready.userId);
        assertThat(renewed).isGreaterThan(ready.generation);
        profiles.save(ready.userId, 2, renewed, next);
        assertThat(profiles.current(ready.userId).orElseThrow().inputHash()).isEqualTo(next.inputHash());
        assertThatThrownBy(() -> profiles.save(ready.userId, 2, ready.generation, next))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void eraseRacesSaveAndLeavesNoReadableProfile() throws Exception {
        Ready ready = ready();
        RelationshipProfileSnapshot snapshot = stage(ready.userId, 1, List.of("reading"), false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var erase = pool.submit(() -> profiles.erase(ready.userId));
            var late = pool.submit(() -> {
                try {
                    profiles.save(ready.userId, 1, ready.generation, snapshot);
                    return "saved";
                } catch (DomainException error) {
                    return error.code().name();
                }
            });
            erase.get(20, TimeUnit.SECONDS);
            late.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(ready.userId)).isZero();
        assertThat(profiles.current(ready.userId)).isEmpty();
        assertThatThrownBy(() -> profiles.save(ready.userId, 1, ready.generation, snapshot))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ProfileVersion save(Ready ready, int source, List<String> hobbies, boolean sensitive) {
        return profiles.save(ready.userId, source, ready.generation, stage(ready.userId, source, hobbies, sensitive));
    }

    private RelationshipProfileSnapshot stage(long userId, int source, List<String> hobbies, boolean sensitive) {
        setSource(userId, source);
        Map<String, Object> answers = answers(hobbies, sensitive);
        replaceAnswers(userId, answers);
        RelationshipProfileSnapshot snapshot = RelationshipProfileProjector.project(userId, answers, FeatureRegistry.v1());
        if (sensitive) {
            assertThat(snapshot.preference().targets()).anySatisfy(target -> {
                assertThat(target.featureCode()).isEqualTo("partner.gender");
                assertThat(target.constraintType()).isEqualTo(RelationshipTypes.ConstraintType.DOCUMENTED_EXISTING_RULE);
                assertThat(target.targetValue()).isEqualTo(new FeatureValue.ControlledRef("profile.expected_genders_enc"));
            });
        }
        return snapshot;
    }

    private Map<String, Object> answers(List<String> hobbies, boolean sensitive) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("hobbies", hobbies);
        answers.put("relationshipLooking", List.of("serious"));
        answers.put("heightRange", Map.of("any", true, "min", 150, "max", 190));
        answers.put("educationLevel", "preferNot");
        if (sensitive) {
            answers.put("name", "测试姓名");
            answers.put("expectedGender", List.of("men"));
            answers.put("personalEmail", "secret@example.com");
            answers.put("dealbreakers", "不能接受异地");
            answers.put("occupation", "请打13800138000");
            answers.put("location", Map.of("placeId", "place-secret"));
        }
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

    private void setSource(long userId, int version) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM profile WHERE user_id = ?", Number.class, userId).intValue();
        if (existing == 0) {
            jdbc.update("INSERT INTO profile (user_id, version, updated_at) VALUES (?, ?, ?)",
                    userId, version, Timestamp.from(Instant.now()));
        } else {
            jdbc.update("UPDATE profile SET version = ? WHERE user_id = ?", version, userId);
        }
    }

    private Ready ready() {
        long userId = user();
        return new Ready(userId, profiles.grantProcessing(userId));
    }

    private long user() {
        return Long.parseLong(call("GET", "/api/me", null, login(randomPhone())).body().get("id").asString());
    }

    private String text(long userId, int version, String column) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM relationship_profile_version WHERE user_id = ? AND version = ?",
                String.class, userId, version);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ?", Number.class, userId).intValue();
    }

    private int currentCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version WHERE user_id = ? AND is_current
                """, Number.class, userId).intValue();
    }

    private int liveCount(long userId, String hash) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND input_hash = ? AND retracted_at IS NULL AND superseded_at IS NULL
                """, Number.class, userId, hash).intValue();
    }

    private void insertCopy(long userId, int version, String hash, boolean current) {
        jdbc.update("""
                INSERT INTO relationship_profile_version
                    (user_id, version, schema_version, feature_registry_version, source_profile_version, input_hash,
                     self_profile, preference_profile, behavior_profile, memory_profile, evidence, created_at, is_current)
                VALUES (?, ?, 1, 'features-1', 1, ?, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, ?, ?)
                """, userId, version, hash, Timestamp.from(Instant.now()), current);
    }

    private record Ready(long userId, long generation) {}
}
