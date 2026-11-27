# V4-M02 现有评分审计

基准是已验收的 M01 `a50d2e1bf51bdd6193f1b6b621a9a541c822e2cc`。本轮没有改 `Scorer`、`MatchingSolver`、Blossom、`WeekRunner`、S01 发布闸门、P02 的 E3 基线，也没有改 M01 的冻结权重和挑战集。

## V3 对称评分

`Scorer.score` 对两名 `Candidate` 计算同一组整数分：爱好 Jaccard、关系目标、筛选节奏、身份、预算、年龄贴近和距离。交换两人，分数不变。它读的是入池结构字段，不读 `SelfProfile` 或 `PreferenceProfile`。

年龄贴近和距离在内部对两个方向取了平均，但输入仍是同一套候选人字段，不是「A 的偏好对 B 的自身」。`educationImportance` 是学历硬约束上的入驻回答，和 `RelationshipTypes.Importance` 不是同一个枚举。后者才表示用户说这个偏好有多要紧：`NONE`、`KINDA`、`SUPER`。`NONE` 是已经说出的偏好、强度未指明，不是「没有偏好」。

## 不能当成双向评分的现有组件

`TasteWeights.rankAgainst` 用同一组维度既当偏好又当特质，而且生产只算 id 较小的一方。`AgentRerankService` 和 `AiJudge` 是模型精排，不在本轮。`MatchingFeatureGate` 仍然只声明正式分可以读哪些语义支持，生产 `Scorer` 不调用它。

M01 的 `RecallSubject` 把自身和偏好收成 `Map<String, String>`。这个 Map 丢掉重要性、操作符、证据状态、不公开和矛盾。召回可以继续用它产生候选边。评分必须读完整的合成画像。

## 本轮复用

画像仍是 `RelationshipProfile` 的 `SelfProfile` 与 `PreferenceProfile`。特征合法性仍由 `FeatureRegistry` `features-1` 决定。正式分的证据门槛仍是 `MatchingFeatureGate`：只有 `USER_CONFIRMED` 和 `SEMANTIC_SUPPORTED` 可以进入。硬否决仍是 `HardFilter`，软分不能把它加回来。

本轮只接受 `synthetic-fixture:` 来源。不读生产库，不调用模型。
