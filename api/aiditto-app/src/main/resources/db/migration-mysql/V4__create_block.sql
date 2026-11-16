-- 技术方案 §4.7 `block` / `block_contact`, pulled forward from the doc's V5 because the blocklist ships in M1.
-- M2 continues with V5 (week/match), V6 (feedback/report), V7 (ops).

CREATE TABLE `block` (
  id         BIGINT          NOT NULL,
  user_id    BIGINT          NOT NULL,
  name_enc   VARBINARY(512)  NULL,
  emails_enc VARBINARY(4096) NULL,
  phones_enc VARBINARY(4096) NULL,
  note_enc   VARBINARY(2048) NULL,
  created_at DATETIME(3)     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE block_contact (
  block_id   BIGINT     NOT NULL,
  user_id    BIGINT     NOT NULL,
  kind       VARCHAR(8) NOT NULL,
  value_hash BINARY(32) NOT NULL,
  PRIMARY KEY (block_id, kind, value_hash),
  KEY idx_user_hash (user_id, value_hash),
  CONSTRAINT ck_block_contact_kind CHECK (kind IN ('email', 'phone'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
