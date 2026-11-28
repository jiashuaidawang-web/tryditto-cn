-- Isolated shadow runs. These rows are not production matches and have no publish state.

CREATE TABLE shadow_run (
    run_id                      VARCHAR(64)   NOT NULL,
    input_hash                  CHAR(64)      NOT NULL,
    result_hash                 CHAR(64)      NOT NULL,
    started_at                  TIMESTAMP(3)  NOT NULL,
    completed_at                TIMESTAMP(3)  NOT NULL,
    status                      VARCHAR(32)   NOT NULL,
    executable                  BOOLEAN       NOT NULL,
    city                        VARCHAR(16)   NOT NULL,
    cohort                      VARCHAR(32)   NOT NULL,
    matching_period             DATE          NOT NULL,
    source_cutoff               TIMESTAMP(3)  NOT NULL,
    eligible_user_count         INTEGER       NOT NULL,
    legal_edge_count            INTEGER       NOT NULL,
    recommended_edge_count      INTEGER       NOT NULL,
    matched_pair_count          INTEGER       NOT NULL,
    unmatched_user_count        INTEGER       NOT NULL,
    safety_violation_count      INTEGER       NOT NULL,
    candidate_graph_coverage    VARCHAR(32)   NOT NULL,
    scoring_version             VARCHAR(64)   NOT NULL,
    objective_version           VARCHAR(64)   NOT NULL,
    candidate_strategy_version  VARCHAR(64)   NOT NULL,
    commit_sha                  VARCHAR(64)   NOT NULL,
    execution_ms                BIGINT        NOT NULL,
    failure_reason              VARCHAR(200)  NULL,
    input_document              JSONB         NOT NULL,
    comparison_document         JSONB         NOT NULL,
    PRIMARY KEY (run_id),
    CONSTRAINT ck_shadow_run_status CHECK (status IN (
        'COMPLETE', 'REJECTED', 'ABORTED', 'EXPIRED', 'SAFETY_REJECTED', 'CONCURRENCY')),
    CONSTRAINT ck_shadow_run_executable CHECK (executable = FALSE OR status = 'COMPLETE')
);

CREATE TABLE shadow_pair (
    run_id                   VARCHAR(64)          NOT NULL,
    low_user_id              BIGINT               NOT NULL,
    high_user_id             BIGINT               NOT NULL,
    low_profile_version      BIGINT               NOT NULL,
    high_profile_version     BIGINT               NOT NULL,
    low_generation           BIGINT               NOT NULL,
    high_generation          BIGINT               NOT NULL,
    score_low_to_high        DOUBLE PRECISION     NULL,
    score_high_to_low        DOUBLE PRECISION     NULL,
    coverage_low_to_high     DOUBLE PRECISION     NULL,
    coverage_high_to_low     DOUBLE PRECISION     NULL,
    recommendation_eligible  BOOLEAN              NOT NULL,
    optimization_weight      BIGINT               NOT NULL,
    safety_status            VARCHAR(32)          NOT NULL,
    pair_status              VARCHAR(16)          NOT NULL,
    PRIMARY KEY (run_id, low_user_id, high_user_id),
    CONSTRAINT ck_shadow_pair_order CHECK (low_user_id < high_user_id),
    CONSTRAINT ck_shadow_pair_status CHECK (pair_status = 'SHADOW')
);

CREATE INDEX idx_shadow_pair_run ON shadow_pair (run_id);
