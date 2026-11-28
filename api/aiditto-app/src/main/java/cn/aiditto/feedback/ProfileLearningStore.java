package cn.aiditto.feedback;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.learning.ConfirmationSources;
import cn.aiditto.domain.feedback.v4.learning.ExplicitPreferenceCommand;
import cn.aiditto.domain.feedback.v4.learning.LearningActor;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningEngine;
import cn.aiditto.domain.feedback.v4.learning.ProfileLearningProposal;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileVersionPolicy;
import cn.aiditto.domain.relationship.RelationshipProfile;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import cn.aiditto.domain.relationship.RelationshipTypes.Operator;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.SnapshotAdmission;
import cn.aiditto.account.UserLifecycleLock;
import cn.aiditto.relationship.AnswerEvidenceDirectory;
import cn.aiditto.relationship.RelationshipProfileService;
import cn.aiditto.relationship.RelationshipProfileService.LearningPermit;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Confirms one preference through {@link RelationshipProfileService}.
 * The proposal row is not a second profile. Account deletion follows
 * {@link RelationshipProfileService#FUTURE_ACCOUNT_DELETION} and is not called here.
 */
@Service
public class ProfileLearningStore {
    private final JdbcTemplate jdbc;
    private final RelationshipFeedbackStore feedback;
    private final RelationshipProfileService profiles;
    private final AnswerEvidenceDirectory answers;
    private final UserLifecycleLock locks;
    private final ProfileLearningEngine engine = ProfileLearningEngine.v1();
    private final FeatureRegistry registry = FeatureRegistry.v1();

    public ProfileLearningStore(JdbcTemplate jdbc, RelationshipFeedbackStore feedback,
                                @Lazy RelationshipProfileService profiles, AnswerEvidenceDirectory answers,
                                UserLifecycleLock locks) {
        this.jdbc = jdbc;
        this.feedback = feedback;
        this.profiles = profiles;
        this.answers = answers;
        this.locks = locks;
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
                proposal.operator().name(), proposal.importance().name(), join(proposal.sourceEventIds()),
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
        return confirm(context, LearningActor.synthetic(command.userId()), command);
    }

    @Transactional
    public ProfileLearningEngine.ProfileUpdate confirm(MatchFeedbackContext context, LearningActor actor,
                                                       ExplicitPreferenceCommand command) {
        return confirmBound(context, actor, command, null);
    }

    /**
     * Confirms the proposal row that is already stored.
     * The caller picks the command id and the time. The value, the expiry, and the sources stay on the row.
     */
    @Transactional
    public ProfileLearningEngine.ProfileUpdate confirmStored(MatchFeedbackContext context, LearningActor actor,
                                                             String proposalId, String commandId, Instant confirmedAt) {
        ProfileLearningProposal stored = findProposal(proposalId);
        if (stored == null || stored.userId() != actor.userId()) throw new FeedbackRejection("PROPOSAL_MISMATCH");
        ExplicitPreferenceCommand command = new ExplicitPreferenceCommand(commandId, stored.userId(), stored.featureCode(),
                stored.oldValue(), stored.proposedValue(), stored.importance(), stored.operator(),
                ExplicitPreferenceCommand.Action.CONFIRM, stored.profileVersion(), stored.lifecycleGeneration(),
                1, true, true, stored.sourceEventIds(), confirmedAt);
        return confirmBound(context, actor, command, stored);
    }

    @Transactional
    public void confirmThenRollback(MatchFeedbackContext context, ExplicitPreferenceCommand command) {
        confirm(context, command);
        throw new FeedbackRejection("ROLLBACK");
    }

    /**
     * Drops derived evidence whose sources have all been retracted.
     * A value the user stated directly stays. The previous snapshot is not restored.
     */
    @Transactional
    public void retractUnsupported(MatchFeedbackContext context, long userId, Instant when) {
        hold(userId);
        if (erased(userId)) throw new FeedbackRejection("ERASED");
        LearningPermit permit = lock(userId);
        if (!permit.processingGranted()) throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        FeedbackLedger ledger = feedback.load(context);
        Set<String> superseded = new LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.supersedesEventId() != null) superseded.add(event.supersedesEventId());
        }
        List<StoredConfirmation> rows = confirmations(userId);
        Set<String> revokedEvents = new LinkedHashSet<>();
        for (StoredConfirmation row : rows) {
            if (row.retracted || !"DERIVED".equals(row.origin) || row.sources.isEmpty()) continue;
            boolean everySourceGone = row.sources.stream().allMatch(superseded::contains);
            if (!everySourceGone) continue;
            jdbc.update("UPDATE profile_learning_confirmation SET retracted = TRUE WHERE command_id = ?", row.commandId);
            revokedEvents.addAll(row.sources);
        }
        invalidatePending(userId, superseded);
        if (revokedEvents.isEmpty()) return;
        if (erased(userId)) throw new FeedbackRejection("ERASED");
        LearningPermit again = lock(userId);
        if (!again.accountActive() || !again.processingGranted() || again.generation() != permit.generation()) {
            throw new FeedbackRejection("ACCOUNT_INACTIVE");
        }
        writeCurrent(userId);
        for (String eventId : revokedEvents) feedback.revokeEvent(eventId, when);
    }

    /** Confirmation rows only. Questionnaire evidence is loaded separately. */
    public List<ProfileEvidence> storedEvidence(long userId, ConfirmationSources sources) {
        List<ProfileEvidence> evidence = new ArrayList<>();
        for (StoredConfirmation row : confirmations(userId)) {
            evidence.add(evidenceOf(row, sources));
        }
        return evidence;
    }

    private ProfileLearningEngine.ProfileUpdate confirmBound(MatchFeedbackContext context, LearningActor actor,
                                                             ExplicitPreferenceCommand command,
                                                             ProfileLearningProposal forced) {
        if (actor.userId() != command.userId() || !context.participant(command.userId())) {
            throw new FeedbackRejection("CROSS_USER");
        }
        if (!command.learningGranted() || !context.learningConsent(command.userId())) {
            throw new FeedbackRejection("NO_LEARNING_CONSENT");
        }
        if (!command.feedbackProcessingGranted() || !context.feedbackConsent(command.userId())) {
            throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        }
        hold(command.userId());
        if (erased(command.userId())) throw new FeedbackRejection("ERASED");
        LearningPermit permit = lock(command.userId());
        if (!permit.accountActive()) throw new FeedbackRejection("ACCOUNT_INACTIVE");
        if (!permit.processingGranted()) throw new FeedbackRejection("NO_FEEDBACK_CONSENT");
        StoredConfirmation existing = confirmation(command.commandId());
        if (existing != null && !sameCommand(existing, command)) throw new FeedbackRejection("IDEMPOTENCY_CONFLICT");
        if (existing != null && existing.retracted) throw new FeedbackRejection("RETRACTED_SOURCE");
        ProfileLearningProposal stored = forced == null ? findProposal("prop." + command.commandId()) : forced;
        String origin = "DIRECT";
        ProfileLearningProposal proposal;
        if (stored != null) {
            if (!sameProposal(stored, command)) throw new FeedbackRejection("PROPOSAL_MISMATCH");
            proposal = stored;
            if ("PREFERENCE_HYPOTHESIS".equals(stored.evidenceType())) origin = "DERIVED";
        } else {
            proposal = engine.propose(command);
        }
        FeedbackLedger ledger = feedback.load(context);
        boolean sourceRetracted = command.sourceEventIds().stream().anyMatch(id -> retractedSource(ledger, id))
                || ledger.events().stream().anyMatch(event -> event.actorUserId() == command.userId()
                && event.eventType() == FeedbackEventType.FEEDBACK_PROCESSING_REVOKED);
        ConfirmationSources sources = new ConfirmationSources();
        sources.delegate(answers);
        List<ProfileEvidence> evidence = loadEvidence(command.userId(), sources);
        ProfileLearningEngine.ProfileUpdate update = engine.apply(command, proposal, evidence, permit.snapshotVersion(),
                permit.generation(), permit.hash(), true, false, sourceRetracted, sources);
        replaceProposal(update.proposal());
        if (update.snapshot() == null || update.decision().kind() == ProfileVersionPolicy.Kind.KEEP_CURRENT) {
            return update;
        }
        if (update.decision().kind() != ProfileVersionPolicy.Kind.INSERT
                && update.decision().kind() != ProfileVersionPolicy.Kind.ACCEPT_REVISION) {
            throw new FeedbackRejection(update.decision().kind().name());
        }
        if (erased(command.userId())) throw new FeedbackRejection("ERASED");
        LearningPermit again = lock(command.userId());
        if (!again.accountActive() || !again.processingGranted() || again.generation() != permit.generation()) {
            throw new FeedbackRejection("ACCOUNT_INACTIVE");
        }
        try {
            profiles.commitLearning(command.userId(), update.snapshot());
        } catch (DomainException ex) {
            throw rejection(ex);
        }
        if (existing == null) insertConfirmation(command, origin);
        for (String eventId : command.sourceEventIds()) {
            if (eventId.isBlank()) continue;
            feedback.claim(context, eventId, FeedbackConsumption.Consumer.PROFILE_LEARNING,
                    ProfileLearningEngine.POLICY, command.confirmedAt(), update.proposal().proposalId());
        }
        return update;
    }

    private void writeCurrent(long userId) {
        ConfirmationSources sources = new ConfirmationSources();
        sources.delegate(answers);
        List<ProfileEvidence> evidence = loadEvidence(userId, sources);
        RelationshipProfileSnapshot admitted = SnapshotAdmission.admit(
                RelationshipProfile.of(userId, evidence, registry).snapshot(registry), registry, sources);
        try {
            profiles.commitLearning(userId, admitted);
        } catch (DomainException ex) {
            throw rejection(ex);
        }
    }

    private List<ProfileEvidence> loadEvidence(long userId, ConfirmationSources sources) {
        List<ProfileEvidence> evidence = new ArrayList<>(
                RelationshipProfileProjector.project(userId, answers.loadAnswers(userId), registry).evidence());
        Set<String> ids = new LinkedHashSet<>();
        for (ProfileEvidence item : evidence) ids.add(item.evidenceId());
        for (ProfileEvidence item : storedEvidence(userId, sources)) {
            if (ids.add(item.evidenceId())) evidence.add(item);
        }
        return evidence;
    }

    private ProfileEvidence evidenceOf(StoredConfirmation row, ConfirmationSources sources) {
        ExplicitPreferenceCommand command = new ExplicitPreferenceCommand(row.commandId, row.userId, row.featureCode,
                row.oldValue, row.newValue, row.importance, row.operator, ExplicitPreferenceCommand.Action.CONFIRM,
                row.profileVersion, row.generation, row.authorizationEpoch, true, true, row.sources, row.createdAt);
        ProfileEvidence item = engine.confirmationEvidence(command);
        sources.put(ConfirmationSources.explicit(row.userId, item.sourceRef(), false));
        if (row.retracted) {
            sources.retract(item);
            item = engine.retractEvidence(item);
        }
        return item;
    }

    private void invalidatePending(long userId, Set<String> superseded) {
        List<ProfileLearningProposal> pending = jdbc.query("""
                SELECT proposal_id, user_id, profile_version, lifecycle_generation, feature_code, profile_section,
                       old_value, proposed_value, operator_name, importance, source_event_ids, evidence_type,
                       evidence_status, proposal_reason, policy_version, status, created_at, expires_at
                FROM profile_learning_proposal
                WHERE user_id = ? AND status = 'PENDING_CONFIRMATION'
                """, (rs, n) -> mapProposal(rs), userId);
        for (ProfileLearningProposal proposal : pending) {
            boolean cites = proposal.sourceEventIds().stream().anyMatch(superseded::contains);
            if (!cites) continue;
            replaceProposal(proposal.withStatus(ProfileLearningProposal.Status.RETRACTED, "RETRACTED"));
        }
    }

    private void replaceProposal(ProfileLearningProposal proposal) {
        jdbc.update("DELETE FROM profile_learning_proposal WHERE proposal_id = ?", proposal.proposalId());
        saveProposal(proposal);
    }

    private void insertConfirmation(ExplicitPreferenceCommand command, String origin) {
        jdbc.update("""
                INSERT INTO profile_learning_confirmation (
                    command_id, user_id, feature_code, old_value, new_value, operator_name, importance,
                    profile_version, lifecycle_generation, authorization_epoch, source_event_ids, retracted,
                    created_at, evidence_origin
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)
                """, command.commandId(), command.userId(), command.featureCode(),
                new SqlParameterValue(Types.VARCHAR, command.oldValue()), command.newValue(),
                command.operator().name(), command.importance().name(), command.profileVersion(),
                command.lifecycleGeneration(), command.authorizationEpoch(), join(command.sourceEventIds()),
                Timestamp.from(command.confirmedAt()), origin);
    }

    private ProfileLearningProposal findProposal(String proposalId) {
        List<ProfileLearningProposal> rows = jdbc.query("""
                SELECT proposal_id, user_id, profile_version, lifecycle_generation, feature_code, profile_section,
                       old_value, proposed_value, operator_name, importance, source_event_ids, evidence_type,
                       evidence_status, proposal_reason, policy_version, status, created_at, expires_at
                FROM profile_learning_proposal WHERE proposal_id = ?
                """, (rs, n) -> mapProposal(rs), proposalId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ProfileLearningProposal mapProposal(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProfileLearningProposal(rs.getString("proposal_id"), rs.getLong("user_id"),
                rs.getInt("profile_version"), rs.getLong("lifecycle_generation"), rs.getString("feature_code"),
                Section.valueOf(rs.getString("profile_section")), rs.getString("old_value"), rs.getString("proposed_value"),
                Operator.valueOf(rs.getString("operator_name")), Importance.valueOf(rs.getString("importance")),
                split(rs.getString("source_event_ids")), rs.getString("evidence_type"), rs.getString("evidence_status"),
                rs.getString("proposal_reason"), rs.getString("policy_version"),
                ProfileLearningProposal.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
    }

    private StoredConfirmation confirmation(String commandId) {
        List<StoredConfirmation> rows = confirmationsWhere("command_id = ?", commandId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<StoredConfirmation> confirmations(long userId) {
        return confirmationsWhere("user_id = ?", userId);
    }

    private List<StoredConfirmation> confirmationsWhere(String clause, Object key) {
        return jdbc.query("""
                SELECT command_id, user_id, feature_code, old_value, new_value, operator_name, importance,
                       profile_version, lifecycle_generation, authorization_epoch, source_event_ids, retracted,
                       created_at, evidence_origin
                FROM profile_learning_confirmation
                WHERE """ + " " + clause + " ORDER BY created_at, command_id",
                (rs, n) -> new StoredConfirmation(rs.getString("command_id"), rs.getLong("user_id"),
                        rs.getString("feature_code"), rs.getString("old_value"), rs.getString("new_value"),
                        Importance.valueOf(rs.getString("importance")), Operator.valueOf(rs.getString("operator_name")),
                        rs.getInt("profile_version"), rs.getLong("lifecycle_generation"), rs.getLong("authorization_epoch"),
                        split(rs.getString("source_event_ids")), rs.getBoolean("retracted"),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("evidence_origin")), key);
    }

    private void hold(long userId) {
        try {
            locks.userThenFeedback(userId);
        } catch (DomainException ex) {
            throw rejection(ex);
        }
    }

    private LearningPermit lock(long userId) {
        try {
            return profiles.lockLearning(userId);
        } catch (DomainException ex) {
            throw rejection(ex);
        }
    }

    private static FeedbackRejection rejection(DomainException ex) {
        if (ex.code() == ErrorCode.NOT_FOUND || ex.code() == ErrorCode.ACCOUNT_INACTIVE) {
            return new FeedbackRejection("ACCOUNT_INACTIVE");
        }
        if (ex.code() == ErrorCode.FORBIDDEN) return new FeedbackRejection("NO_FEEDBACK_CONSENT");
        return new FeedbackRejection(ex.code().name());
    }

    private static boolean sameCommand(StoredConfirmation row, ExplicitPreferenceCommand command) {
        return row.userId == command.userId()
                && row.featureCode.equals(command.featureCode())
                && Objects.equals(row.oldValue, command.oldValue())
                && row.newValue.equals(command.newValue())
                && row.operator == command.operator()
                && row.importance == command.importance()
                && row.sources.equals(command.sourceEventIds());
    }

    private static boolean sameProposal(ProfileLearningProposal proposal, ExplicitPreferenceCommand command) {
        return proposal.userId() == command.userId()
                && proposal.featureCode().equals(command.featureCode())
                && Objects.equals(proposal.oldValue(), command.oldValue())
                && proposal.proposedValue().equals(command.newValue())
                && proposal.operator() == command.operator()
                && proposal.importance() == command.importance()
                && proposal.sourceEventIds().equals(command.sourceEventIds())
                && proposal.profileVersion() == command.profileVersion()
                && proposal.lifecycleGeneration() == command.lifecycleGeneration();
    }

    private static boolean retractedSource(FeedbackLedger ledger, String eventId) {
        Set<String> superseded = new LinkedHashSet<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.supersedesEventId() != null) superseded.add(event.supersedesEventId());
        }
        return superseded.contains(eventId);
    }

    private boolean erased(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM relationship_feedback_erasure WHERE user_id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    private static String join(List<String> ids) {
        return String.join(",", ids);
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(","));
    }

    private record StoredConfirmation(String commandId, long userId, String featureCode, String oldValue, String newValue,
                                      Importance importance, Operator operator, int profileVersion, long generation,
                                      long authorizationEpoch, List<String> sources, boolean retracted, Instant createdAt,
                                      String origin) {}
}
