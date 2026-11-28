# V4-I01 账号生命周期

`LEGACY_ONLY` 的 `requestDelete` 保持原来的行为：锁用户、标记 `deleting`、写 `purge_at`、撤销会话、放开发布。它不调用 `eraseActor`，也不删画像版本。生产配置显式是这个模式。

`V4_INTEGRATION_TEST` 和 `V4_SHADOW_TEST` 在上述步骤提交前继续做这些事，顺序固定：

1. 用户行已经锁住，状态已经是 `deleting`。后续问卷、画像读取和提取许可都会拒绝。
2. `RelationshipFeedbackStore.eraseActor` 在已经持有的用户行锁之后取得咨询锁 11，写入 `relationship_feedback_erasure`，撤回消费，删除该用户的反馈事件，并让 `GroupEvaluationStore.withdrawActor` 按同一顺序撤回观察。
3. `RelationshipProfileService.erase` 删除 `relationship_profile_version`，把画像处理授权改成撤回并增加 `generation_epoch`。
4. 未完成的 `relationship_profile_extraction` 改成 `discarded`，尝试次数封顶，避免旧任务重新启动。正在注销的用户不能再 `tryStart`。
5. 删除该用户的 `profile_learning_proposal` 和 `profile_learning_confirmation`。
6. 把该用户仍待同步的向量 upsert 标成 `cancelled`。这只改关系数据库里的队列表。
7. 撤回匹配授权。
8. 插入 `account_erasure_task`，存储名 `QDRANT`，状态 `NOT_RUN`，并写下任务编号和当时的生命周期代次。重复删除遇到已有行时不把 `FAILED` 改回 `NOT_RUN`。

数据库事务不能和 Qdrant 一起提交。本轮没有外部擦除执行器，调用方不能传入成功标志把任务改成 `DONE`。`recordExternalFailure` 只把 `NOT_RUN` 或 `PENDING` 记成 `FAILED`，失败原因会去掉长数字和密钥样式。`fullyErased` 仍要求任务为 `DONE`，所以这一轮它保持为假。失败或未执行都不会把画像行写回去。

锁顺序由 `UserLifecycleLock` 固定：先按用户编号从小到大锁用户行，再按同样顺序取咨询锁 11，涉及一场匹配时最后取咨询锁 12。学习确认、反馈写入、群体观察和注销都走这条顺序。提交前会再次读取账号状态、处理授权和代次。没有用更长的锁等待代替这个顺序。提取任务在 `tryStart` 和 `complete` 时也先锁用户行，注销中的旧任务不能写回画像。

`TasteExtractor.ensure` 看到 `deleting` 或 `purged` 就跳过，不再调用模型。`FeedbackLearningService` 扫描到这种账号时不改写口味。默认模式下，普通活跃用户的 V3 学习仍会运行。

最小审计留在账号状态、删除时间和擦除任务行上。问卷答案、画像版本和反馈事件不属于这份最小记录；集成模式会删掉后三类中的 V4 行。V3 的 `profile`、`user_taste` 和照片文件不在这个事务里宣称已经从所有外部存储消失。

学习确认不再先拿咨询锁。它和注销使用同一顺序。
