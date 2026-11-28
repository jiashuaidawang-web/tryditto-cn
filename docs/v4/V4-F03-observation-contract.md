# V4-F03 观察契约

数据集版本是 `f03-synthetic-1`。结构化行在 `docs/v4/f03-observations.json`。

一条观察至少包含：

- `observationId`、`matchId`、`matchingRunId`、`matchingPeriod`
- 双方用户、发布时的画像版本和生命周期代次
- `candidateStrategyVersion`、`compatibilityPolicyVersion`、`globalObjectiveVersion`、`scoreFormula`
- `scoreAB`、`scoreBA`、`coverageAB`、`coverageBA`、`reciprocalScore`
- `publishedAt`、回应截止、见面截止、继续交往截止、`observedAt`
- 双方回应、是否查看、是否安排见面、见面证据、双方继续意愿
- 反馈授权、是否撤回、来源事件编号

预测特征来自发布时的快照。匹配之后的画像版本留在调用方，不写进这条观察。回应和后续结果是标签，不进入 `features()`。

开发集是用户编号小于 92000 且周期为 2026-11-01 的行，共 96 条。保留集是 92000 起、周期为 2026-11-20 的行，共 32 条。两边的用户集合不相交。

未匹配的人不会被写成所有候选关系的负例。观察只来自已发布匹配。没有发布的配对没有接受、见面或继续交往标签。

同一匹配的重复事件合成一条观察。再插入同一 `matchId + matchingRunId` 会得到 `DUPLICATE_MATCH`。

## 事件驱动入口

契约版本是 `f03-observation-2`。`ObservationBuilder.fromLedger` 只从服务端账本和 `MatchScoreSnapshot` 建观察。调用方不能传入双方接受、见面或继续意愿。`MatchScoreSnapshot` 只有分数、覆盖率和公式。

进入观察之前要核对：

- 匹配编号、运行编号、匹配周期、双方用户与账本上下文一致。
- 双方都有反馈处理授权。一方授权不能代替另一方。
- 来源事件编号集合等于当前有效事件集合。不存在、已被更正取代的编号返回 `SOURCE_MISMATCH`。
- 有效事件里出现 `FEEDBACK_RETRACTED` 返回 `RETRACTED_SOURCE`。出现 `FEEDBACK_PROCESSING_REVOKED` 返回 `PROCESSING_REVOKED`。
- 必须有一条有效的 `MATCH_PUBLISHED`。发布时间取这条事件的记录时间。
- 事件上的策略版本、策略目标和画像版本与匹配上下文一致。方向只能是 `A_TO_B`、`B_TO_A` 或系统事件的 `JOINT`。
- `observedAt` 不早于发布时间，也不晚于调用时的 `now`。三个截止时间都晚于发布时间。

数学和统计夹具继续用 `MatchingObservation` 的构造器。那条入口标记为 `FIXTURE`，与 `EVENT` 入口分开。

`ObservationDocument` 保留预测特征、结果标签、三个截止时间、发布时间、观察时间、匹配周期、授权、撤回、来源事件和数据集版本。摘要是整段文本的 SHA-256。只改一个截止时间，摘要就会变。

`TimeSplit.development` 按 `publishedAt` 切分。匹配周期字段不同，本身不是这条时间外推。

`GroupEvaluationStore.recordBuilt` 写入观察，并把每条来源事件记到 `matching_observation_source`。可消费事件领取 `GROUP_EVALUATION` 凭证。`MATCH_PUBLISHED` 只链接，不领取。撤回一条观察时，只撤销它链接的凭证，并保持 `withdrawn=true`。没有把已撤回凭证重新打开的方法。数据集版本失效时，该版本的策略提案和校准运行标成 `valid=false`，其他数据集不动。

本轮不读取生产匹配快照。`MatchFeedbackContext.production()` 仍抛出 `PRODUCTION_CONTEXT_NOT_WIRED`。
