-- V10: 反馈学习闭环（Step 8）。
-- 与 V6 的 user_taste / taste_log / ai_pair_memory 衔接，只增量加列与一张校准表，不改既有表语义。

-- 1) 结构化口味权重：与 taste_text 双轨并存。
--    自然语言给分身/裁判/文案用；10 维权重给「单次只调 1 个权重、幅度 ≤0.15」、
--    taste_recovery 的 Spearman ρ、口是心非收敛、恶意差评稳定性这些可计算闸门用。
--    默认 '{}' 表示冷启动中性（TasteWeights.of 会把缺失维度补 0.5）。
ALTER TABLE user_taste
  ADD COLUMN weights JSONB NOT NULL DEFAULT '{}'::JSONB,
  ADD COLUMN learned_feedback_cnt INTEGER NOT NULL DEFAULT 0;

-- 2) 已消费反馈的可重入标记：taste_log.trigger_feedback 记 match_id，
--    (user_id, trigger_feedback) 唯一，保证同一条反馈不会被重复学习。
CREATE UNIQUE INDEX uq_taste_log_feedback
  ON taste_log (user_id, trigger_feedback)
  WHERE trigger_feedback IS NOT NULL;

-- 3) 每周≤1 次画像重写的计数索引。
CREATE INDEX idx_taste_log_rewrite_week
  ON taste_log (user_id, created_at)
  WHERE changed_dim IN ('taste_rewrite', 'taste_rewrite_failed');

-- 4) 裁判校准：≥30 条真实「预测分 vs 观测反馈」后才允许启用偏置。
--    bias 是作用在 AI 分上的加性修正，服务端硬夹在 ±10 内，防止校准反客为主。
CREATE TABLE judge_calibration (
  id            BIGINT        NOT NULL,
  sample_cnt    INTEGER       NOT NULL,
  predicted_avg NUMERIC(6,2)  NOT NULL,
  observed_avg  NUMERIC(6,2)  NOT NULL,
  bias          NUMERIC(6,2)  NOT NULL,
  active        BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_calibration_bias CHECK (bias BETWEEN -10 AND 10)
);
CREATE INDEX idx_judge_calibration_active ON judge_calibration (active, created_at DESC);
