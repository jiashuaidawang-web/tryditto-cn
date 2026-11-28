package cn.aiditto.feedback;

import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.strategy.MatchScoreSnapshot;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation;
import cn.aiditto.domain.feedback.v4.strategy.ObservationBuilder;
import cn.aiditto.domain.feedback.v4.strategy.ObservationWindow;
import cn.aiditto.account.User;
import cn.aiditto.account.UserLifecycleLock;
import cn.aiditto.domain.feedback.v4.strategy.StrategyLearningProposal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Stores one published observation and its group-evaluation credential together.
 * A revoked or erased row leaves the legal set. It is not restored by a later claim.
 */
@Service
public class GroupEvaluationStore {
    /**
     * Account deletion is not called from {@code AccountService} in this round.
     * {@link RelationshipFeedbackStore#eraseActor} already calls {@link #withdrawActor}.
     * {@code V4_INTEGRATION_TEST} deletion calls that path. {@code LEGACY_ONLY} does not.
     */
    public static final String FUTURE_ACCOUNT_DELETION =
            "AccountService.requestDelete locks the user, calls eraseActor, then RelationshipProfileService.erase, and commits. eraseActor withdraws that user's observations. It does not run a learner or restore an old snapshot.";

    private static final Set<FeedbackEventType> CONSUMABLE = EnumSet.of(
            FeedbackEventType.MATCH_ACCEPTED,
            FeedbackEventType.MATCH_DECLINED,
            FeedbackEventType.DATE_REPORTED_ATTENDED,
            FeedbackEventType.DATE_REPORTED_NOT_ATTENDED,
            FeedbackEventType.CONTINUE_INTEREST_EXPRESSED,
            FeedbackEventType.CONTINUE_INTEREST_DECLINED,
            FeedbackEventType.PREFERENCE_CORRECTION_SUBMITTED,
            FeedbackEventType.FEEDBACK_CORRECTED);

    private final JdbcTemplate jdbc;
    private final RelationshipFeedbackStore feedback;
    private final UserLifecycleLock locks;

    public GroupEvaluationStore(JdbcTemplate jdbc, RelationshipFeedbackStore feedback, UserLifecycleLock locks) {
        this.jdbc = jdbc;
        this.feedback = feedback;
        this.locks = locks;
    }

    @Transactional
    public void record(MatchFeedbackContext context, MatchingObservation observation, String datasetVersion,
                       String eventId, String policyVersion, Instant when) {
        if (datasetVersion == null || datasetVersion.isBlank() || policyVersion == null || policyVersion.isBlank()) {
            throw new FeedbackRejection("OBSERVATION");
        }
        if (observation.observationId().length() > 64) throw new FeedbackRejection("OBSERVATION");
        if (observation.matchId() != context.matchId() || !observation.matchingRunId().equals(context.matchingRunId())
                || observation.userA() != context.userA() || observation.userB() != context.userB()) {
            throw new FeedbackRejection("OBSERVATION");
        }
        if (observation.profileVersionA() != context.profileVersionA()
                || observation.profileVersionB() != context.profileVersionB()) {
            throw new FeedbackRejection("SNAPSHOT");
        }
        locks.pairThenMatch(context.userA(), context.userB(), context.matchId());
        rejectClosed(context.userA());
        rejectClosed(context.userB());
        try {
            jdbc.update("""
                    INSERT INTO matching_observation (
                        observation_id, match_id, matching_run_id, dataset_version,
                        user_a, user_b, profile_version_a, profile_version_b,
                        payload, withdrawn, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)
                    """, observation.observationId(), observation.matchId(), observation.matchingRunId(), datasetVersion,
                    observation.userA(), observation.userB(), observation.profileVersionA(), observation.profileVersionB(),
                    observation.canonical(), Timestamp.from(when));
        } catch (DuplicateKeyException duplicate) {
            throw new FeedbackRejection("DUPLICATE_MATCH");
        }
        feedback.claim(context, eventId, FeedbackConsumption.Consumer.GROUP_EVALUATION, policyVersion, when,
                observation.observationId());
    }

