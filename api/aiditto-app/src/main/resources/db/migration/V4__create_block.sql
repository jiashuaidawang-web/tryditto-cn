-- 技术方案 §4.7 "block" / "block_contact", pulled forward from the doc's V5 because the blocklist ships in M1.
-- M2 continues with V5 (week/match), V6 (feedback/report), V7 (ops).

CREATE TABLE "block" (
  id         BIGINT          NOT NULL,
  user_id    BIGINT          NOT NULL,
  name_enc   BYTEA  NULL,
  emails_enc BYTEA NULL,
  phones_enc BYTEA NULL,
  note_enc   BYTEA NULL,
  created_at TIMESTAMP(3)     NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_block_user ON "block" (user_id);

CREATE TABLE block_contact (
  block_id   BIGINT     NOT NULL,
  user_id    BIGINT     NOT NULL,
  kind       VARCHAR(8) NOT NULL,
  value_hash BYTEA NOT NULL,
  PRIMARY KEY (block_id, kind, value_hash),
  CONSTRAINT ck_block_contact_kind CHECK (kind IN ('email', 'phone'))
);
CREATE INDEX idx_user_hash ON "block_contact" (user_id, value_hash);
