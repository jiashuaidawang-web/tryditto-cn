-- 技术方案 §4.4.

CREATE TABLE onboarding_answer (
  user_id     BIGINT          NOT NULL,
  question_id VARCHAR(48)     NOT NULL,
  value_json  JSONB            NULL,
  value_enc   BYTEA NULL,
  updated_at  TIMESTAMP(3)     NOT NULL,
  PRIMARY KEY (user_id, question_id),
  CONSTRAINT ck_answer_one_value CHECK ((value_json IS NULL) <> (value_enc IS NULL))
);

CREATE TABLE profile (
  user_id              BIGINT         NOT NULL,
  version              INTEGER            NOT NULL DEFAULT 0,
  name                 VARCHAR(64)    NULL,
  gender               VARCHAR(16)    NULL,
  expected_genders_enc BYTEA NULL,
  birthday             DATE           NULL,
  age_min              INTEGER            NOT NULL DEFAULT 18,
  age_max              INTEGER            NOT NULL DEFAULT 24,
  height_cm            INTEGER            NULL,
  height_any           BOOLEAN        NOT NULL DEFAULT TRUE,
  height_min           INTEGER            NULL,
  height_max           INTEGER            NULL,
  is_student           BOOLEAN        NOT NULL DEFAULT FALSE,
  school_year          VARCHAR(16)    NULL,
  school_domain        VARCHAR(255)   NULL,
  occupation           VARCHAR(255)   NULL,
  education_level      VARCHAR(16)    NULL,
  education_importance VARCHAR(16)    NULL,
  budget               VARCHAR(16)    NULL,
  matching_style       VARCHAR(16)    NULL,
  relationship_looking JSONB           NULL,
  hobbies              JSONB           NULL,
  hobby_details        JSONB           NULL,
  place_id             VARCHAR(32)    NULL,
  travel_mode          VARCHAR(16)    NULL,
  travel_minutes       INTEGER            NULL,
  first_message        VARCHAR(400)   NULL,
  ai_tags              JSONB           NULL,
  ai_constraints       JSONB           NULL,
  ai_status            VARCHAR(16)    NOT NULL DEFAULT 'none',
  avatar_spec          JSONB           NULL,
  persona              JSONB           NULL,
  wechat_enc           BYTEA NULL,
  photo_count          INTEGER            NOT NULL DEFAULT 0,
  updated_at           TIMESTAMP(3)    NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT ck_profile_gender CHECK (gender IS NULL OR gender IN ('man', 'woman', 'nonbinary', 'other')),
  CONSTRAINT ck_profile_ai_status CHECK (ai_status IN ('none', 'pending', 'ok', 'failed')),
  CONSTRAINT ck_profile_ages CHECK (age_min >= 18 AND age_min <= age_max AND age_max <= 80)
);
CREATE INDEX idx_place ON "profile" (place_id);
CREATE INDEX idx_gender ON "profile" (gender);
