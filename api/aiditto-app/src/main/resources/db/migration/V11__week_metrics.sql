-- 真实效果埋点（创始人需求：每次策略/学习方案改动都能归因到指标涨跌）。
-- week.stats 只记过程指标（配了几对/耗时），本表记**结果指标快照**（接受/成功/见面/满意），
-- 且每次快照带 config_version 标记——对照组按版本切分即可回答「从多少提升到多少、因为改了什么」。
--
-- 指标从现有事实表聚合算出，不重复埋点、不冗余存储原始事件：
--   pairs        = match 行数
--   responded    = match_side.response <> 'pending' 的人数
--   accepted     = match_side.response = 'accept' 的人数
--   mutual_ok    = match.outcome = 'MUTUAL' 的配对数（双方都接受、确认见面）
--   met          = feedback.met = 'met' 的人数（真的见了面）
--   again_yes    = feedback.again = 'yes' 的人数（还想再见 = 满意代理指标）
-- 所有比率在查询层算，避免落库后口径写死。

CREATE TABLE week_metric (
  week_id          BIGINT       NOT NULL,
  pool             VARCHAR(32)  NOT NULL,
  week_start       DATE         NOT NULL,
  config_version   VARCHAR(64)  NOT NULL,
  pairs            INTEGER      NOT NULL,
  participants     INTEGER      NOT NULL,
  responded        INTEGER      NOT NULL,
  accepted         INTEGER      NOT NULL,
  mutual_ok        INTEGER      NOT NULL,
  met              INTEGER      NOT NULL,
  again_yes        INTEGER      NOT NULL,
  feedback_cnt     INTEGER      NOT NULL,
  captured_at      TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (week_id, config_version),
  CONSTRAINT fk_week_metric_week FOREIGN KEY (week_id) REFERENCES week(id)
);

CREATE INDEX idx_week_metric_version ON week_metric (config_version, week_start);
