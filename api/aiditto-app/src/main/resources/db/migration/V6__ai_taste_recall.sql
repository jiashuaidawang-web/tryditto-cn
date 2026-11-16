-- V6: AI 匹配核心表（master_agent_v3 / Q1 完全按 v3）。
-- 风格对齐 V1–V5：无外键、应用侧雪花 ID、UTC TIMESTAMP(3)、JSONB。
-- 现有表不改动；user_taste / recall_candidate 是 v3 新增能力的主存储。

-- 1) 用户口味画像（版本化，不覆盖；反馈学习的主对象）
CREATE TABLE user_taste (
  user_id              BIGINT       NOT NULL,
  version              INTEGER      NOT NULL DEFAULT 1,
  persona_text         TEXT         NOT NULL,   -- 分身模拟用：这个人是什么气质
  taste_text           TEXT         NOT NULL,   -- 召回/裁判用：这个人真正想要什么
  embedding_id         VARCHAR(64)  NULL,      -- Qdrant point id
  embedding_version    VARCHAR(16)  NOT NULL,  -- 禁止跨版本混算相似度
  vector_sync_status   VARCHAR(16)  NOT NULL DEFAULT 'pending',
  source_feedback_cnt  INTEGER      NOT NULL DEFAULT 0,
  updated_reason       TEXT         NULL,
  is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at           TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id, version),
  CONSTRAINT ck_taste_sync CHECK (vector_sync_status IN ('pending','synced','failed','deleted'))
);
CREATE UNIQUE INDEX uq_user_taste_active ON user_taste (user_id) WHERE is_active;
CREATE INDEX idx_user_taste_sync ON user_taste (vector_sync_status, embedding_version);

-- 2) 画像重写审计（可回滚、可举证；单次只改 1 个主权重）
CREATE TABLE taste_log (
  id                BIGINT       NOT NULL,
  user_id           BIGINT       NOT NULL,
  from_version      INTEGER      NULL,
  to_version        INTEGER      NOT NULL,
  trigger_match_id  BIGINT       NULL,
  trigger_feedback  BIGINT       NULL,
  diff_text         TEXT         NULL,
  changed_dim       VARCHAR(32)  NULL,
  created_at        TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_taste_log_user ON taste_log (user_id, created_at);

-- 3) 配对永久记忆（无向唯一；拒绝原因永久沉淀，硬过滤第一道就查它）
CREATE TABLE ai_pair_memory (
  pair_hash      CHAR(32)     NOT NULL,
  user_lo        BIGINT       NOT NULL,
  user_hi        BIGINT       NOT NULL,
  last_week_id   BIGINT       NULL,
  result         VARCHAR(16)  NOT NULL,
  reject_reason  VARCHAR(64)  NULL,
  score_history  JSONB        NOT NULL DEFAULT '[]'::jsonb,
  forbidden      BOOLEAN      NOT NULL DEFAULT FALSE,
  updated_at     TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (pair_hash),
  CONSTRAINT ck_pair_memory_order CHECK (user_lo < user_hi),
  CONSTRAINT ck_pair_memory_result CHECK (result IN
    ('accepted','dated','again','refused','no_show','paused','declined','reported'))
);
CREATE INDEX idx_pair_memory_users ON ai_pair_memory (user_lo, user_hi);
CREATE INDEX idx_pair_memory_forbidden ON ai_pair_memory (forbidden) WHERE forbidden;

-- 4) 五路召回 + Agent 精排全链路留痕（可解释、可归因、裁判校准、算法备案证据）
CREATE TABLE recall_candidate (
  id            BIGINT        NOT NULL,
  week_id       BIGINT        NOT NULL,
  user_id       BIGINT        NOT NULL,
  cand_id       BIGINT        NOT NULL,
  sources       TEXT[]        NOT NULL DEFAULT '{}',  -- R1..R5 归因
  rrf_score     NUMERIC(8,4)  NULL,
  recall_rank   INTEGER       NULL,
  coarse_score  NUMERIC(6,2)  NULL,
  sim_report    JSONB         NULL,                   -- 裁判严格 JSON
  final_score   NUMERIC(6,2)  NULL,
  llm_cost_rmb  NUMERIC(10,4) NOT NULL DEFAULT 0,
  degraded      BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_recall_week_pair UNIQUE (week_id, user_id, cand_id)
);
CREATE INDEX idx_recall_user ON recall_candidate (week_id, user_id, final_score DESC);
CREATE INDEX idx_recall_sources ON recall_candidate USING gin (sources);

-- 5) 大模型调用日志（主备切换、重试、熔断、成本；敏感内容不入库）
CREATE TABLE llm_call_log (
  id             BIGINT       NOT NULL,
  week_id        BIGINT       NULL,
  call_point     VARCHAR(32)  NOT NULL,  -- taste_extract / date_simulate / judge / taste_rewrite / match_copy
  model          VARCHAR(64)  NOT NULL,
  prompt_version VARCHAR(32)  NOT NULL,
  input_tokens   INTEGER      NULL,
  output_tokens  INTEGER      NULL,
  latency_ms     INTEGER      NULL,
  status         VARCHAR(16)  NOT NULL,  -- ok / retry / fallback / failed / degraded
  error_code     VARCHAR(32)  NULL,
  created_at     TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_llm_call_week ON llm_call_log (week_id, call_point, created_at);
CREATE INDEX idx_llm_call_status ON llm_call_log (status, created_at);

-- 6) PG → Qdrant 同步账（双写 / 影子索引 / 切流 / 回滚依据）
CREATE TABLE vector_sync (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  persona_version INTEGER      NOT NULL,
  collection      VARCHAR(64)  NOT NULL,
  vector_kind     VARCHAR(16)  NOT NULL,  -- taste / persona / crush / tags
  op              VARCHAR(16)  NOT NULL,  -- upsert / delete
  status          VARCHAR(16)  NOT NULL DEFAULT 'pending',
  retry_cnt       SMALLINT     NOT NULL DEFAULT 0,
  error_msg       TEXT         NULL,
  created_at      TIMESTAMP(3) NOT NULL,
  synced_at       TIMESTAMP(3) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_vector_sync_pending ON vector_sync (status, created_at);

-- 7) 人工干预审计（算法备案需说明人工干预机制）
CREATE TABLE manual_audit (
  id          BIGINT       NOT NULL,
  week_id     BIGINT       NULL,
  operator    VARCHAR(64)  NOT NULL,
  action      VARCHAR(32)  NOT NULL,  -- replace_pair / force_deliver / disable_match
  payload     JSONB        NOT NULL DEFAULT '{}'::jsonb,
  reason      TEXT         NOT NULL,
  created_at  TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_manual_audit_week ON manual_audit (week_id, created_at);
