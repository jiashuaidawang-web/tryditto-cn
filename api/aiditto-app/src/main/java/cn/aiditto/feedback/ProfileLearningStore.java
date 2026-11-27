package cn.aiditto.feedback;

import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.learning.ConfirmationSources;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningEngine;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a confirmed preference into the existing profile version table.
 * The proposal row is not a second profile. Account deletion is not wired here.
 */
@Service
public class ProfileLearningStore {
    private final JdbcTemplate jdbc;
    private final RelationshipFeedbackStore feedback;
    private final ProfileLearningEngine engine = ProfileLearningEngine.v1();

    public ProfileLearningStore(JdbcTemplate jdbc, RelationshipFeedbackStore feedback) {
        this.jdbc = jdbc;
        this.feedback = feedback;
    }

    @Transactional
    public void saveProposal(ProfileLearningProposal proposal) {
        jdbc.update("""
                INSERT INTO profile_learning_proposal (
                    proposal_id, user_id, profile_version, lifecycle_generation, feature_code, profile_section,
                    old_value, proposed_value, operator_name, importance, source_event_ids, evidence_type,
                    evidence_status, proposal_reason, policy_version, status, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, proposal.proposalId(), proposal.userId(), proposal.profileVersion(), proposal.lifecycleGeneration(),
                proposal.featureCode(), proposal.profileSection().name(),
                new SqlParameterValue(Types.VARCHAR, proposal.oldValue()), proposal.proposedValue(),
                proposal.operator().name(), proposal.importance().name(), String.join(",", proposal.sourceEventIds()),
                proposal.evidenceType(), proposal.evidenceStatus(), proposal.proposalReason(), proposal.policyVersion(),
                proposal.status().name(), Timestamp.from(proposal.createdAt()), Timestamp.from(proposal.expiresAt()));
    }

    @Transactional
    public int expireDue(Instant now) {
        return jdbc.update("""
                UPDATE profile_learning_proposal
                SET status = 'EXPIRED', evidence_status = 'EXPIRED'
                WHERE status = 'PENDING_CONFIRMATION' AND expires_at < ?
                """, Timestamp.from(now));
    }

    @Transactional
    public ProfileLearningEngine.ProfileUpdate confirm(MatchFeedbackContext context, ExplicitPreferenceCommand command) {
        if (!context.participant(command.userId())) throw new FeedbackRejection("CROSS_USER");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(11, ?::int)", (int) command.userId());
        if (erased(command.userId())) throw new FeedbackRejection("ERASED");
        FeedbackLedger ledger = feedback.load(context);
        boolean sourceRetracted = ledger.events().stream().anyMatch(event -> event.actorUserId() == command.userId()
                && (event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED
                || event.eventType() == FeedbackEventType.FEEDBACK_RETRACTED));
        int current = currentVersion(command.userId());
        ConfirmationSources sources = new ConfirmationSources();
        List<ProfileEvidence> existing = loadEvidence(command.userId(), sources);
        ProfileLearningProposal proposal = engine.propose(command);
        String hash = currentHash(command.userId());
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, proposal, existing, current,
                command.lifecycleGeneration(), hash, true, false, sourceRetracted, sources);
        replaceProposal(update.proposal());
        if (update.snapshot() == null || update.decision().kind() == ProfileVersionPolicy.Kind.KEEP_CURRENT) {
            return update;
        }
        if (update.decision().kind() != ProfileVersionPolicy.Kind.INSERT
                && update.decision().kind() != ProfileVersionPolicy.Kind.ACCEPT_REVISION) {
            throw new FeedbackRejection(update.decision().kind().name());
        }
        insertVersion(command.userId(), current, update);
        insertConfirmation(command);
        for (String eventId : command.sourceEventIds()) {
            feedback.claim(context, eventId, FeedbackConsumption.Consumer.PROFILE_LEARNING,
                    ProfileLearningEngine.POLICY, command.confirmedAt(), update.proposal().proposalId());
        }
        return update;
    }

    @Transactional
    public void confirmThenRollback(MatchFeedbackContext context, ExplicitPreferenceCommand command) {
        confirm(context, command);
        throw new FeedbackRejection("ROLLBACK");
    }

    private void replaceProposal(ProfileLearningProposal proposal) {
        jdbc.update("DELETE FROM profile_learning_proposal WHERE proposal_id = ?", proposal.proposalId());
        saveProposal(proposal);
    }

    private void insertVersion(long userId, int current, ProfileLearningEngine.ProfileUpdate update) {
        Instant created = Instant.now();
        if (current > 0) {
            jdbc.update("""
                    UPDATE relationship_profile_version
                    SET is_current = FALSE, superseded_at = ?
                    WHERE user_id = ? AND is_current
                    """, Timestamp.from(created), userId);
        }
        var snapshot = update.snapshot();
        jdbc.update("""
                INSERT INTO relationship_profile_version
                    (user_id, version, schema_version, feature_registry_version, source_profile_version, input_hash,
                     self_profile, preference_profile, behavior_profile, memory_profile, evidence, created_at, is_current)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, TRUE)
                """, userId, current + 1, snapshot.schemaVersion(), snapshot.featureRegistryVersion(),
                update.decision().watermark(), snapshot.inputHash(), snapshot.selfJson(), snapshot.preferenceJson(),
                snapshot.behaviorJson(), snapshot.memoryJson(), snapshot.evidenceJson(), Timestamp.from(created));
    }

    private void insertConfirmation(ExplicitPreferenceCommand command) {
        jdbc.update("""
                INSERT INTO profile_learning_confirmation (
                    command_id, user_id, feature_code, old_value, new_value, operator_name, importance,
                    profile_version, lifecycle_generation, authorization_epoch, source_event_ids, retracted, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)
                """, command.commandId(), command.userId(), command.featureCode(),
                new SqlParameterValue(Types.VARCHAR, command.oldValue()), command.newValue(),
                command.operator().name(), command.importance().name(), command.profileVersion(),
                command.lifecycleGeneration(), command.authorizationEpoch(), String.join(",", command.sourceEventIds()),
                Timestamp.from(command.confirmedAt()));
    }

    private List<ProfileEvidence> loadEvidence(long userId, ConfirmationSources sources) {
        List<ProfileEvidence> evidence = new ArrayList<>();
        jdbc.query("""
                SELECT command_id, user_id, feature_code, old_value, new_value, operator_name, importance,
                       profile_version, lifecycle_generation, authorization_epoch, source_event_ids, retracted, created_at
                FROM profile_learning_confirmation
                WHERE user_id = ?
                ORDER BY created_at, command_id
                """, rs -> {
            ExplicitPreferenceCommand command = new ExplicitPreferenceCommand(
                    rs.getString("command_id"), rs.getLong("user_id"), rs.getString("feature_code"),
                    rs.getString("old_value"), rs.getString("new_value"),
                    Importance.valueOf(rs.getString("importance")), Operator.valueOf(rs.getString("operator_name")),
                    ExplicitPreferenceCommand.Action.CONFIRM, rs.getInt("profile_version"),
                    rs.getLong("lifecycle_generation"), rs.getLong("authorization_epoch"), true, true,
                    List.of(rs.getString("source_event_ids").split(",")), rs.getTimestamp("created_at").toInstant());
            ProfileEvidence item = engine.confirmationEvidence(command);
            sources.put(ConfirmationSources.explicit(userId, item.sourceRef(), false));
            if (rs.getBoolean("retracted")) {
                sources.retract(item);
                item = engine.retractEvidence(item);
            }
            evidence.add(item);
        }, userId);
        return evidence;
    }

    private int currentVersion(long userId) {
        Integer version = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version), 0) FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, Integer.class, userId);
        return version == null ? 0 : version;
    }

    private String currentHash(long userId) {
        return jdbc.query("""
                SELECT input_hash FROM relationship_profile_version
                WHERE user_id = ? AND is_current AND retracted_at IS NULL
                """, rs -> rs.next() ? rs.getString(1) : "", userId);
    }

    private boolean erased(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_erasure WHERE user_id = ?", Integer.class, userId);
        return count != null && count > 0;
    }
}
