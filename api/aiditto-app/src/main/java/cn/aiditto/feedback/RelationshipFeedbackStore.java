package cn.aiditto.feedback;

import cn.aiditto.domain.feedback.v4.FeedbackCommand;
import cn.aiditto.domain.feedback.v4.FeedbackFacts;
import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackEventType;
import cn.aiditto.domain.feedback.v4.MatchFeedbackContext;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Stores feedback facts for the isolated tests. Account deletion does not call this yet.
 * Profile versions are copied from the server context inside the ledger, not from the client.
 */
@Service
public class RelationshipFeedbackStore {
    private final JdbcTemplate jdbc;

    public RelationshipFeedbackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public RelationshipFeedbackEvent append(MatchFeedbackContext context, FeedbackCommand command, Instant recordedAt) {
        lock(context.matchId());
        FeedbackLedger ledger = loadLocked(context);
        int before = ledger.events().size();
        RelationshipFeedbackEvent event = ledger.append(command, recordedAt);
        List<RelationshipFeedbackEvent> events = ledger.events();
        for (int i = before; i < events.size(); i++) insert(events.get(i));
        return event;
    }

    public FeedbackLedger load(MatchFeedbackContext context) {
        return loadLocked(context);
    }

    @Transactional
    public boolean markConsumed(String eventId, Instant consumedAt) {
        int updated = jdbc.update("""
                UPDATE relationship_feedback_event
                SET consumed_at = ?
                WHERE event_id = ? AND consumed_at IS NULL AND learning_blocked = FALSE
                """, Timestamp.from(consumedAt), eventId);
        return updated == 1;
    }

    @Transactional
    public void eraseActor(long userId, Instant erasedAt) {
        Timestamp when = Timestamp.from(erasedAt);
        jdbc.update("""
                INSERT INTO relationship_feedback_erasure (user_id, erased_at)
                VALUES (?, ?)
                ON CONFLICT (user_id) DO NOTHING
                """, userId, when);
        jdbc.update("DELETE FROM relationship_feedback_event WHERE actor_user_id = ?", userId);
        jdbc.update("""
                UPDATE relationship_feedback_event
                SET learning_blocked = TRUE, reason_code = NULL, erased_at = ?
                WHERE counterpart_user_id = ?
                """, when, userId);
    }

    private void lock(long matchId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", matchId);
    }

    private FeedbackLedger loadLocked(MatchFeedbackContext context) {
        boolean erased = count("SELECT COUNT(*) FROM relationship_feedback_erasure WHERE user_id IN (?, ?)",
                context.userA(), context.userB()) > 0;
        List<StoredEvent> rows = jdbc.query("""
                SELECT event_id, schema_version, event_type, match_id, matching_run_id, matching_period,
                       actor_user_id, counterpart_user_id, direction, source_type, occurred_at, recorded_at,
                       idempotency_key, causation_event_id, supersedes_event_id, consent_scope,
                       actor_profile_version_at_match, counterpart_profile_version_at_match,
                       compatibility_policy_version, matching_objective_version, candidate_strategy_version,
                       reason_code, evidence_status, learning_blocked, consumed_at
                FROM relationship_feedback_event
                WHERE match_id = ?
                ORDER BY event_seq
                """, (rs, row) -> new StoredEvent(
                rs.getString("event_id"),
                rs.getString("schema_version"),
                FeedbackEventType.valueOf(rs.getString("event_type")),
                rs.getLong("match_id"),
                rs.getString("matching_run_id"),
                rs.getDate("matching_period").toLocalDate(),
                rs.getLong("actor_user_id"),
                rs.getLong("counterpart_user_id"),
                FeedbackFacts.Direction.valueOf(rs.getString("direction")),
                FeedbackFacts.SourceType.valueOf(rs.getString("source_type")),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("idempotency_key"),
                rs.getString("causation_event_id"),
                rs.getString("supersedes_event_id"),
                rs.getString("consent_scope"),
                rs.getLong("actor_profile_version_at_match"),
                rs.getLong("counterpart_profile_version_at_match"),
                rs.getString("compatibility_policy_version"),
                rs.getString("matching_objective_version"),
                rs.getString("candidate_strategy_version"),
                rs.getString("reason_code"),
                FeedbackFacts.EvidenceStatus.valueOf(rs.getString("evidence_status")),
                rs.getBoolean("learning_blocked"),
                rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()
        ), context.matchId());
        boolean blocked = erased || rows.stream().anyMatch(StoredEvent::learningBlocked);
        FeedbackLedger ledger = FeedbackLedger.replay(context, rows.stream().map(StoredEvent::event).toList(), blocked);
        for (StoredEvent row : rows) {
            if (row.consumedAt() != null) ledger.restoreConsumed(row.event().eventId());
        }
        return ledger;
    }

    private void insert(RelationshipFeedbackEvent event) {
        jdbc.update("""
                INSERT INTO relationship_feedback_event (
                    event_id, schema_version, event_type, match_id, matching_run_id, matching_period,
                    actor_user_id, counterpart_user_id, direction, source_type, occurred_at, recorded_at,
                    idempotency_key, causation_event_id, supersedes_event_id, consent_scope,
                    actor_profile_version_at_match, counterpart_profile_version_at_match,
                    compatibility_policy_version, matching_objective_version, candidate_strategy_version,
                    reason_code, evidence_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.eventId(), event.schemaVersion(), event.eventType().name(), event.matchId(),
                event.matchingRunId(), Date.valueOf(event.matchingPeriod()), event.actorUserId(),
                event.counterpartUserId(), event.direction().name(), event.sourceType().name(),
                Timestamp.from(event.occurredAt()), Timestamp.from(event.recordedAt()), event.idempotencyKey(),
                event.causationEventId(), event.supersedesEventId(), event.consentScope(),
                event.actorProfileVersionAtMatch(), event.counterpartProfileVersionAtMatch(),
                event.compatibilityPolicyVersion(), event.matchingObjectiveVersion(), event.candidateStrategyVersion(),
                event.reasonCode(), event.evidenceStatus().name());
    }

    private int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private record StoredEvent(String eventId, String schemaVersion, FeedbackEventType eventType, long matchId,
                               String matchingRunId, LocalDate matchingPeriod, long actorUserId, long counterpartUserId,
                               FeedbackFacts.Direction direction, FeedbackFacts.SourceType sourceType, Instant occurredAt,
                               Instant recordedAt, String idempotencyKey, String causationEventId, String supersedesEventId,
                               String consentScope, long actorProfileVersionAtMatch, long counterpartProfileVersionAtMatch,
                               String compatibilityPolicyVersion, String matchingObjectiveVersion, String candidateStrategyVersion,
                               String reasonCode, FeedbackFacts.EvidenceStatus evidenceStatus, boolean learningBlocked,
                               Instant consumedAt) {
        RelationshipFeedbackEvent event() {
            return new RelationshipFeedbackEvent(eventId, schemaVersion, eventType, matchId, matchingRunId, matchingPeriod,
                    actorUserId, counterpartUserId, direction, sourceType, occurredAt, recordedAt, idempotencyKey,
                    causationEventId, supersedesEventId, consentScope, actorProfileVersionAtMatch,
                    counterpartProfileVersionAtMatch, compatibilityPolicyVersion, matchingObjectiveVersion,
                    candidateStrategyVersion, reasonCode, evidenceStatus);
        }
    }
}
