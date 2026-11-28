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
