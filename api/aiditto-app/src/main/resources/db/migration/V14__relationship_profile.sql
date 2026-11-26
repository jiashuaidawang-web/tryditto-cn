-- V14: versioned relationship profiles. Parallel to profile and user_taste.
-- Matching does not read this table. Existing columns are not dropped or renamed.
-- Snapshots are immutable. One current row per user. One live copy of each input hash.

CREATE TABLE relationship_profile_version (
  user_id                    BIGINT       NOT NULL,
  version                    INTEGER      NOT NULL,
  schema_version             INTEGER      NOT NULL,
  feature_registry_version   VARCHAR(32)  NOT NULL,
  source_profile_version     INTEGER      NOT NULL,
  input_hash                 CHAR(64)     NOT NULL,
  self_profile               JSONB        NOT NULL,
  preference_profile         JSONB        NOT NULL,
  behavior_profile           JSONB        NOT NULL,
  memory_profile             JSONB        NOT NULL,
  evidence                   JSONB        NOT NULL,
  created_at                 TIMESTAMP(3) NOT NULL,
  is_current                 BOOLEAN      NOT NULL,
  retracted_at               TIMESTAMP(3) NULL,
  PRIMARY KEY (user_id, version),
  CONSTRAINT ck_rel_profile_version CHECK (version >= 1),
  CONSTRAINT ck_rel_profile_schema CHECK (schema_version >= 1),
  CONSTRAINT ck_rel_profile_source CHECK (source_profile_version >= 0),
  CONSTRAINT ck_rel_profile_hash CHECK (input_hash ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_rel_profile_current
  ON relationship_profile_version (user_id) WHERE is_current;

CREATE UNIQUE INDEX uq_rel_profile_active_hash
  ON relationship_profile_version (user_id, input_hash) WHERE retracted_at IS NULL;
