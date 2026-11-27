-- V19 stays as committed. A proposal is not a profile version.
-- Confirming a proposal writes relationship_profile_version and one consumption credential together.

CREATE TABLE profile_learning_proposal (
    proposal_id           VARCHAR(64)  NOT NULL,
    user_id               BIGINT       NOT NULL,
    profile_version       INTEGER      NOT NULL,
    lifecycle_generation  BIGINT       NOT NULL,
    feature_code          VARCHAR(64)  NOT NULL,
    profile_section       VARCHAR(16)  NOT NULL,
    old_value             VARCHAR(64),
    proposed_value        VARCHAR(64)  NOT NULL,
    operator_name         VARCHAR(16)  NOT NULL,
    importance            VARCHAR(16)  NOT NULL,
    source_event_ids      VARCHAR(256) NOT NULL,
    evidence_type         VARCHAR(32)  NOT NULL,
    evidence_status       VARCHAR(32)  NOT NULL,
    proposal_reason       VARCHAR(160) NOT NULL,
    policy_version        VARCHAR(32)  NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMP(3) NOT NULL,
    expires_at            TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (proposal_id),
    CONSTRAINT ck_profile_learning_proposal_status CHECK (status IN (
        'PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'RETRACTED',
        'SUPERSEDED', 'STALE_PROFILE', 'INVALID_EVIDENCE'
    ))
);

CREATE TABLE profile_learning_confirmation (
    command_id            VARCHAR(64)  NOT NULL,
    user_id               BIGINT       NOT NULL,
    feature_code          VARCHAR(64)  NOT NULL,
    old_value             VARCHAR(64),
    new_value             VARCHAR(64)  NOT NULL,
    operator_name         VARCHAR(16)  NOT NULL,
    importance            VARCHAR(16)  NOT NULL,
    profile_version       INTEGER      NOT NULL,
    lifecycle_generation  BIGINT       NOT NULL,
    authorization_epoch   BIGINT       NOT NULL,
    source_event_ids      VARCHAR(256) NOT NULL,
    retracted             BOOLEAN      NOT NULL,
    created_at            TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (command_id)
);

CREATE INDEX ix_profile_learning_confirmation_user
    ON profile_learning_confirmation (user_id, retracted);
