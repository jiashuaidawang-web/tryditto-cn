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
- 同一版本但摘要不同，结果是 `REJECT_CONFLICT`。
- `delete` 同时去掉自身和偏好，搜索不再返回这个人。
- 搜索只比较同一 `modelVersion`。

CI 和本轮消融使用 `HashingEmbeddingService`。它的版本号以 `hash-` 开头。这个向量只证明工程链路：幂等、拒旧、撤回后不可检索。它不是语义召回质量。

索引名必须是 `v4_it_` 或 `v4_synth_` 开头。`match_user_v1` 以及任何 `match_user` 前缀会被 `V4IndexNames` 拒绝。`V4QdrantIsolationIT` 只在 `AIDITTO_QDRANT_IT=1` 时连接本地 Qdrant，集合名带 `v4_it_`。这次没有设置该变量，结果是 NOT_RUN。

真实 embedding 服务没有调用。真实向量能力是 NOT_RUN。
