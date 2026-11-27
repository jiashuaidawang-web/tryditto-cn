# V4-M01 向量契约

自身向量和偏好向量分开。文本由 `RecallVectorText` 从已经过门控的特征生成：自身文本是排序后的爱好和 `self.*`，偏好文本是排序后的 `partner.*`。没有 persona 拼接，没有自由文本，没有未验证推断。

每条点记录：

- `userId`
- `kind`：`SELF` 或 `PREFERENCE`
- `profileVersion`
- `modelVersion`
- 输入摘要 SHA-256
- 向量

`VersionedVectorIndex` 的规则：

- 同一版本、同一模型、同一摘要再写一次，结果是 `IDEMPOTENT`。
- 更低的画像版本不能覆盖更高版本，结果是 `REJECT_STALE`。
- 同一版本但摘要或模型不同，结果是 `REJECT_CONFLICT`。旧的完整快照继续可见。
- 自身和偏好同一次提交。只算出一边、模型超时，或提交前发生撤回，搜索里都不会出现半份快照。
- 撤回记下代次和画像水位。延迟返回的旧任务不能把向量写回去。
- 新的授权代次才能再次同步。更老的撤回事件不能删掉新代次。
- 搜索只返回已经完整提交、且模型版本相同的点。`Point.vector()` 返回副本。

CI 和本轮消融使用 `HashingEmbeddingService`。它的版本号以 `hash-` 开头。这个向量只证明工程链路：幂等、拒旧、撤回后不可检索。它不是语义召回质量。

索引名必须是 `v4_it_` 或 `v4_synth_` 开头。`match_user_v1` 以及任何 `match_user` 前缀会被 `V4IndexNames` 拒绝。`V4QdrantIsolationIT` 使用独立集合，并要求 `AIDITTO_QDRANT_IT=1`。CI 的 `qdrant-isolation` 作业启动 `qdrant/qdrant:v1.12.5`，不连接生产集合。`RecallEvalIT`、`RecallExperimentIT`、`QdrantClientIT`、`VectorSyncIT` 仍然不在这条作业里。

真实 embedding 服务没有调用。真实向量能力是 NOT_RUN。
