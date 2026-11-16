-- 技术方案 §4.3. Times are UTC TIMESTAMP(3); hashes are HMAC-SHA256(HASH_PEPPER, normalized value).

CREATE TABLE "user" (
  id                      BIGINT        NOT NULL,
  phone_enc               BYTEA NULL,
  phone_hash              BYTEA    NOT NULL,
  role                    VARCHAR(16)   NOT NULL DEFAULT 'user',
  status                  VARCHAR(24)   NOT NULL DEFAULT 'onboarding',
  lang                    VARCHAR(8)    NOT NULL DEFAULT 'zh',
  is_synthetic            BOOLEAN       NOT NULL DEFAULT FALSE,
  handle                  VARCHAR(32)   NULL,
  invite_code             VARCHAR(16)   NOT NULL,
  referred_by             BIGINT        NULL,
  onboarding_completed_at TIMESTAMP(3)   NULL,
  furthest_step_id        VARCHAR(48)   NULL,
  photo_issue             VARCHAR(32)   NULL,
  suspended_until         TIMESTAMP(3)   NULL,
  no_show_count           INTEGER           NOT NULL DEFAULT 0,
  empty_week_streak       INTEGER           NOT NULL DEFAULT 0,
  deactivated_at          TIMESTAMP(3)   NULL,
  delete_requested_at     TIMESTAMP(3)   NULL,
  purge_at                TIMESTAMP(3)   NULL,
  last_login_at           TIMESTAMP(3)   NULL,
  created_at              TIMESTAMP(3)   NOT NULL,
  updated_at              TIMESTAMP(3)   NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_phone_hash UNIQUE (phone_hash),
  CONSTRAINT uk_invite_code UNIQUE (invite_code),
  CONSTRAINT uk_handle UNIQUE (handle),
  CONSTRAINT ck_user_role CHECK (role IN ('user', 'admin')),
  CONSTRAINT ck_user_status CHECK (status IN ('onboarding', 'active', 'paused', 'suspended', 'deactivated', 'deleting', 'purged', 'banned')),
  CONSTRAINT ck_user_lang CHECK (lang IN ('zh', 'en'))
);
CREATE INDEX idx_status_synthetic ON "user" (status, is_synthetic);
CREATE INDEX idx_purge_at ON "user" (purge_at);

CREATE TABLE auth_code (
  id          BIGINT      NOT NULL,
  channel     VARCHAR(8)  NOT NULL,
  target_hash BYTEA  NOT NULL,
  code_hash   BYTEA  NOT NULL,
  sent_at     TIMESTAMP(3) NOT NULL,
  expires_at  TIMESTAMP(3) NOT NULL,
  attempts    INTEGER         NOT NULL DEFAULT 0,
  last_attempt_at TIMESTAMP(3) NULL,
  ip          VARCHAR(45) NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_auth_code_channel CHECK (channel IN ('sms', 'email')),
  CONSTRAINT ck_auth_code_attempts CHECK (attempts >= 0)
);
CREATE INDEX idx_target_sent ON "auth_code" (channel, target_hash, sent_at);
CREATE INDEX idx_ip_sent ON "auth_code" (ip, sent_at);

CREATE TABLE auth_session (
  id           BIGINT       NOT NULL,
  token_hash   BYTEA   NOT NULL,
  user_id      BIGINT       NOT NULL,
  created_at   TIMESTAMP(3)  NOT NULL,
  last_seen_at TIMESTAMP(3)  NOT NULL,
  expires_at   TIMESTAMP(3)  NOT NULL,
  revoked_at   TIMESTAMP(3)  NULL,
  user_agent   VARCHAR(255) NULL,
  ip           VARCHAR(45)  NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_auth_session_user ON "auth_session" (user_id, revoked_at);

CREATE TABLE email_verification (
  id          BIGINT         NOT NULL,
  user_id     BIGINT         NOT NULL,
  kind        VARCHAR(16)    NOT NULL,
  email_enc   BYTEA NOT NULL,
  email_hash  BYTEA     NOT NULL,
  domain      VARCHAR(255)   NOT NULL,
  verified_at TIMESTAMP(3)    NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_kind UNIQUE (user_id, kind),
  CONSTRAINT ck_email_verification_kind CHECK (kind IN ('school', 'personal'))
);
CREATE INDEX idx_email_hash ON "email_verification" (email_hash);
