-- V21 stays as committed. One row is one published match in one run.
-- A withdrawn row stays withdrawn. activated cannot become true.

CREATE TABLE matching_observation (
    observation_id     VARCHAR(64)  NOT NULL,
    match_id           BIGINT       NOT NULL,
    matching_run_id    VARCHAR(64)  NOT NULL,
    dataset_version    VARCHAR(32)  NOT NULL,
    user_a             BIGINT       NOT NULL,
    user_b             BIGINT       NOT NULL,
    profile_version_a  INTEGER      NOT NULL,
    profile_version_b  INTEGER      NOT NULL,
    payload            TEXT         NOT NULL,
    withdrawn          BOOLEAN      NOT NULL,
    created_at         TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (observation_id),
    CONSTRAINT uq_matching_observation_run UNIQUE (match_id, matching_run_id),
    CONSTRAINT ck_matching_observation_users CHECK (user_a <> user_b)
);

CREATE INDEX ix_matching_observation_legal
    ON matching_observation (dataset_version, withdrawn);

CREATE TABLE strategy_learning_proposal (
    proposal_id      VARCHAR(64)  NOT NULL,
    dataset_version  VARCHAR(32)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    payload          VARCHAR(160) NOT NULL,
    activated        BOOLEAN      NOT NULL,
    created_at       TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (proposal_id),
    CONSTRAINT ck_strategy_proposal_status CHECK (status IN (
        'INSUFFICIENT_DATA', 'EXPERIMENTAL', 'EVALUATED', 'REJECTED', 'APPROVAL_REQUIRED'
    )),
    CONSTRAINT ck_strategy_proposal_inactive CHECK (activated = FALSE)
);
