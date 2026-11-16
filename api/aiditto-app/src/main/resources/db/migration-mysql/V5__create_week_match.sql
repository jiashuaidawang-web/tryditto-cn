-- M2: weekly batches, matches, feedback, reports. V1–V4 are unchanged.

ALTER TABLE `user`
  ADD COLUMN cohort VARCHAR(16) NOT NULL DEFAULT 'real' AFTER is_synthetic,
  ADD COLUMN late_cancel_count INT NOT NULL DEFAULT 0 AFTER no_show_count,
  ADD COLUMN no_response_streak INT NOT NULL DEFAULT 0 AFTER late_cancel_count,
  ADD COLUMN dispute_count INT NOT NULL DEFAULT 0 AFTER no_response_streak,
  ADD CONSTRAINT ck_user_cohort CHECK (cohort IN ('real', 'demo', 'synthetic'));

CREATE TABLE week (
  id            BIGINT       NOT NULL,
  pool          VARCHAR(32)  NOT NULL,
  week_start    DATE         NOT NULL,
  trigger_kind  VARCHAR(16)  NOT NULL,
  run_no        INT          NOT NULL,
  state         VARCHAR(16)  NOT NULL,
  failed_step   VARCHAR(16)  NULL,
  snapshot_hash CHAR(64)     NULL,
  stats         JSON         NULL,
  error         VARCHAR(2000) NULL,
  publish_at    DATETIME(3)  NULL,
  locked_at     DATETIME(3)  NULL,
  solved_at     DATETIME(3)  NULL,
  copied_at     DATETIME(3)  NULL,
  published_at  DATETIME(3)  NULL,
  closed_at     DATETIME(3)  NULL,
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pool_week_run (pool, week_start, trigger_kind, run_no),
  KEY idx_week_state (state),
  CONSTRAINT ck_week_trigger CHECK (trigger_kind IN ('scheduled', 'manual', 'demo')),
  CONSTRAINT ck_week_state CHECK (state IN ('open', 'locked', 'solved', 'copied', 'published', 'closed', 'failed'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE week_participant (
  week_id         BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  profile_version INT          NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  exclude_reason  VARCHAR(32)  NULL,
  PRIMARY KEY (week_id, user_id),
  KEY idx_participant_user (user_id),
  CONSTRAINT ck_participant_status CHECK (status IN ('in', 'excluded'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `match` (
  id              BIGINT       NOT NULL,
  week_id         BIGINT       NOT NULL,
  user_low        BIGINT       NOT NULL,
  user_high       BIGINT       NOT NULL,
  score           INT          NOT NULL,
  score_json      JSON         NULL,
  state           VARCHAR(24)  NOT NULL,
  outcome         VARCHAR(24)  NULL,
  published_at    DATETIME(3)  NULL,
  respond_due     DATETIME(3)  NULL,
  reselect_due    DATETIME(3)  NULL,
  slot_start      DATETIME(3)  NULL,
  slot_end        DATETIME(3)  NULL,
  feedback_due    DATETIME(3)  NULL,
  closed_at       DATETIME(3)  NULL,
  reporter_id     BIGINT       NULL,
  cancelled_by    BIGINT       NULL,
  version         INT          NOT NULL DEFAULT 0,
  created_at      DATETIME(3)  NOT NULL,
  updated_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_week_pair (week_id, user_low, user_high),
  KEY idx_state_due (state, respond_due),
  KEY idx_state_slot (state, slot_end),
  KEY idx_user_low (user_low),
  KEY idx_user_high (user_high),
  CONSTRAINT ck_match_order CHECK (user_low < user_high),
  CONSTRAINT ck_match_state CHECK (state IN (
    'draft', 'published', 'declined', 'no_overlap', 'expired', 'unscheduled',
    'confirmed', 'cancelled', 'feedback_open', 'closed', 'halted'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE match_side (
  match_id          BIGINT       NOT NULL,
  user_id           BIGINT       NOT NULL,
  week_id           BIGINT       NOT NULL,
  response          VARCHAR(16)  NOT NULL,
  accept_clicked_at DATETIME(3)  NULL,
  slot_ids          JSON         NULL,
  reselected        BOOLEAN      NOT NULL DEFAULT 0,
  decline_reasons   JSON         NULL,
  decline_note      VARCHAR(500) NULL,
  flagged_reasons   JSON         NULL,
  checked_in_at     DATETIME(3)  NULL,
  responded_at      DATETIME(3)  NULL,
  PRIMARY KEY (match_id, user_id),
  UNIQUE KEY uk_week_user (week_id, user_id),
  CONSTRAINT ck_side_response CHECK (response IN ('pending', 'accept', 'decline'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE date_plan (
  match_id          BIGINT        NOT NULL,
  slots             JSON          NOT NULL,
  venue_json        JSON          NOT NULL,
  confirmed_slot_id VARCHAR(32)   NULL,
  activity          VARCHAR(200)  NULL,
  extra             VARCHAR(400)  NULL,
  PRIMARY KEY (match_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE match_copy (
  match_id       BIGINT       NOT NULL,
  viewer_id      BIGINT       NOT NULL,
  lang           VARCHAR(8)   NOT NULL,
  headline       VARCHAR(200) NOT NULL,
  blurb          JSON         NOT NULL,
  reasons        JSON         NOT NULL,
  openers        JSON         NOT NULL,
  source         VARCHAR(16)  NOT NULL,
  prompt_version VARCHAR(32)  NULL,
  PRIMARY KEY (match_id, viewer_id),
  CONSTRAINT ck_copy_source CHECK (source IN ('ai', 'template')),
  CONSTRAINT ck_copy_lang CHECK (lang IN ('zh', 'en'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE feedback (
  match_id    BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL,
  met         VARCHAR(16)   NOT NULL,
  rating      INT           NULL,
  again       VARCHAR(16)   NULL,
  comment_enc VARBINARY(2048) NULL,
  felt_safe   BOOLEAN       NULL,
  created_at  DATETIME(3)   NOT NULL,
  PRIMARY KEY (match_id, user_id),
  CONSTRAINT ck_feedback_met CHECK (met IN ('met', 'noShow', 'iSkipped')),
  CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),
  CONSTRAINT ck_feedback_again CHECK (again IS NULL OR again IN ('yes', 'no', 'unsure'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE report (
  id          BIGINT        NOT NULL,
  reporter_id BIGINT        NOT NULL,
  reported_id BIGINT        NOT NULL,
  match_id    BIGINT        NOT NULL,
  category    VARCHAR(32)   NOT NULL,
  note_enc    VARBINARY(2048) NULL,
  severity    VARCHAR(16)   NOT NULL,
  state       VARCHAR(16)   NOT NULL,
  sla_due_at  DATETIME(3)   NOT NULL,
  created_at  DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_state_sla (state, sla_due_at),
  KEY idx_report_pair (reporter_id, reported_id),
  KEY idx_report_match (match_id),
  CONSTRAINT ck_report_severity CHECK (severity IN ('urgent', 'normal')),
  CONSTRAINT ck_report_state CHECK (state IN ('open', 'reviewing', 'actioned', 'dismissed'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE match_demo (
  match_id            BIGINT  NOT NULL,
  partner_wants_again BOOLEAN NOT NULL DEFAULT 1,
  PRIMARY KEY (match_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE job_lock (
  name         VARCHAR(64) NOT NULL,
  owner        VARCHAR(64) NOT NULL,
  locked_until DATETIME(3) NOT NULL,
  PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE admin_audit (
  id          BIGINT       NOT NULL,
  admin_id    BIGINT       NOT NULL,
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(32)  NULL,
  target_id   VARCHAR(64)  NULL,
  payload     JSON         NULL,
  ip          VARCHAR(45)  NULL,
  created_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
