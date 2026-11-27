# V4-F01 数据模型

迁移是 `V18__relationship_feedback_event.sql`。没有改已执行的 V1 到 V17。表不引用生产的 `match` 或 `user`，测试可以使用虚构编号。

## relationship_feedback_event

一行是一条事实。`event_id` 是主键。`event_seq` 按插入递增，回放按它排序，所以同一毫秒里的接受和派生事件不会换位。

唯一键是 `idempotency_key`。另外两个索引是 `(match_id, recorded_at)` 和 `(actor_user_id, direction, recorded_at)`。

版本、策略、原因、证据、`learning_blocked`、`consumed_at`、`erased_at` 都在这行上。原因只能是契约里的九个词或空。没有自由文本列。

同一配对的写入先取 `pg_advisory_xact_lock(match_id)`，再读取已有事件、做领域校验、插入新行。锁在事务结束时释放。两个不同的接受键因此不会同时通过「还没有接受」的检查。

## relationship_feedback_erasure

`user_id` 主键，加上 `erased_at`。`eraseActor` 在一个事务里：

- 记下这个人已经删除。
- 删掉他作为行为人的事件。
- 把他作为对方出现的事件标成 `learning_blocked`，并清掉原因词。

这样删除之后，他的原话不留下，对方的记录也不能再被学习。只删事件行是不够的，因为双方事件都删光以后会失去「这个人已经行使删除权」的标记。

`consumed_at` 只有在为空且该行没有被阻断学习时才能写上。第二次声明返回 false。

## 还没有做的生产动作

这张表只在隔离测试库由 Flyway 建出来。本轮不改生产库，也不把 `AccountService.requestDelete` 接到 `eraseActor`。可审计不等于永久保存个人数据：删除和阻断学习已经有明确的写入，接入注销流程留在后面。
