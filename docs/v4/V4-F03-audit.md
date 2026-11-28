# V4-F03 审计

F03 读的是已经落地的反馈凭证、画像快照和离线匹配实验，用来做群体评估。这一轮没有改 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom，也没有把 V3 的自动调权接进来。

## 已有代码

`FeedbackConsumption` 原先在 `GROUP_EVALUATION` 上抛出 `F03_NOT_IMPLEMENTED`。V19 已经允许这个 `consumer_type`，主键仍是 `(event_id, consumer_type, policy_version)`。本轮让群体评估走自己的准备路径：事件必须存在且可消费，行动者不是系统占位，反馈处理授权仍在，来源没有被取代。它不读取学习同意，也不读取旧的 `consumed_at`。个体学习已经消费同一事件时，群体评估仍可以按自己的用途键领取。

`JudgeCalibrationService`、`FeedbackLearningService` 和 `TasteRewriter` 仍留在原处。F03 不调用它们，也不把它们的权重写回 M02 或 M04。

M02 的 B5 分数是契合度，不是接受概率。M04 的默认离线目标仍是 `Objective.G2_CARDINALITY_THEN_WEIGHT`。`Objective.SELECTED` 没有改。

## 本轮新增

观察记录、三个关系标签、漏斗、离线校准、策略计数和 `StrategyLearningProposal` 都在 `cn.aiditto.domain.feedback.v4.strategy`。持久化在 V22 的 `matching_observation` 和 `strategy_learning_proposal`。V14–V21 没有改。

`activate()` 抛出 `STRATEGY_NOT_ACTIVATED`。表上的 `activated` 有 `CHECK (activated = FALSE)`。

## 没有接入的路径

- `AccountService.requestDelete` 仍不调用删除。`eraseActor` 会把该用户的观察标成撤回并抹掉载荷。
- `MatchFeedbackContext.production()` 仍抛出 `PRODUCTION_CONTEXT_NOT_WIRED`。
- 生产匹配、生产画像处理和真实反馈学习都没有打开。
- 没有调用大模型，没有读生产用户数据。
