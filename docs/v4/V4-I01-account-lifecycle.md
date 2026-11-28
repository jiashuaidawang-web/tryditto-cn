# V4-I01 账号生命周期

`LEGACY_ONLY` 的 `requestDelete` 保持原来的行为：锁用户、标记 `deleting`、写 `purge_at`、撤销会话、放开发布。它不调用 `eraseActor`，也不删画像版本。生产配置显式是这个模式。

`V4_INTEGRATION_TEST` 在上述步骤提交前继续做这些事，顺序固定：

1. 用户行已经锁住，状态已经是 `deleting`。后续问卷、画像读取和提取许可都会拒绝。
2. `RelationshipFeedbackStore.eraseActor` 取得咨询锁，写入 `relationship_feedback_erasure`，撤回消费，删除该用户的反馈事件，并让 `GroupEvaluationStore.withdrawActor` 撤回观察。
3. `RelationshipProfileService.erase` 删除 `relationship_profile_version`，把画像处理授权改成撤回并增加 `generation_epoch`。
4. 未完成的 `relationship_profile_extraction` 改成 `discarded`，尝试次数封顶，避免旧任务重新启动。正在注销的用户不能再 `tryStart`。
5. 删除该用户的 `profile_learning_proposal` 和 `profile_learning_confirmation`。
6. 把该用户仍待同步的向量 upsert 标成 `cancelled`。这只改关系数据库里的队列表。
7. 撤回匹配授权。
8. 插入 `account_erasure_task`，存储名 `QDRANT`，状态 `PENDING`。重复删除遇到已有行时不把 `FAILED` 或 `DONE` 改回 `PENDING`。

数据库事务不能和 Qdrant 一起提交。`fullyErased` 只有在该用户的外部任务全部是 `DONE` 时才为真。`FAILED` 不是全部删除成功。之后把任务记成 `DONE` 也不会把画像行写回去。再次调用 `recordExternal` 不能把 `DONE` 改回失败。

`TasteExtractor.ensure` 看到 `deleting` 或 `purged` 就跳过，不再调用模型。`FeedbackLearningService` 扫描到这种账号时不改写口味。默认模式下，普通活跃用户的 V3 学习仍会运行。

最小审计留在账号状态、删除时间和擦除任务行上。问卷答案、画像版本和反馈事件不属于这份最小记录；集成模式会删掉后三类中的 V4 行。V3 的 `profile`、`user_taste` 和照片文件不在这个事务里宣称已经从所有外部存储消失。

锁的顺序是先用户行，再咨询锁。学习确认目前先拿咨询锁再拿用户行。两边同时进行时可能互相等待。本轮不改 F02 的锁顺序。
