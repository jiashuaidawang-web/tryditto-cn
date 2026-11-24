-- 自适应情景采集引擎（创始人 2026-11-18 拍板治本：分身保真度 > 模拟次数）。
--
-- 核心设计（创始人原话拆解）：
--  - LLM 基于用户已答内容动态出「独一无二」的情景题，不是固定题库；
--  - 每题给 N 个方向选项，按「系统认为用户最可能选什么」排序（A=预测第一，依次往下）——
--    选项顺序本身携带信息：用户实际选的 vs 系统预测的差距 = 校准信号（prediction vs reality）；
--  - 让用户选（低摩擦），同时保留自定义输入框（用户原话 = 语气样本资产）；
--  - 答完一题再据实生成下一题；用户可随时选择「不再继续」，此时基于已有回答推断人格；
--  - 全过程产出两类资产：人格推断（性格）+ 语气样本（本人原话），供分身 Agent 学习。
--
-- 合规：与 §8.1 一致——只存用户本人自愿输入的情景题作答，不接聊天记录/第三方数据；
-- 自定义文本按敏感信息处理（加密存储），即使用户写了手机号邮箱也不泄露给匹配对象。

CREATE TABLE voice_session (
  id            BIGINT        NOT NULL,
  user_id       BIGINT        NOT NULL,
  state         VARCHAR(16)   NOT NULL DEFAULT 'active',   -- active / completed / abandoned
  prompt_version VARCHAR(32)  NOT NULL,
  step_count    INTEGER       NOT NULL DEFAULT 0,
  started_at    TIMESTAMP(3)  NOT NULL,
  ended_at      TIMESTAMP(3)  NULL,
  created_at    TIMESTAMP(3)  NOT NULL,
  updated_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_voice_session_state CHECK (state IN ('active','completed','abandoned'))
);
CREATE INDEX idx_voice_session_user ON voice_session (user_id, state, started_at DESC);

CREATE TABLE voice_question (
  id            BIGINT        NOT NULL,
  session_id    BIGINT        NOT NULL,
  step          INTEGER       NOT NULL,
  scenario      VARCHAR(500)  NOT NULL,          -- 情景题面（LLM 生成）
  -- options: [{key:'A',text:'...',trait:'outgoing',predict_rank:1}, ...]
  -- predict_rank = 系统预测用户会选的排序（1=最可能=A），用于事后校准
  options_json  JSONB         NOT NULL,
  prompt_version VARCHAR(32)  NOT NULL,
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (session_id, step),
  CONSTRAINT fk_voice_question_session FOREIGN KEY (session_id) REFERENCES voice_session(id)
);

CREATE TABLE voice_answer (
  id            BIGINT        NOT NULL,
  question_id   BIGINT        NOT NULL,
  session_id    BIGINT        NOT NULL,
  user_id       BIGINT        NOT NULL,
  chosen_key    VARCHAR(4)    NULL,              -- 选中的选项 key（A/B/C…）；自定义时为 null
  custom_text_enc BYTEA       NULL,              -- 自定义输入原文（加密，语气样本资产）
  predict_hit   BOOLEAN       NULL,              -- 是否命中系统预测第一（A）= 校准信号
  trait_signal  VARCHAR(64)   NULL,              -- 该选项指向的人格维度（供推断）
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (question_id),
  CONSTRAINT ck_voice_answer_choice CHECK (chosen_key IS NOT NULL OR custom_text_enc IS NOT NULL),
  CONSTRAINT fk_voice_answer_question FOREIGN KEY (question_id) REFERENCES voice_question(id)
);
CREATE INDEX idx_voice_answer_user ON voice_answer (user_id, created_at);

-- 语气样本：用户本人原话（来自自定义输入，或未来反馈原话），分身 Agent 的语气锚点。
CREATE TABLE voice_sample (
  id            BIGINT        NOT NULL,
  user_id       BIGINT        NOT NULL,
  source        VARCHAR(24)   NOT NULL,          -- scenario_custom / feedback / voice_seed
  text_enc      BYTEA         NOT NULL,          -- 加密原文
  context       VARCHAR(200)  NULL,              -- 该话的场景（脱敏）
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX idx_voice_sample_user ON voice_sample (user_id, source, created_at);

-- 人格推断：会话结束时基于全部作答生成，供 persona/分身使用。
CREATE TABLE voice_profile (
  user_id       BIGINT        NOT NULL,
  version       INTEGER       NOT NULL,
  session_id    BIGINT        NOT NULL,
  traits_json   JSONB         NOT NULL,          -- 人格维度结论 + 置信度
  summary_enc   BYTEA         NULL,              -- 脱敏后的人格总结（加密）
  calibration   NUMERIC(5,4)  NULL,              -- 预测命中率（系统预测 vs 用户实际选择）
  prompt_version VARCHAR(32)  NOT NULL,
  is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMP(3)  NOT NULL,
  PRIMARY KEY (user_id, version),
  CONSTRAINT fk_voice_profile_session FOREIGN KEY (session_id) REFERENCES voice_session(id)
);
