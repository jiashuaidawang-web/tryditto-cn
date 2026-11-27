# V4-M01 现有召回审计

基准是 `f6189dc517eee74edd7c6ce78e5a5ae48c155191`。本轮没有改 P02 的 Prompt、封闭短语、语义验证器，也没有改 `WeekRunner`、`MatchingSolver`、`Scorer` 和 Blossom。

## 生产调用链

每周匹配从 `MatchScheduler.tick` 进入 `WeekRunner`。`solve` 用 `People.city` 取出整座城市里已完成 onboarding 的用户，再经过 `PoolGate` 和匹配同意。`MatchingSolver` 对每一对做双向 `HardFilter`，用规则分建边，然后求最大权匹配。默认关闭的 agent 精排只在全局边里保留 `topPairs`（默认 15，上限 100）。这是全局配对预算，不是每个用户的 Top-K。

`WeekRunner` 不调用 `RecallPipeline`，也不读 `recall_candidate`。

## V3 召回实验链

`RecallPipeline.recall` 只被 `VectorSyncIT` 调用。它的五路是：

| 路 | 做法 | 默认 K |
| --- | --- | ---: |
| R1 | `taste_text` 向量 | 200 |
| R2 | `persona_text` 向量 | 200 |
| R3 / R4 | 兴趣和价值标签 | 各 200 |
| R5 | `userId % 97` 的探索分 | 50 |

融合后还有 `coarseRank`，把 RRF 分、标签分、探索分和 `FeedbackPrior` 再加一遍，截成每人 15 人。这 15 和 agent 的 15 不是同一个数。

融合之前没有走 `HardFilter`。Qdrant 过滤只做单向年龄和身高；结构化召回的性别也是单向的。cohort、同意、屏蔽和成年门槛都不在这条链上。`includeSynthetic` 没有被过滤器用到。

## taste 与反馈

Qdrant 里的 taste 向量来自 `taste_text`，不是关系画像特征。`TasteWriter` 希望它只写「想遇到谁」，但输入 JSON 同时包含自身爱好和伴侣期望。确定性回退把通勤写进 taste。`TasteWeights` 的 10 维把同一组数既当自身又当偏好，而且 0.5 被当成冷启动而不是未知。这 10 维不进入 embedding。

`FeedbackPrior` 用候选人自己打出的历史评分，向 0.5 收缩。它不读性别或年龄，也没有读它注释里提到的 `ai_pair_memory`。`GenderNorms` 只做词面归一；空偏好会被收成全部性别。

## 向量同步

集合默认是 `match_user_v1`，点 id 是 `user_id`，覆盖写。payload 有 embedding 模型版本，没有画像版本。同一用户的旧批次可以在新版本已经同步之后再写回 Qdrant，Postgres 也不会把它改回 pending。注销一类状态会删点。关系画像撤回不会删点。

CI 默认不跑 `RecallEvalIT`、`RecallExperimentIT`、`QdrantClientIT`、`VectorSyncIT`。它们还要 `AIDITTO_QDRANT_IT=1` 或 `AIDITTO_RECALL_MATRIX=1`。

## 复用和隔离

可以直接复用：`PoolGate`、`ConsentPolicy`、`HardFilter`、`Candidate`、`PairKey`、`RrfFusion`、`MatchingFeatureGate`、`HashingEmbeddingService`、`QdrantRestClient` 的隔离集合能力，以及 200 人合成人口 `users-v1.json`。

必须留在 V3 实验链里：混合的 taste / persona 文本、`TasteWeights`、`FeedbackPrior`、空偏好变全部性别、单向年龄身高过滤、`userId % 97` 探索，以及按 `user_id` 无版本覆盖的同步。

`EligibilityGate.evaluate` 会把「正在发布的那一对」从历史配对里拿掉。召回如果直接调用它，已经配对过的人会漏过 `NEVER_MATCHED`。V4 复用它背后的账户、同意和 `HardFilter` 规则，但不复用这个发布例外。
