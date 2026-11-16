-- 技术方案 §4.3. Times are UTC DATETIME(3); hashes are HMAC-SHA256(HASH_PEPPER, normalized value).

CREATE TABLE `user` (
  id                      BIGINT        NOT NULL,
  phone_enc               VARBINARY(64) NULL,
  phone_hash              BINARY(32)    NOT NULL,
  role                    VARCHAR(16)   NOT NULL DEFAULT 'user',
  status                  VARCHAR(24)   NOT NULL DEFAULT 'onboarding',
  lang                    VARCHAR(8)    NOT NULL DEFAULT 'zh',
  is_synthetic            BOOLEAN       NOT NULL DEFAULT 0,
  handle                  VARCHAR(32)   NULL,
  invite_code             VARCHAR(16)   NOT NULL,
  referred_by             BIGINT        NULL,
  onboarding_completed_at DATETIME(3)   NULL,
  furthest_step_id        VARCHAR(48)   NULL,
  photo_issue             VARCHAR(32)   NULL,
  suspended_until         DATETIME(3)   NULL,
  no_show_count           INT           NOT NULL DEFAULT 0,
  empty_week_streak       INT           NOT NULL DEFAULT 0,
  deactivated_at          DATETIME(3)   NULL,
  delete_requested_at     DATETIME(3)   NULL,
  purge_at                DATETIME(3)   NULL,
  last_login_at           DATETIME(3)   NULL,
  created_at              DATETIME(3)   NOT NULL,
  updated_at              DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone_hash (phone_hash),
  UNIQUE KEY uk_invite_code (invite_code),
  UNIQUE KEY uk_handle (handle),
  KEY idx_status_synthetic (status, is_synthetic),
  KEY idx_purge_at (purge_at),
  CONSTRAINT ck_user_role CHECK (role IN ('user', 'admin')),
  CONSTRAINT ck_user_status CHECK (status IN ('onboarding', 'active', 'paused', 'suspended', 'deactivated', 'deleting', 'purged', 'banned')),
  CONSTRAINT ck_user_lang CHECK (lang IN ('zh', 'en'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_code (
  id          BIGINT      NOT NULL,
  channel     VARCHAR(8)  NOT NULL,
  target_hash BINARY(32)  NOT NULL,
  code_hash   BINARY(32)  NOT NULL,
  sent_at     DATETIME(3) NOT NULL,
  expires_at  DATETIME(3) NOT NULL,
  attempts    INT         NOT NULL DEFAULT 0,
  last_attempt_at DATETIME(3) NULL,
  ip          VARCHAR(45) NULL,
  PRIMARY KEY (id),
  KEY idx_target_sent (channel, target_hash, sent_at),
  KEY idx_ip_sent (ip, sent_at),
  CONSTRAINT ck_auth_code_channel CHECK (channel IN ('sms', 'email')),
  CONSTRAINT ck_auth_code_attempts CHECK (attempts >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
  id           BIGINT       NOT NULL,
  token_hash   BINARY(32)   NOT NULL,
  user_id      BIGINT       NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  last_seen_at DATETIME(3)  NOT NULL,
  expires_at   DATETIME(3)  NOT NULL,
  revoked_at   DATETIME(3)  NULL,
  user_agent   VARCHAR(255) NULL,
  ip           VARCHAR(45)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_token_hash (token_hash),
  KEY idx_user (user_id, revoked_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_verification (
  id          BIGINT         NOT NULL,
  user_id     BIGINT         NOT NULL,
  kind        VARCHAR(16)    NOT NULL,
  email_enc   VARBINARY(512) NOT NULL,
  email_hash  BINARY(32)     NOT NULL,
  domain      VARCHAR(255)   NOT NULL,
  verified_at DATETIME(3)    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_kind (user_id, kind),
  KEY idx_email_hash (email_hash),
  CONSTRAINT ck_email_verification_kind CHECK (kind IN ('school', 'personal'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
