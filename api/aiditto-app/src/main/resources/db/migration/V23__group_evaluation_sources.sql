-- V22 stays as committed. A source row links one observation to one event.
-- Changing the dataset version marks older calibration rows and proposals invalid.

ALTER TABLE strategy_learning_proposal
    ADD COLUMN valid BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE matching_observation_source (
    observation_id VARCHAR(64) NOT NULL,
    event_id       VARCHAR(36) NOT NULL,
    actor_user_id  BIGINT      NOT NULL,
    PRIMARY KEY (observation_id, event_id)
);

CREATE TABLE group_calibration_run (
    run_id          VARCHAR(64)  NOT NULL,
    dataset_version VARCHAR(32)  NOT NULL,
    valid           BOOLEAN      NOT NULL,
    created_at      TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (run_id)
);
