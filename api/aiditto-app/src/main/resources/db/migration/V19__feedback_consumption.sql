-- V18 stays as committed. One row is one learning use of one event.
-- A false learning_blocked flag is not a consumption permit. Group evaluation is not implemented.

CREATE TABLE relationship_feedback_consumption (
    event_id VARCHAR(36) NOT NULL,
    consumer_type VARCHAR(32) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    lifecycle_generation BIGINT NOT NULL,
    authorization_epoch BIGINT NOT NULL,
    processing_status VARCHAR(16) NOT NULL,
    consumed_at TIMESTAMP(3) NOT NULL,
    derived_artifact_id VARCHAR(64),
    PRIMARY KEY (event_id, consumer_type, policy_version),
    CONSTRAINT ck_feedback_consumption_status CHECK (
        processing_status IN ('PENDING', 'APPLIED', 'FAILED', 'REVOKED')
    ),
    CONSTRAINT ck_feedback_consumption_consumer CHECK (
        consumer_type IN ('PROFILE_LEARNING', 'GROUP_EVALUATION')
    )
);

CREATE INDEX ix_feedback_consumption_status
    ON relationship_feedback_consumption (consumer_type, processing_status);
