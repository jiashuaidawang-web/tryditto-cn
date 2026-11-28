-- The external task keeps its own identity and the lifecycle generation it was bound to.
-- NOT_RUN means no executor has deleted the external store. A caller cannot mark that done.

ALTER TABLE account_erasure_task
  ADD COLUMN task_id VARCHAR(40) NOT NULL DEFAULT '',
  ADD COLUMN lifecycle_generation BIGINT NOT NULL DEFAULT 0;

ALTER TABLE account_erasure_task DROP CONSTRAINT ck_account_erasure_status;
ALTER TABLE account_erasure_task ADD CONSTRAINT ck_account_erasure_status
  CHECK (status IN ('PENDING', 'NOT_RUN', 'FAILED', 'DONE'));
