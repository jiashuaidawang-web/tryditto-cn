-- V13: matching consent is its own decision. No backfill.
-- A missing row means UNKNOWN. Historical users are not treated as GRANTED.
-- V4 compliance publishes only GRANTED. UNKNOWN and REVOKED both stay out.
-- Reconfirm is a new POST from the person. Do not UPDATE historical rows to granted.

CREATE TABLE matching_consent (
  user_id          BIGINT       NOT NULL,
  consent_status   VARCHAR(16)  NOT NULL,
  consent_version  INTEGER      NOT NULL,
  granted_at       TIMESTAMP(3) NULL,
  revoked_at       TIMESTAMP(3) NULL,
  policy_version   VARCHAR(32)  NOT NULL,
  updated_at       TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT ck_matching_consent_status CHECK (consent_status IN ('unknown', 'granted', 'revoked')),
  CONSTRAINT ck_matching_consent_version CHECK (consent_version >= 1)
);

-- Internal reason for a safety hold. Never copied onto the match card or an error body.
ALTER TABLE "match" ADD COLUMN hold_reason VARCHAR(200) NULL;
