-- External erasure is retryable and visible. A database commit is not a Qdrant commit.
-- PENDING and FAILED both mean the account is not fully erased.

CREATE TABLE account_erasure_task (
  user_id     BIGINT       NOT NULL,
  store_name  VARCHAR(32)  NOT NULL,
  status      VARCHAR(16)  NOT NULL,
  attempts    INTEGER      NOT NULL,
  last_error  VARCHAR(160) NULL,
  updated_at  TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id, store_name),
  CONSTRAINT ck_account_erasure_status CHECK (status IN ('PENDING', 'FAILED', 'DONE')),
  CONSTRAINT ck_account_erasure_attempts CHECK (attempts >= 0)
);
