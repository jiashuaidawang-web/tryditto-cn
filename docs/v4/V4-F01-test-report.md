# V4-F01 测试

领域测试在本地执行。PostgreSQL 集成测试写在 `FeedbackEventIT`，并加进 `.github/workflows/v4-ci.yml` 的 `-Dit.test` 和要求执行的方法名单。本地没有可用的 PostgreSQL，这一份报告先记下领域结果。集成数字以该分支的 CI 为准，CI 地址在运行完成后补上。

F01 从 `bbc092e8b0120f18032432dc22d1e56c7636b6cb` 拉出。该提交已并入 `master_agent_v4`，CI 是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46049404560 。四个作业 success。

## 领域

`FeedbackLedgerTest` 11 项，失败 0。覆盖：

- 合法发布、查看、接受，以及双方接受只派生一次。
- 非参与者、伪造对方、伪造系统事件。
- 同一幂等键重复提交，以及第二个接受键被拒绝。
- 并发的两次接受只留一条。
- 接受和撤回并发之后学习资格关闭，撤回不能被更正打开。
- 乱序查看不改写接受。
- 更正后旧事件不能消费。
- 见面争议保持 `CONTESTED`，一侧继续不等于两侧继续。
- 画像版本和三种策略版本来自快照。伪造回放失去资格。
- 未发布是 `NOT_AUTHORIZED`。没有反馈授权是 `NO_FEEDBACK_CONSENT`，两者分开。
- 删除标记、未知和超时都不算拒绝。未成熟配对不进分母。
- 十个案例回放后的投影与原投影相同。已发布口径和已响应口径分开断言。

全部领域测试 `mvn -pl aiditto-domain test`：569 项，失败 0，错误 0。其中 11 项是 `FeedbackLedgerTest`。其余 558 项是收口提交上的原有领域测试。

## PostgreSQL

`FeedbackEventIT` 要求执行这 6 个方法：

- `sameKeyIsOneRow`：唯一键，直接再插入同一幂等键会违反约束。
- `replayMatchesTheStoredProjection`：库中事件投影与内存投影的响应和双方接受一致，再读一次不变。
- `eraseRemovesTheActorAndBlocksLearning`：行为人的行被删除，对方的行阻断学习。
- `concurrentAcceptsKeepOne`：两个事务并发接受，只留一条。
- `acceptAndRetractBothRemainAndLearningStops`：接受和撤回都留下，学习资格关闭。
- `secondConsumptionDoesNotClaimAgain`：第二次消费声明失败，重新加载后是 `ALREADY_CONSUMED`。

这些方法还没在本地的 PostgreSQL 上跑过。工作流会在 CI 的真实 PostgreSQL 16 上执行，并且少一个方法就会让作业失败。原有的 `PublishSafetyIT`、`ConsentLegacyIT`、`MatchIT`、`RelationshipProfileIT`、`ProfileExtractionIT` 仍在名单里。

本轮没有调用模型，没有改口味，没有改匹配权重，也没有读生产反馈。
