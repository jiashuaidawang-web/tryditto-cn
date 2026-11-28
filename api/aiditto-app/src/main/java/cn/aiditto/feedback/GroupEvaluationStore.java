package cn.aiditto.feedback;

import cn.aiditto.domain.feedback.v4.FeedbackConsumption;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation;
import cn.aiditto.domain.feedback.v4.strategy.StrategyLearningProposal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Stores one published observation and its group-evaluation credential together.
 * A revoked or erased row leaves the legal set. It is not restored by a later claim.
 */
@Service
public class GroupEvaluationStore {
    /**
     * Account deletion is not called from {@code AccountService} in this round.
     * {@link RelationshipFeedbackStore#eraseActor} already calls {@link #withdrawActor}.
     */
    public static final String FUTURE_ACCOUNT_DELETION =
            "AccountService.requestDelete locks the user, calls eraseActor, then RelationshipProfileService.erase, and commits. eraseActor withdraws that user's observations. It does not run a learner or restore an old snapshot.";

    private final JdbcTemplate jdbc;
    private final RelationshipFeedbackStore feedback;

    public GroupEvaluationStore(JdbcTemplate jdbc, RelationshipFeedbackStore feedback) {
        this.jdbc = jdbc;
        this.feedback = feedback;
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
        jdbc.queryForList("SELECT pg_advisory_xact_lock(11, ?::int)", (int) userId);
        return jdbc.update("""
                UPDATE matching_observation
                SET withdrawn = TRUE, payload = 'REDACTED'
                WHERE withdrawn = FALSE AND (user_a = ? OR user_b = ?)
                """, userId, userId);
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
