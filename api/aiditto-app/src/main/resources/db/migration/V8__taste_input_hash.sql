-- V8: 画像幂等判重所需的输入指纹（Step 4 信息收集衔接）。
-- V6 已在 aiditto_pg / aiditto_test 执行过，validate-on-migrate=true 下不允许改写 V6，故走增量迁移。
-- input_hash = SHA-256(prompt_version + "\n" + canonical(TasteFacts.values()))。
-- 同一用户答案未变时复用现有 active 版本，不重复调用模型、不产生新版本。

ALTER TABLE user_taste ADD COLUMN input_hash CHAR(64) NULL;

CREATE INDEX idx_user_taste_hash ON user_taste (user_id, input_hash);

COMMENT ON COLUMN user_taste.input_hash IS
  'SHA-256 of prompt_version + canonical desensitized facts; NULL for rows created before V8';
