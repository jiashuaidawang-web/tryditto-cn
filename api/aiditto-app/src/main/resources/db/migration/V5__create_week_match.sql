-- M2: weekly batches, matches, feedback, reports. V1–V4 are unchanged.

ALTER TABLE "user"
  ADD COLUMN cohort VARCHAR(16) NOT NULL DEFAULT 'real',
  ADD COLUMN late_cancel_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN no_response_streak INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN dispute_count INTEGER NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_user_cohort CHECK (cohort IN ('real', 'demo', 'synthetic'));

CREATE TABLE week (
  id            BIGINT       NOT NULL,
  pool          VARCHAR(32)  NOT NULL,
  week_start    DATE         NOT NULL,
  trigger_kind  VARCHAR(16)  NOT NULL,
  run_no        INTEGER          NOT NULL,
  state         VARCHAR(16)  NOT NULL,
  failed_step   VARCHAR(16)  NULL,
  snapshot_hash CHAR(64)     NULL,
  stats         JSONB         NULL,
  error         VARCHAR(2000) NULL,
  publish_at    TIMESTAMP(3)  NULL,
  locked_at     TIMESTAMP(3)  NULL,
  solved_at     TIMESTAMP(3)  NULL,
  copied_at     TIMESTAMP(3)  NULL,
  published_at  TIMESTAMP(3)  NULL,
  closed_at     TIMESTAMP(3)  NULL,
  created_at    TIMESTAMP(3)  NOT NULL,
  updated_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_pool_week_run UNIQUE (pool, week_start, trigger_kind, run_no),
  CONSTRAINT ck_week_trigger CHECK (trigger_kind IN ('scheduled', 'manual', 'demo')),
  CONSTRAINT ck_week_state CHECK (state IN ('open', 'locked', 'solved', 'copied', 'published', 'closed', 'failed'))
);
CREATE INDEX idx_week_state ON "week" (state);

CREATE TABLE week_participant (
  week_id         BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  profile_version INTEGER          NOT NULL,
  status          VARCHAR(16)  NOT NULL,
  exclude_reason  VARCHAR(32)  NULL,
  PRIMARY KEY (week_id, user_id),
  CONSTRAINT ck_participant_status CHECK (status IN ('in', 'excluded'))
);
CREATE INDEX idx_participant_user ON "week_participant" (user_id);

CREATE TABLE "match" (
  id              BIGINT       NOT NULL,
  week_id         BIGINT       NOT NULL,
  user_low        BIGINT       NOT NULL,
  user_high       BIGINT       NOT NULL,
  score           INTEGER          NOT NULL,
  score_json      JSONB         NULL,
  state           VARCHAR(24)  NOT NULL,
  outcome         VARCHAR(24)  NULL,
  published_at    TIMESTAMP(3)  NULL,
  respond_due     TIMESTAMP(3)  NULL,
  reselect_due    TIMESTAMP(3)  NULL,
  slot_start      TIMESTAMP(3)  NULL,
  slot_end        TIMESTAMP(3)  NULL,
  feedback_due    TIMESTAMP(3)  NULL,
  closed_at       TIMESTAMP(3)  NULL,
  reporter_id     BIGINT       NULL,
  cancelled_by    BIGINT       NULL,
  version         INTEGER          NOT NULL DEFAULT 0,
  created_at      TIMESTAMP(3)  NOT NULL,
  updated_at      TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_week_pair UNIQUE (week_id, user_low, user_high),
  CONSTRAINT ck_match_order CHECK (user_low < user_high),
  CONSTRAINT ck_match_state CHECK (state IN (
    'draft', 'published', 'declined', 'no_overlap', 'expired', 'unscheduled',
    'confirmed', 'cancelled', 'feedback_open', 'closed', 'halted'))
);
CREATE INDEX idx_state_due ON "match" (state, respond_due);
CREATE INDEX idx_state_slot ON "match" (state, slot_end);
CREATE INDEX idx_user_low ON "match" (user_low);
CREATE INDEX idx_user_high ON "match" (user_high);

