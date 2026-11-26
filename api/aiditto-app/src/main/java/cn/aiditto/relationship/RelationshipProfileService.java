package cn.aiditto.relationship;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.account.User;
import cn.aiditto.account.UserLock;
import cn.aiditto.infra.web.Times;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Writes immutable relationship-profile versions. Not used by matching.
 * The user row lock is the same lock publish already takes, so a delete and a profile write cannot pass each other.
 */
@Service
public class RelationshipProfileService {
    private final JdbcTemplate jdbc;
    private final UserLock userLock;

    public RelationshipProfileService(JdbcTemplate jdbc, UserLock userLock) {
        this.jdbc = jdbc;
        this.userLock = userLock;
    }

    @Transactional
    public ProfileVersion save(long userId, int sourceProfileVersion, RelationshipProfileSnapshot snapshot) {
        if (snapshot.userId() != userId) throw DomainException.invalid("userId", "画像不属于这个用户");
        if (userLock.lock(userId) == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        String status = jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId);
        boolean active = !User.DELETING.equals(status) && !"purged".equals(status);
        Current current = currentRow(userId);
        boolean hashElsewhere = activeHashElsewhere(userId, snapshot.inputHash(), current);
        ProfileVersionPolicy.Decision decision = ProfileVersionPolicy.decide(
                active,
                current == null ? null : current.version(),
                current == null ? null : current.source(),
                current == null ? null : current.hash(),
                hashElsewhere,
                sourceProfileVersion,
                snapshot.inputHash());
        return switch (decision.kind()) {
            case KEEP_CURRENT -> current.versionRow();
            case REJECT_INACTIVE -> throw new DomainException(ErrorCode.ACCOUNT_INACTIVE, "账号正在注销，不能写画像");
            case REJECT_STALE -> throw DomainException.invalid("sourceProfileVersion", "旧的源画像不能覆盖更新的版本");
            case REJECT_CONFLICT -> throw DomainException.invalid("sourceProfileVersion", "同一个源画像版本不能改成另一份内容");
            case REJECT_DUPLICATE -> throw DomainException.invalid("inputHash", "这份内容已经有一个未撤回的版本");
            case INSERT -> insert(userId, sourceProfileVersion, snapshot);
        };
    }

    @Transactional
    public void retractCurrent(long userId) {
        if (userLock.lock(userId) == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        jdbc.update("""
                UPDATE relationship_profile_version
                SET is_current = FALSE, retracted_at = ?
                WHERE user_id = ? AND is_current
                """, Timestamp.from(Times.millis(Instant.now())), userId);
    }

    /** Future account purge calls this. It removes every version, including withdrawn ones. */
    @Transactional
    public void erase(long userId) {
        if (userLock.lock(userId) == null) return;
        jdbc.update("DELETE FROM relationship_profile_version WHERE user_id = ?", userId);
    }

    public Optional<ProfileVersion> current(long userId) {
        List<ProfileVersion> rows = jdbc.query("""
                SELECT user_id, version, schema_version, feature_registry_version, source_profile_version,
                       input_hash, created_at, is_current, retracted_at
                FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, (rs, n) -> new ProfileVersion(
                rs.getLong("user_id"),
                rs.getInt("version"),
                rs.getInt("schema_version"),
                rs.getString("feature_registry_version"),
                rs.getInt("source_profile_version"),
                rs.getString("input_hash").trim(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getBoolean("is_current"),
                null), userId);
        return rows.stream().findFirst();
    }

    private ProfileVersion insert(long userId, int sourceProfileVersion, RelationshipProfileSnapshot snapshot) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM relationship_profile_version WHERE user_id = ?", Integer.class, userId);
        int version = max + 1;
        Instant created = Times.millis(Instant.now());
        jdbc.update("UPDATE relationship_profile_version SET is_current = FALSE WHERE user_id = ? AND is_current", userId);
        jdbc.update("""
                INSERT INTO relationship_profile_version
                    (user_id, version, schema_version, feature_registry_version, source_profile_version, input_hash,
                     self_profile, preference_profile, behavior_profile, memory_profile, evidence, created_at, is_current)
                VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,TRUE)
                """, userId, version, snapshot.schemaVersion(), snapshot.featureRegistryVersion(), sourceProfileVersion,
                snapshot.inputHash(), snapshot.selfJson(), snapshot.preferenceJson(), snapshot.behaviorJson(),
                snapshot.memoryJson(), snapshot.evidenceJson(), Timestamp.from(created));
        return new ProfileVersion(userId, version, snapshot.schemaVersion(), snapshot.featureRegistryVersion(),
                sourceProfileVersion, snapshot.inputHash(), created, true, null);
    }

    private Current currentRow(long userId) {
        List<Current> rows = jdbc.query("""
                SELECT version, source_profile_version, input_hash, schema_version, feature_registry_version, created_at
                FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, (rs, n) -> new Current(
                rs.getInt("version"),
                rs.getInt("source_profile_version"),
                rs.getString("input_hash").trim(),
                new ProfileVersion(userId, rs.getInt("version"), rs.getInt("schema_version"),
                        rs.getString("feature_registry_version"), rs.getInt("source_profile_version"),
                        rs.getString("input_hash").trim(), rs.getTimestamp("created_at").toInstant(), true, null)), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean activeHashElsewhere(long userId, String hash, Current current) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM relationship_profile_version
                WHERE user_id = ? AND input_hash = ? AND retracted_at IS NULL AND is_current = FALSE
                """, Integer.class, userId, hash);
        return count != null && count > 0 && (current == null || !hash.equals(current.hash()));
    }

    private record Current(int version, int source, String hash, ProfileVersion versionRow) {}
}