    /**
     * Builds the observation from the stored ledger. The caller's event list must be the effective set.
     * One person's consent does not authorize the other person's outcome.
     */
    @Transactional
    public MatchingObservation recordBuilt(MatchFeedbackContext context, String datasetVersion, String policyVersion,
                                           MatchScoreSnapshot scores, ObservationWindow window, String observationId,
                                           Instant observedAt, Instant now, List<String> sourceEventIds) {
        locks.pairThenMatch(context.userA(), context.userB(), context.matchId());
        rejectClosed(context.userA());
        rejectClosed(context.userB());
        FeedbackLedger ledger = feedback.load(context);
        MatchingObservation observation = ObservationBuilder.fromLedger(context, ledger, scores, window, observationId,
                observedAt, now, sourceEventIds);
        String primary = null;
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (sourceEventIds.contains(event.eventId()) && CONSUMABLE.contains(event.eventType())) {
                primary = event.eventId();
                break;
            }
        }
        if (primary == null) throw new FeedbackRejection("NOT_CONSUMABLE");
        record(context, observation, datasetVersion, primary, policyVersion, now);
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (!sourceEventIds.contains(event.eventId())) continue;
            jdbc.update("""
                    INSERT INTO matching_observation_source (observation_id, event_id, actor_user_id)
                    VALUES (?, ?, ?)
                    """, observationId, event.eventId(), event.actorUserId());
            if (CONSUMABLE.contains(event.eventType()) && !event.eventId().equals(primary)) {
                feedback.claim(context, event.eventId(), FeedbackConsumption.Consumer.GROUP_EVALUATION, policyVersion,
                        now, observationId);
            }
        }
        return observation;
    }

    @Transactional
    public void withdrawLinked(String observationId, Instant when) {
        List<String> events = jdbc.queryForList("""
                SELECT event_id FROM matching_observation_source WHERE observation_id = ?
                """, String.class, observationId);
        for (String eventId : events) feedback.revokeEvent(eventId, when);
        int updated = jdbc.update("""
                UPDATE matching_observation SET withdrawn = TRUE
                WHERE observation_id = ? AND withdrawn = FALSE
                """, observationId);
        if (updated != 1) throw new FeedbackRejection("OBSERVATION");
    }

    @Transactional
    public void saveCalibration(String runId, String datasetVersion, Instant when) {
        jdbc.update("""
                INSERT INTO group_calibration_run (run_id, dataset_version, valid, created_at)
                VALUES (?, ?, TRUE, ?)
                """, runId, datasetVersion, Timestamp.from(when));
    }

    @Transactional
    public int invalidateDataset(String datasetVersion) {
        int proposals = jdbc.update("""
                UPDATE strategy_learning_proposal SET valid = FALSE
                WHERE dataset_version = ? AND valid = TRUE
                """, datasetVersion);
        jdbc.update("""
                UPDATE group_calibration_run SET valid = FALSE
                WHERE dataset_version = ? AND valid = TRUE
                """, datasetVersion);
        return proposals;
    }

    public boolean proposalValid(String proposalId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT valid FROM strategy_learning_proposal WHERE proposal_id = ?", Boolean.class, proposalId));
    }

    @Transactional
    public void withdraw(String observationId, String eventId, Instant when) {
        feedback.revokeEvent(eventId, when);
        int updated = jdbc.update("""
                UPDATE matching_observation
                SET withdrawn = TRUE
                WHERE observation_id = ? AND withdrawn = FALSE
                """, observationId);
        if (updated != 1) throw new FeedbackRejection("OBSERVATION");
    }

    /** Drops one user's observations from the legal set and redacts the stored trace. */
    @Transactional
    public int withdrawActor(long userId, Instant when) {
        locks.userThenFeedback(userId);
        return jdbc.update("""
                UPDATE matching_observation
                SET withdrawn = TRUE, payload = 'REDACTED'
                WHERE withdrawn = FALSE AND (user_a = ? OR user_b = ?)
                """, userId, userId);
    }

    /** A missing row is a synthetic observation, not a closed account. Only a real deleting account is refused. */
    private void rejectClosed(long userId) {
        List<String> rows = jdbc.query("SELECT status FROM \"user\" WHERE id = ?", (rs, n) -> rs.getString(1), userId);
        if (!rows.isEmpty() && (User.DELETING.equals(rows.get(0)) || "purged".equals(rows.get(0)))) {
            throw new FeedbackRejection("ACCOUNT_INACTIVE");
        }
    }

    public List<String> legalPayloads(String datasetVersion) {
        return jdbc.queryForList("""
                SELECT payload FROM matching_observation
                WHERE dataset_version = ? AND withdrawn = FALSE
                ORDER BY observation_id
                """, String.class, datasetVersion);
    }

    @Transactional
    public void saveProposal(StrategyLearningProposal proposal) {
        jdbc.update("""
                INSERT INTO strategy_learning_proposal (
                    proposal_id, dataset_version, status, payload, activated, created_at
                ) VALUES (?, ?, ?, ?, FALSE, ?)
                """, proposal.proposalId(), proposal.evaluationDatasetVersion(), proposal.status().name(),
                proposal.proposedChange(), Timestamp.from(proposal.createdAt()));
    }

    public void activate(String proposalId) {
        String status = jdbc.queryForObject(
                "SELECT status FROM strategy_learning_proposal WHERE proposal_id = ?", String.class, proposalId);
        new StrategyLearningProposal(proposalId, "global-objective-g2-1", "global-objective-g2-1",
                "stored", "synthetic-published-matches", "Y1", "no weight change", "not estimated",
                "synthetic only", "no counterfactual outcomes", "hard constraints unchanged",
                "not measured on real users", StrategyLearningProposal.Status.valueOf(status), Instant.EPOCH)
                .activate();
    }
}
