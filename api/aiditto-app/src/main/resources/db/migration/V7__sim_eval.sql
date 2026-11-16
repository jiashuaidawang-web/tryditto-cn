-- V7: 合成反馈仿真沙箱（独立 schema，不进生产业务表）。
-- 隐藏真值只能由 sim 包读取，禁止进入任何 prompt / persona_text / 日志（泄漏扫描保护）。

CREATE SCHEMA IF NOT EXISTS sim;

-- 1) 合成用户索引（业务本体仍走 "user".cohort = synthetic，夹具 users-v1.json 不改动）
CREATE TABLE sim.sim_user (
  user_id         BIGINT       NOT NULL,
  batch_id        VARCHAR(32)  NOT NULL,
  demographic     JSONB        NOT NULL DEFAULT '{}'::jsonb,
  long_tail_tag   VARCHAR(32)  NULL,
  is_two_faced    BOOLEAN      NOT NULL DEFAULT FALSE,  -- 嘴上条件与隐藏真值故意冲突
  dirty_data_tag  VARCHAR(32)  NULL,
  created_at      TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id)
);
CREATE INDEX idx_sim_user_batch ON sim.sim_user (batch_id, long_tail_tag);

-- 2) 隐藏真值（10 维权重 + 审美向量 + 真实红线）
CREATE TABLE sim.sim_hidden_truth (
  user_id           BIGINT       NOT NULL,
  weights           JSONB        NOT NULL,
  crush_vector      JSONB        NULL,
  dealbreakers_true JSONB        NOT NULL DEFAULT '[]'::jsonb,
  true_score_model  VARCHAR(16)  NOT NULL DEFAULT 'v1',
  created_at        TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id)
);

-- 3) 回放批次（E1–E14 实验矩阵，同配置可复现）
CREATE TABLE sim.sim_run (
  run_id      BIGSERIAL    NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  experiment  VARCHAR(16)  NOT NULL,
  config      JSONB        NOT NULL DEFAULT '{}'::jsonb,
  user_cnt    INTEGER      NOT NULL,
  cycles      INTEGER      NOT NULL DEFAULT 12,
  started_at  TIMESTAMP(3) NOT NULL,
  finished_at TIMESTAMP(3) NULL,
  verdict     VARCHAR(16)  NULL,
  PRIMARY KEY (run_id),
  CONSTRAINT uk_sim_run_code UNIQUE (code),
  CONSTRAINT ck_sim_run_verdict CHECK (verdict IS NULL OR verdict IN ('pass','fail'))
);

-- 4) 每周期指标（学习曲线 / 召回质量 / 公平 / 成本）
CREATE TABLE sim.sim_cycle_metric (
  id                     BIGSERIAL    NOT NULL,
  run_id                 BIGINT       NOT NULL,
  cycle_no               INTEGER      NOT NULL,
  top1_hit_decile        NUMERIC(6,4) NULL,
  regret                 NUMERIC(6,4) NULL,
  taste_recovery         NUMERIC(6,4) NULL,
  two_faced_conv         NUMERIC(6,4) NULL,
  recall_at50            NUMERIC(6,4) NULL,
  filtered_recall_1pct   NUMERIC(6,4) NULL,
  coverage               NUMERIC(6,4) NULL,
  source_diversity       NUMERIC(6,4) NULL,
  long_tail_share        NUMERIC(6,4) NULL,
  pair_repeat_rate       NUMERIC(6,4) NULL,
  cost_rmb               NUMERIC(10,2) NULL,
  degraded_cnt           INTEGER      NULL,
  created_at             TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_sim_metric_cycle UNIQUE (run_id, cycle_no)
);

-- 5) 反馈 Oracle 留档（真值 + 噪声 + 对抗标记）
CREATE TABLE sim.sim_feedback_log (
  id           BIGSERIAL    NOT NULL,
  run_id       BIGINT       NOT NULL,
  cycle_no     INTEGER      NOT NULL,
  feedback_id  BIGINT       NULL,
  user_id      BIGINT       NOT NULL,
  partner_id   BIGINT       NOT NULL,
  true_score   NUMERIC(6,2) NOT NULL,
  noise        NUMERIC(6,4) NULL,
  flags        JSONB        NOT NULL DEFAULT '{}'::jsonb,  -- ambivalent / emotional / terse
  created_at   TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_sim_fb_run ON sim.sim_feedback_log (run_id, cycle_no);

-- 6) 对抗用例结果（A01–A28，P0 必须 100% 通过）
CREATE TABLE sim.sim_case_result (
  id          BIGSERIAL    NOT NULL,
  run_id      BIGINT       NOT NULL,
  case_code   VARCHAR(8)   NOT NULL,
  level       VARCHAR(4)   NOT NULL,
  passed      BOOLEAN      NOT NULL,
  detail      TEXT         NULL,
  created_at  TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_sim_case UNIQUE (run_id, case_code),
  CONSTRAINT ck_sim_case_level CHECK (level IN ('P0','P1'))
);
