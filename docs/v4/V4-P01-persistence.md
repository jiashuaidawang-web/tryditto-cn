# V4-P01 持久化与版本

迁移是 `api/aiditto-app/src/main/resources/db/migration/V14__relationship_profile.sql`。它只新建 `relationship_profile_version`。`profile` 和 `user_taste` 的表名、列名都不改。

匹配代码不读这张表。

## 表

一行是一份不可变快照。主键 `(user_id, version)`。

| 列 | 含义 |
| --- | --- |
| user_id / version | 用户和单调版本。版本从 1 起，只增 |
| schema_version | 1 |
| feature_registry_version | `features-1` |
| source_profile_version | 来源 `profile.version`。不进入哈希 |
| input_hash | canonical JSON 的 SHA-256，固定 64 位小写十六进制 |
| self_profile / preference_profile / behavior_profile / memory_profile / evidence | 各节的 canonical JSON。不是随机键序的另一份序列化 |
| created_at | 写入时间。不进入哈希 |
| is_current | 每个用户最多一行 true |
| retracted_at | 撤回时间。空表示这份输入仍有效 |

两个部分唯一索引：

- `uq_rel_profile_current`：`(user_id) WHERE is_current`
- `uq_rel_profile_active_hash`：`(user_id, input_hash) WHERE retracted_at IS NULL`

因此同一份有效输入不能存两行，也不能有两个当前版本。撤回后这份哈希可以在将来重新写入，但不会自动把旧行升回当前。

哈希覆盖用户、schema、注册表版本，以及四节和证据的语义字段。Map 和枚举集合都按规范顺序写出。生成时间、随机 id、`source_profile_version` 不参与。

## 写入

`RelationshipProfileService.save` 在用户行锁里做决定，锁与发布闸门用的是同一把 `UserLock`。

| 条件 | 结果 |
| --- | --- |
| 用户状态是 `deleting` 或 `purged` | 拒绝。不写新版本 |
| 哈希与当前行相同 | 返回当前行。源版本号更高也不再插一行 |
| 源版本小于当前行，且内容不同 | 拒绝。旧输入不能覆盖新来源 |
| 源版本相同但内容不同 | 拒绝 |
| 这份哈希已经有未撤回的非当前行 | 拒绝。不把旧内容重新设为当前 |
| 源版本更高且内容不同 | 插入 `max(version)+1`，旧的 `is_current` 先清掉 |

`createdAt` 截断到毫秒，和现有 `TIMESTAMP(3)` 一致。

## 生命周期

| 动作 | 行为 | 本轮是否接到账户流程 |
| --- | --- | --- |
| 保存 | 只追加。不改旧行的 JSON | 没有接到入驻。生产匹配不会调用 |
| 撤回 `retractCurrent` | 当前行 `is_current=false` 且写入 `retracted_at`。内容还在，但不能当当前特征 | 不改 `AccountService`，避免动到 S01 的注销与发布竞态。以后处理授权撤回应调用它 |
| 删除 `erase` | 删掉该用户全部版本，包括已撤回 | 现有注销只把状态改成 `deleting` 并登记 `purge_at`，还没有清除任务。将来的清除必须调用 `erase`。本轮测试直接调用 |
| 反馈修正 | 新证据追加，矛盾保留为 `CONTESTED`，撤回的证据失效但不物理覆盖 | 不接 `FeedbackLearningService`。留给后续，不在本轮生成观察 |

注销中的用户不能再保存。`erase` 不删除 `profile` 和 `user_taste`，那两张表仍走原来的账户与隐私路径。

精确地址、联系方式、生日、性别选项和其余敏感原文不进这张表的 JSON。性别期望只存 `profile.expected_genders_enc` 这个引用。
