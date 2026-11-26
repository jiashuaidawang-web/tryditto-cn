package cn.aiditto.relationship;

import cn.aiditto.account.User;
import cn.aiditto.account.UserLock;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.ProfileVersion;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.ProfileVersionPolicy.WriteMode;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.SnapshotAdmission;
import cn.aiditto.infra.web.Times;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Writes immutable relationship-profile versions. Matching does not call this.
 * Profile processing stays off until account deletion and the processing grant are wired.
 * Matching consent and model-export permission are different grants and are not read here.
 */
@Service
public class RelationshipProfileService {
    private final JdbcTemplate jdbc;
    private final UserLock userLock;
    private final AnswerEvidenceDirectory answers;
    private final boolean processingEnabled;
    private final FeatureRegistry registry = FeatureRegistry.v1();

    public RelationshipProfileService(JdbcTemplate jdbc, UserLock userLock, AnswerEvidenceDirectory answers,
                                      @Value("${aiditto.relationship-profile.processing-enabled:false}") boolean processingEnabled) {
        this.jdbc = jdbc;
        this.userLock = userLock;
        this.answers = answers;
        this.processingEnabled = processingEnabled;
    }

    @Transactional
    public long grantProcessing(long userId) {
        requireEnabled();
        lockActive(userId);
        Lifecycle life = lifecycle(userId);
        Instant now = Times.millis(Instant.now());
        if (life == null) {
            jdbc.update("""
                    INSERT INTO relationship_profile_lifecycle
                        (user_id, latest_processed_source_version, generation_epoch,
                         processing_authorization, model_export_authorization, updated_at)
                    VALUES (?, 0, 1, 'granted', 'revoked', ?)
                    """, userId, Timestamp.from(now));
            return 1L;
        }
        if ("granted".equals(life.processing())) return life.generation();
        long generation = life.generation() + 1;
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET processing_authorization = 'granted', generation_epoch = ?, updated_at = ?
                WHERE user_id = ?
                """, generation, Timestamp.from(now), userId);
        return generation;
    }

    /** Records a model-export choice. It does not grant profile processing or matching. */
    @Transactional
    public void grantModelExport(long userId) {
        requireEnabled();
        lockActive(userId);
        Lifecycle life = lifecycle(userId);
        Instant now = Times.millis(Instant.now());
        if (life == null) {
            jdbc.update("""
                    INSERT INTO relationship_profile_lifecycle
                        (user_id, latest_processed_source_version, generation_epoch,
                         processing_authorization, model_export_authorization, updated_at)
                    VALUES (?, 0, 1, 'revoked', 'granted', ?)
                    """, userId, Timestamp.from(now));
            return;
        }
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET model_export_authorization = 'granted', updated_at = ?
                WHERE user_id = ?
                """, Timestamp.from(now), userId);
    }

    @Transactional
    public void revokeProcessing(long userId) {
        requireEnabled();
        if (userLock.lock(userId) == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        Lifecycle life = lifecycle(userId);
        if (life == null) return;
        Instant now = Times.millis(Instant.now());
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET processing_authorization = 'revoked', generation_epoch = ?, updated_at = ?
                WHERE user_id = ?
                """, life.generation() + 1, Timestamp.from(now), userId);
        retractLive(userId, now);
    }

    @Transactional
    public ProfileVersion save(long userId, int claimedSource, long generation, RelationshipProfileSnapshot offered) {
        requireEnabled();
        if (offered.userId() != userId) throw DomainException.invalid("userId", "画像不属于这个用户");
        lockActive(userId);
        Lifecycle life = lifecycle(userId);
        Integer actual = profileVersion(userId);
        Current current = currentRow(userId);
        ProfileVersionPolicy.Decision gate = ProfileVersionPolicy.decide(
                true, life != null && "granted".equals(life.processing()), generation,
                life == null ? -1 : life.generation(), life == null ? 0 : life.watermark(),
                actual == null ? -1 : actual, claimedSource, current != null, current == null ? null : current.hash(),
                current == null ? "" : current.hash(), WriteMode.DETERMINISTIC);
        if (gate.kind() != ProfileVersionPolicy.Kind.KEEP_CURRENT && gate.kind() != ProfileVersionPolicy.Kind.INSERT
                && gate.kind() != ProfileVersionPolicy.Kind.ACCEPT_REVISION) {
            return apply(userId, claimedSource, offered, gate);
        }
        RelationshipProfileSnapshot server = null;
        try {
            server = SnapshotAdmission.admit(
                    RelationshipProfileProjector.project(userId, answers.loadAnswers(userId), registry), registry, answers);
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("snapshot", e.getMessage());
        }
        if (!server.inputHash().equals(offered.inputHash())) {
            throw DomainException.invalid("inputHash", "提交的画像与服务端投影不一致");
        }
        ProfileVersionPolicy.Decision decision = ProfileVersionPolicy.decide(
                true, life != null && "granted".equals(life.processing()), generation,
                life == null ? -1 : life.generation(), life == null ? 0 : life.watermark(),
                actual == null ? -1 : actual, claimedSource, current != null, current == null ? null : current.hash(),
                server.inputHash(), WriteMode.DETERMINISTIC);
        return apply(userId, claimedSource, server, decision);
    }

    /** Model revisions are a different decision from a deterministic conflict, and this round refuses them. */
    @Transactional
    public ProfileVersion saveModelRevision(long userId, int claimedSource, long generation, RelationshipProfileSnapshot offered) {
        requireEnabled();
        lockActive(userId);
        throw new DomainException(ErrorCode.FORBIDDEN, "模型提取尚未接入，不能按模型修订写入画像");
    }

    /**
     * Clears the live snapshot and retires the generation that was allowed to write it.
     * A task that already started cannot restore the row by noticing that no current row remains.
     */
    @Transactional
    public void retractCurrent(long userId) {
        requireEnabled();
        if (userLock.lock(userId) == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        Lifecycle life = lifecycle(userId);
        Instant now = Times.millis(Instant.now());
        if (life != null) {
            jdbc.update("""
                    UPDATE relationship_profile_lifecycle
                    SET generation_epoch = ?, updated_at = ?
                    WHERE user_id = ?
                    """, life.generation() + 1, Timestamp.from(now), userId);
        }
        retractLive(userId, now);
    }

    /** Removes stored snapshots. Not called by account purge yet. The lifecycle row stays revoked. */
    @Transactional
    public void erase(long userId) {
        if (userLock.lock(userId) == null) return;
        jdbc.update("DELETE FROM relationship_profile_version WHERE user_id = ?", userId);
        Lifecycle life = lifecycle(userId);
        Instant now = Times.millis(Instant.now());
        if (life == null) return;
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET processing_authorization = 'revoked', generation_epoch = ?, updated_at = ?
                WHERE user_id = ?
                """, life.generation() + 1, Timestamp.from(now), userId);
    }

    @Transactional(readOnly = true)
    public Optional<ProfileVersion> current(long userId) {
        requireEnabled();
        String status = jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId);
        if (status == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        if (User.DELETING.equals(status) || "purged".equals(status)) {
            throw new DomainException(ErrorCode.ACCOUNT_INACTIVE, "账号正在注销，不能读取画像");
        }
        Lifecycle life = lifecycle(userId);
        if (life == null || !"granted".equals(life.processing())) return Optional.empty();
        Current current = currentRow(userId);
        return current == null ? Optional.empty() : Optional.of(current.versionRow());
    }

    public int latestProcessedSourceVersion(long userId) {
        requireEnabled();
        Lifecycle life = lifecycle(userId);
        return life == null ? 0 : life.watermark();
    }

    private ProfileVersion apply(long userId, int claimedSource, RelationshipProfileSnapshot snapshot,
                                 ProfileVersionPolicy.Decision decision) {
        return switch (decision.kind()) {
            case KEEP_CURRENT -> {
                advance(userId, decision.watermark());
                yield currentRow(userId).versionRow();
            }
            case INSERT -> insert(userId, claimedSource, snapshot, decision.watermark());
            case REJECT_INACTIVE -> throw new DomainException(ErrorCode.ACCOUNT_INACTIVE, "账号正在注销，不能写画像");
            case REJECT_UNAUTHORIZED, REJECT_GENERATION ->
                    throw new DomainException(ErrorCode.FORBIDDEN, "画像处理授权与这次任务的代次不一致");
            case REJECT_UNVERIFIED_SOURCE -> throw DomainException.invalid("sourceProfileVersion", "声明的源版本与 profile.version 不一致");
            case REJECT_STALE -> throw DomainException.invalid("sourceProfileVersion", "旧的源画像不能覆盖已处理的水位");
            case REJECT_CONFLICT -> throw DomainException.invalid("sourceProfileVersion", "同一个源版本的确定性投影不能改成另一份内容");
            case ACCEPT_REVISION -> throw new DomainException(ErrorCode.FORBIDDEN, "模型提取尚未接入，不能按模型修订写入画像");
        };
    }

    private ProfileVersion insert(long userId, int source, RelationshipProfileSnapshot snapshot, int watermark) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM relationship_profile_version WHERE user_id = ?", Integer.class, userId);
        int version = max + 1;
        Instant created = Times.millis(Instant.now());
        supersede(userId, created);
        jdbc.update("""
                INSERT INTO relationship_profile_version
                    (user_id, version, schema_version, feature_registry_version, source_profile_version, input_hash,
                     self_profile, preference_profile, behavior_profile, memory_profile, evidence, created_at, is_current)
                VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,TRUE)
                """, userId, version, snapshot.schemaVersion(), snapshot.featureRegistryVersion(), source,
                snapshot.inputHash(), snapshot.selfJson(), snapshot.preferenceJson(), snapshot.behaviorJson(),
                snapshot.memoryJson(), snapshot.evidenceJson(), Timestamp.from(created));
        advance(userId, watermark);
        return new ProfileVersion(userId, version, snapshot.schemaVersion(), snapshot.featureRegistryVersion(),
                source, snapshot.inputHash(), created, true, null);
    }

    private void supersede(long userId, Instant now) {
        jdbc.update("""
                UPDATE relationship_profile_version
                SET is_current = FALSE, superseded_at = ?
                WHERE user_id = ? AND retracted_at IS NULL AND superseded_at IS NULL
                """, Timestamp.from(now), userId);
    }

    private void retractLive(long userId, Instant now) {
        jdbc.update("""
                UPDATE relationship_profile_version
                SET is_current = FALSE, retracted_at = ?
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, Timestamp.from(now), userId);
    }

    private void advance(long userId, int watermark) {
        jdbc.update("""
                UPDATE relationship_profile_lifecycle
                SET latest_processed_source_version = ?, updated_at = ?
                WHERE user_id = ?
                """, watermark, Timestamp.from(Times.millis(Instant.now())), userId);
    }

    private void lockActive(long userId) {
        if (userLock.lock(userId) == null) throw new DomainException(ErrorCode.NOT_FOUND, "用户不存在");
        String status = jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, userId);
        if (User.DELETING.equals(status) || "purged".equals(status)) {
            throw new DomainException(ErrorCode.ACCOUNT_INACTIVE, "账号正在注销，不能写画像");
        }
    }

    private Integer profileVersion(long userId) {
        List<Integer> rows = jdbc.query("SELECT version FROM profile WHERE user_id = ?",
                (rs, n) -> rs.getInt("version"), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Current currentRow(long userId) {
        List<Current> rows = jdbc.query("""
                SELECT version, source_profile_version, input_hash, schema_version, feature_registry_version, created_at
                FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL AND superseded_at IS NULL
                """, (rs, n) -> new Current(
                rs.getInt("version"),
                rs.getString("input_hash").trim(),
                new ProfileVersion(userId, rs.getInt("version"), rs.getInt("schema_version"),
                        rs.getString("feature_registry_version"), rs.getInt("source_profile_version"),
                        rs.getString("input_hash").trim(), rs.getTimestamp("created_at").toInstant(), true, null)), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Lifecycle lifecycle(long userId) {
        List<Lifecycle> rows = jdbc.query("""
                SELECT latest_processed_source_version, generation_epoch, processing_authorization
                FROM relationship_profile_lifecycle WHERE user_id = ?
                """, (rs, n) -> new Lifecycle(rs.getInt("latest_processed_source_version"),
                rs.getLong("generation_epoch"), rs.getString("processing_authorization")), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireEnabled() {
        if (!processingEnabled) {
            throw new DomainException(ErrorCode.FORBIDDEN, "关系画像处理尚未接入账户与授权事件，不能启用");
        }
    }

    private record Current(int version, String hash, ProfileVersion versionRow) {}

    private record Lifecycle(int watermark, long generation, String processing) {}
}
