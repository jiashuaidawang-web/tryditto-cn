-- V16 stays as committed. These columns add an export generation and one fenced attempt.
-- A row still stores status, hashes, and token counts only. It does not store prompt or user text.

ALTER TABLE relationship_profile_lifecycle
  ADD COLUMN model_export_epoch BIGINT NOT NULL DEFAULT 1;

ALTER TABLE relationship_profile_lifecycle
  ADD CONSTRAINT ck_rel_life_export_epoch CHECK (model_export_epoch >= 1);

ALTER TABLE relationship_profile_extraction
  ADD COLUMN attempt_id VARCHAR(36) NOT NULL DEFAULT '',
  ADD COLUMN fence BIGINT NOT NULL DEFAULT 1,
  ADD COLUMN lease_until TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN model_export_epoch BIGINT NOT NULL DEFAULT 1;

ALTER TABLE relationship_profile_extraction
  ADD CONSTRAINT ck_rel_extract_fence CHECK (fence >= 1),
  ADD CONSTRAINT ck_rel_extract_export_epoch CHECK (model_export_epoch >= 1);
