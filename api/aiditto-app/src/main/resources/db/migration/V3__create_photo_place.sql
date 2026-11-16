-- 技术方案 §4.5. Rows of "place" come from R__seed_places.sql (generated from the frontend).

CREATE TABLE photo (
  id           BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  kind         VARCHAR(16)  NOT NULL,
  position     INTEGER          NOT NULL DEFAULT 0,
  status       VARCHAR(16)  NOT NULL DEFAULT 'pending',
  storage_key  VARCHAR(128) NULL,
  content_type VARCHAR(32)  NULL,
  width        INTEGER          NULL,
  height       INTEGER          NULL,
  bytes        INTEGER          NULL,
  sha256       BYTEA   NULL,
  moderation   VARCHAR(16)  NOT NULL DEFAULT 'skipped',
  created_at   TIMESTAMP(3)  NOT NULL,
  deleted_at   TIMESTAMP(3)  NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_photo_kind CHECK (kind IN ('profile', 'ideal')),
  CONSTRAINT ck_photo_status CHECK (status IN ('pending', 'active', 'deleted')),
  CONSTRAINT ck_photo_moderation CHECK (moderation IN ('skipped', 'pass', 'reject'))
);
CREATE INDEX idx_user_kind ON "photo" (user_id, kind, status, position);

CREATE TABLE place (
  id                  VARCHAR(32)  NOT NULL,
  kind                VARCHAR(8)   NOT NULL,
  city                VARCHAR(8)   NOT NULL,
  name_zh             VARCHAR(64)  NOT NULL,
  name_en             VARCHAR(128) NOT NULL,
  sub_zh              VARCHAR(64)  NULL,
  sub_en              VARCHAR(128) NULL,
  kw                  VARCHAR(255) NOT NULL DEFAULT '',
  nearby              JSONB         NULL,
  density             DECIMAL(6, 2) NOT NULL DEFAULT 0,
  en_only             BOOLEAN      NOT NULL DEFAULT FALSE,
  lat                 DECIMAL(9, 6) NULL,
  lng                 DECIMAL(9, 6) NULL,
  address_zh          VARCHAR(255) NULL,
  address_en          VARCHAR(255) NULL,
  activity_tags       JSONB         NULL,
  open_hours          JSONB         NULL,
  default_activity_zh VARCHAR(255) NULL,
  default_activity_en VARCHAR(255) NULL,
  active              BOOLEAN      NOT NULL DEFAULT TRUE,
  sort_order          INTEGER          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT ck_place_kind CHECK (kind IN ('area', 'venue'))
);
CREATE INDEX idx_kind_city ON "place" (kind, city, active);
