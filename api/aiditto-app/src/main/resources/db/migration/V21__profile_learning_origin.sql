-- V20 stays as committed. A confirmation records whether the user stated the value
-- or only accepted a hypothesis that depends on feedback events.

ALTER TABLE profile_learning_confirmation
    ADD COLUMN evidence_origin VARCHAR(16) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE profile_learning_confirmation
    ADD CONSTRAINT ck_profile_learning_confirmation_origin
        CHECK (evidence_origin IN ('DIRECT', 'DERIVED'));
