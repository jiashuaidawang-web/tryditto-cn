package cn.aiditto.it;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes;
import cn.aiditto.relationship.RelationshipProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Synthetic users only. The new profile is not fed to the matcher. */
class RelationshipProfileIT extends ApiIT {
    @Autowired RelationshipProfileService profiles;

    @Test
    void persistsAndReadsTheSnapshot() {
        long userId = user();
        RelationshipProfileSnapshot snapshot = projector(userId, List.of("reading"), true);
        var stored = profiles.save(userId, 3, snapshot);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.inputHash()).isEqualTo(snapshot.inputHash());
        assertThat(profiles.current(userId).orElseThrow().inputHash()).isEqualTo(snapshot.inputHash());
        String self = text(userId, 1, "self_profile");
        String preference = text(userId, 1, "preference_profile");
        String evidence = text(userId, 1, "evidence");
        assertThat(self).contains("reading");
        assertThat(preference).contains("UNBOUNDED").doesNotContain("150", "men");
        assertThat(evidence).doesNotContain("测试姓名", "secret@example.com", "place-secret", "13800138000", "不能接受异地");
        assertThat(text(userId, 1, "memory_profile")).isEqualTo("[]");
    }

    @Test
    void sameInputDoesNotCreateAnotherVersion() {
        long userId = user();
        RelationshipProfileSnapshot snapshot = projector(userId, List.of("reading"), false);
        profiles.save(userId, 1, snapshot);
        var again = profiles.save(userId, 1, snapshot);
        var laterSource = profiles.save(userId, 5, snapshot);
        assertThat(again.version()).isEqualTo(1);
        assertThat(laterSource.version()).isEqualTo(1);
        assertThat(count(userId)).isEqualTo(1);
    }

    @Test
    void differentInputCreatesTheNextVersion() {
        long userId = user();
        var first = profiles.save(userId, 1, projector(userId, List.of("reading"), false));
        var second = profiles.save(userId, 2, projector(userId, List.of("reading", "music"), false));
        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(first.inputHash()).isNotEqualTo(second.inputHash());
        assertThat(profiles.current(userId).orElseThrow().version()).isEqualTo(2);
        assertThat(count(userId)).isEqualTo(2);
        assertThat(currentCount(userId)).isEqualTo(1);
    }

    @Test
    void olderSourceCannotOverwrite() {
        long userId = user();
        RelationshipProfileSnapshot first = projector(userId, List.of("reading"), false);
        RelationshipProfileSnapshot second = projector(userId, List.of("gym"), false);
        profiles.save(userId, 1, first);
        profiles.save(userId, 2, second);
        assertThatThrownBy(() -> profiles.save(userId, 1, first))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.VALIDATION);
        assertThat(profiles.current(userId).orElseThrow().inputHash()).isEqualTo(second.inputHash());
        assertThat(count(userId)).isEqualTo(2);
    }

    @Test
    void uniqueCurrentAndHashConstraints() {
        long userId = user();
        RelationshipProfileSnapshot snapshot = projector(userId, List.of("art"), false);
        profiles.save(userId, 1, snapshot);
        assertThatThrownBy(() -> insertCopy(userId, 9, snapshot.inputHash(), false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCopy(userId, 8, "a".repeat(64), true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(currentCount(userId)).isEqualTo(1);
    }

    @Test
    void legacyProfileAndTasteStayReadable() {
        long userId = user();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO profile (user_id, occupation, hobbies, updated_at) VALUES (?, ?, ?::jsonb, ?)",
                userId, "旧职业", "[\"reading\"]", now);
        jdbc.update("""
                INSERT INTO user_taste (user_id, version, persona_text, taste_text, embedding_version, created_at)
                VALUES (?, 1, ?, ?, ?, ?)
                """, userId, "旧分身", "旧口味", "v3", now);
        profiles.save(userId, 1, projector(userId, List.of("cooking"), false));
        assertThat(jdbc.queryForObject("SELECT occupation FROM profile WHERE user_id = ?", String.class, userId)).isEqualTo("旧职业");
        assertThat(jdbc.queryForObject("SELECT hobbies::text FROM profile WHERE user_id = ?", String.class, userId)).contains("reading");
        assertThat(jdbc.queryForObject("SELECT taste_text FROM user_taste WHERE user_id = ?", String.class, userId)).isEqualTo("旧口味");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'profile' AND column_name = 'expected_genders_enc'
                """, Number.class).intValue()).isEqualTo(1);
    }

    @Test
    void deletingUserIsRefusedAndEraseRemovesRows() {
        long userId = user();
        profiles.save(userId, 1, projector(userId, List.of("movies"), false));
        jdbc.update("UPDATE \"user\" SET status = 'deleting' WHERE id = ?", userId);
        assertThatThrownBy(() -> profiles.save(userId, 2, projector(userId, List.of("art"), false)))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);
        assertThat(count(userId)).isEqualTo(1);
        profiles.erase(userId);
        assertThat(count(userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId)).isEqualTo("deleting");
    }

    private RelationshipProfileSnapshot projector(long userId, List<String> hobbies, boolean sensitive) {
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

    private void insertCopy(long userId, int version, String hash, boolean current) {
        jdbc.update("""
                INSERT INTO relationship_profile_version
                    (user_id, version, schema_version, feature_registry_version, source_profile_version, input_hash,
                     self_profile, preference_profile, behavior_profile, memory_profile, evidence, created_at, is_current)
                VALUES (?, ?, 1, 'features-1', 1, ?, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, ?, ?)
                """, userId, version, hash, Timestamp.from(Instant.now()), current);
    }
}
