# V4-F03 设计

F02 记录一个用户确认后的偏好变化。F03 记录一批已经发布的匹配后来发生了什么，并据此比较策略。一次拒绝不改这个人的画像。一次接受也不把整套算法标成正确。

## 数据

一条 `MatchingObservation` 是一次已发布匹配。分数、覆盖率、画像版本、生命周期代次、候选策略、契合策略、全局目标和评分公式在发布时冻结。`historical(laterA, laterB)` 返回原记录。`features()` 只带发布时的预测字段，不带回应、见面和继续交往。

同一 `matchId + matchingRunId` 只能有一条观察。同一匹配上的多条事件记在 `sourceEventIds` 里，计数时仍是一条关系。

## 消费

`GROUP_EVALUATION` 和 `PROFILE_LEARNING` 是两把钥匙。领取群体评估凭证和写入观察在同一个事务里。来源撤回后，凭证变成 `REVOKED`，观察离开合法集。已经是 `REVOKED` 的钥匙再次领取会得到 `DUPLICATE_CONSUMPTION`，观察不会恢复。

账号删除走 `eraseActor`：先锁用户，再撤回该用户的消费凭证，再把观察标成撤回并把载荷写成 `REDACTED`。`AccountService.requestDelete` 仍未调用这条路径。约定写在 `GroupEvaluationStore.FUTURE_ACCOUNT_DELETION`。

## 策略提案

`StrategyLearningProposal` 可以是 `INSUFFICIENT_DATA`、`EXPERIMENTAL`、`EVALUATED`、`REJECTED` 或 `APPROVAL_REQUIRED`。合成集上样本够用时状态是 `EVALUATED`，变更内容是 `no weight change`。任何状态调用 `activate()` 都拒绝。数据库不允许把 `activated` 改成 true。

受控实验的记录值是 `NOT_STARTED`。本轮不启动真实 A/B。

## 范围

只用合成数据。校准结果不写回评分。策略计数不给未发布的候选编造接受、见面或继续交往结果。
