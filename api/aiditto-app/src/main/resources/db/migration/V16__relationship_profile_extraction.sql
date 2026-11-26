-- One extraction attempt for one input fingerprint and generation.
-- The row stores status and token counts only. It does not store the prompt, the user text, or the model context.

CREATE TABLE relationship_profile_extraction (
  user_id           BIGINT       NOT NULL,
  input_hash        CHAR(64)     NOT NULL,
  prompt_version    VARCHAR(64)  NOT NULL,
  generation_epoch  BIGINT       NOT NULL,
  status            VARCHAR(16)  NOT NULL,
  result_hash       CHAR(64)     NULL,
  attempts          INTEGER      NOT NULL,
  created_at        TIMESTAMP(3) NOT NULL,
  updated_at        TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id, input_hash, prompt_version, generation_epoch),
  CONSTRAINT ck_rel_extract_status CHECK (status IN ('running', 'completed', 'discarded', 'degraded')),
  CONSTRAINT ck_rel_extract_attempts CHECK (attempts >= 1 AND attempts <= 3)
);
