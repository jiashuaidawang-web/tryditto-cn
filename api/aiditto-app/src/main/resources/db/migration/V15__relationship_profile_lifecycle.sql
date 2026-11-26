-- V15: source watermark and processing authorization live outside the immutable snapshot.
-- V14 is unchanged. Matching consent is not stored here and does not grant profile processing.
-- A superseded row keeps its history and releases the live hash, so a later return to the same
-- content can be stored again. User withdrawal still uses retracted_at.

ALTER TABLE relationship_profile_version
  ADD COLUMN superseded_at TIMESTAMP(3) NULL;

DROP INDEX uq_rel_profile_active_hash;

CREATE UNIQUE INDEX uq_rel_profile_live_hash
  ON relationship_profile_version (user_id, input_hash)
  WHERE retracted_at IS NULL AND superseded_at IS NULL;

CREATE TABLE relationship_profile_lifecycle (
  user_id                          BIGINT       NOT NULL,
  latest_processed_source_version  INTEGER      NOT NULL,
  generation_epoch                 BIGINT       NOT NULL,
  processing_authorization         VARCHAR(16)  NOT NULL,
  model_export_authorization       VARCHAR(16)  NOT NULL,
  updated_at                       TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT ck_rel_life_processing CHECK (processing_authorization IN ('granted', 'revoked')),
  CONSTRAINT ck_rel_life_model CHECK (model_export_authorization IN ('granted', 'revoked')),
  CONSTRAINT ck_rel_life_source CHECK (latest_processed_source_version >= 0),
  CONSTRAINT ck_rel_life_generation CHECK (generation_epoch >= 1)
);