CREATE TABLE match_side (
  match_id          BIGINT       NOT NULL,
  user_id           BIGINT       NOT NULL,
  week_id           BIGINT       NOT NULL,
  response          VARCHAR(16)  NOT NULL,
  accept_clicked_at TIMESTAMP(3)  NULL,
  slot_ids          JSONB         NULL,
  reselected        BOOLEAN      NOT NULL DEFAULT FALSE,
  decline_reasons   JSONB         NULL,
  decline_note      VARCHAR(500) NULL,
  flagged_reasons   JSONB         NULL,
  checked_in_at     TIMESTAMP(3)  NULL,
  responded_at      TIMESTAMP(3)  NULL,
  PRIMARY KEY (match_id, user_id),
  CONSTRAINT uk_week_user UNIQUE (week_id, user_id),
  CONSTRAINT ck_side_response CHECK (response IN ('pending', 'accept', 'decline'))
);

CREATE TABLE date_plan (
  match_id          BIGINT        NOT NULL,
  slots             JSONB          NOT NULL,
  venue_json        JSONB          NOT NULL,
  confirmed_slot_id VARCHAR(32)   NULL,
  activity          VARCHAR(200)  NULL,
  extra             VARCHAR(400)  NULL,
  PRIMARY KEY (match_id)
);

CREATE TABLE match_copy (
  match_id       BIGINT       NOT NULL,
  viewer_id      BIGINT       NOT NULL,
  lang           VARCHAR(8)   NOT NULL,
  headline       VARCHAR(200) NOT NULL,
  blurb          JSONB         NOT NULL,
  reasons        JSONB         NOT NULL,
  openers        JSONB         NOT NULL,
  source         VARCHAR(16)  NOT NULL,
  prompt_version VARCHAR(32)  NULL,
  PRIMARY KEY (match_id, viewer_id),
  CONSTRAINT ck_copy_source CHECK (source IN ('ai', 'template')),
  CONSTRAINT ck_copy_lang CHECK (lang IN ('zh', 'en'))
);

CREATE TABLE feedback (
  match_id    BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL,
  met         VARCHAR(16)   NOT NULL,
  rating      INTEGER           NULL,
  again       VARCHAR(16)   NULL,
  comment_enc BYTEA NULL,
  felt_safe   BOOLEAN       NULL,
  created_at  TIMESTAMP(3)   NOT NULL,
  PRIMARY KEY (match_id, user_id),
  CONSTRAINT ck_feedback_met CHECK (met IN ('met', 'noShow', 'iSkipped')),
  CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),
  CONSTRAINT ck_feedback_again CHECK (again IS NULL OR again IN ('yes', 'no', 'unsure'))
);

CREATE TABLE report (
  id          BIGINT        NOT NULL,
  reporter_id BIGINT        NOT NULL,
  reported_id BIGINT        NOT NULL,
  match_id    BIGINT        NOT NULL,
  category    VARCHAR(32)   NOT NULL,
  note_enc    BYTEA NULL,
  severity    VARCHAR(16)   NOT NULL,
  state       VARCHAR(16)   NOT NULL,
  sla_due_at  TIMESTAMP(3)   NOT NULL,
  created_at  TIMESTAMP(3)   NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_report_severity CHECK (severity IN ('urgent', 'normal')),
  CONSTRAINT ck_report_state CHECK (state IN ('open', 'reviewing', 'actioned', 'dismissed'))
);
CREATE INDEX idx_state_sla ON "report" (state, sla_due_at);
CREATE INDEX idx_report_pair ON "report" (reporter_id, reported_id);
CREATE INDEX idx_report_match ON "report" (match_id);

CREATE TABLE match_demo (
  match_id            BIGINT  NOT NULL,
  partner_wants_again BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (match_id)
);

CREATE TABLE job_lock (
  name         VARCHAR(64) NOT NULL,
  owner        VARCHAR(64) NOT NULL,
  locked_until TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (name)
);

CREATE TABLE admin_audit (
  id          BIGINT       NOT NULL,
  admin_id    BIGINT       NOT NULL,
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(32)  NULL,
  target_id   VARCHAR(64)  NULL,
  payload     JSONB         NULL,
  ip          VARCHAR(45)  NULL,
  created_at  TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_audit_created ON "admin_audit" (created_at);
CREATE INDEX idx_audit_target ON "admin_audit" (target_type, target_id);
