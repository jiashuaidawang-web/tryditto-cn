-- V17 stays as committed. These rows record what happened around a match.
-- They are not a second copy of the V3 feedback form, and nothing here updates taste or score.

CREATE TABLE relationship_feedback_event (
    event_seq BIGINT GENERATED ALWAYS AS IDENTITY,
    event_id VARCHAR(36) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    match_id BIGINT NOT NULL,
    matching_run_id VARCHAR(64) NOT NULL,
    matching_period DATE NOT NULL,
    actor_user_id BIGINT NOT NULL,
    counterpart_user_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    recorded_at TIMESTAMP(3) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    causation_event_id VARCHAR(120),
    supersedes_event_id VARCHAR(36),
    consent_scope VARCHAR(32) NOT NULL,
    actor_profile_version_at_match BIGINT NOT NULL,
    counterpart_profile_version_at_match BIGINT NOT NULL,
    compatibility_policy_version VARCHAR(64) NOT NULL,
    matching_objective_version VARCHAR(64) NOT NULL,
    candidate_strategy_version VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64),
    evidence_status VARCHAR(32) NOT NULL,
    learning_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMP(3),
    erased_at TIMESTAMP(3),
    CONSTRAINT uq_relationship_feedback_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_relationship_feedback_reason CHECK (
        reason_code IS NULL OR reason_code IN (
            'COMMON_INTEREST', 'COMMUNICATION_STYLE', 'LIFESTYLE_PREFERENCE', 'RELATIONSHIP_EXPECTATION',
            'SCHEDULING_DIFFICULTY', 'NO_ROMANTIC_INTEREST', 'INSUFFICIENT_INFORMATION', 'OTHER', 'PREFER_NOT_TO_SAY'
        )
    )
);

CREATE INDEX ix_relationship_feedback_match_time
    ON relationship_feedback_event (match_id, recorded_at);

CREATE INDEX ix_relationship_feedback_actor_direction_time
    ON relationship_feedback_event (actor_user_id, direction, recorded_at);

CREATE TABLE relationship_feedback_erasure (
    user_id BIGINT PRIMARY KEY,
    erased_at TIMESTAMP(3) NOT NULL
);
