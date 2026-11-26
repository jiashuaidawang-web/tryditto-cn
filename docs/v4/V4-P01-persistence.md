# V4-P01 持久化与版本

迁移是 `api/aiditto-app/src/main/resources/db/migration/V14__relationship_profile.sql`。它只新建 `relationship_profile_version`。`profile` 和 `user_taste` 的表名、列名都不改。已经提交的 V14 不覆盖。水位和授权在后续的 `V15__relationship_profile_lifecycle.sql`。

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
| retracted_at | 用户撤回时间。空表示这份输入没有被撤回 |
| superseded_at | V15 增加。内容被更新的版本替换的时间。空且未撤回，才是仍在生效的那一行 |

V14 的两个部分唯一索引是：

- `uq_rel_profile_current`：`(user_id) WHERE is_current`
- `uq_rel_profile_active_hash`：`(user_id, input_hash) WHERE retracted_at IS NULL`

V15 删掉第二个索引，改成 `uq_rel_profile_live_hash`：`(user_id, input_hash) WHERE retracted_at IS NULL AND superseded_at IS NULL`。同一时刻仍然只能有一行当前版本，也只能有一行未撤回且未替换的相同哈希。用户从 A 改到 B 再改回 A 时，旧的 A 先标 `superseded_at`，新的 A 可以再插一行。两个都还生效的相同哈希仍然被唯一索引拒绝。用户撤回走 `retracted_at`，不用 `superseded_at`。

`relationship_profile_lifecycle` 每个用户一行，不放进不可变快照：

| 列 | 含义 |
| --- | --- |
| latest_processed_source_version | 已经处理过的最高 `profile.version`。不是快照行上的 `source_profile_version` |
| generation_epoch | 允许写入的代次。撤回处理授权或 `retractCurrent` 都会加一 |
| processing_authorization | `granted` 或 `revoked`。只表示能不能做画像处理 |
| model_export_authorization | `granted` 或 `revoked`。只表示能不能把内容发给外部模型。它不授予处理，也不授予匹配 |
| updated_at | 这行元数据的更新时间 |

匹配同意仍在 `matching_consent`。画像服务不读那张表。

哈希覆盖用户、schema、注册表版本，以及四节和证据的语义字段。Map 和枚举集合都按规范顺序写出。生成时间、随机 id、`source_profile_version` 不参与。

## 写入

`RelationshipProfileService.save` 在用户行锁里做决定，锁与发布闸门用的是同一把 `UserLock`。

| 条件 | 结果 |
| --- | --- |
| `processing-enabled` 不是 true | 拒绝。生产配置固定 false |
| 用户状态是 `deleting` 或 `purged` | 拒绝。不写新版本，也不能读取 |
| 没有处理授权，或代次与当前 `generation_epoch` 不同 | 拒绝。匹配同意和模型外发授权都不能代替 |
| 声明的源版本不等于库里的 `profile.version` | 拒绝。不采用调用方自己报的版本 |
| 声明的源版本低于已处理水位 | 拒绝。当前行还是原来的内容，水位不变 |
| 服务端投影的哈希与当前生效行相同 | 返回当前行，不新插快照。水位推进到这次的源版本 |
| 同一个源版本、确定性投影、哈希不同 | 拒绝。这不是模型修订 |
| `saveModelRevision` | 一律拒绝。领域策略里它和确定性冲突是两种结果，服务本轮不实现写入 |
| 提交的哈希不是服务端按已存储答案投影出来的哈希 | 拒绝 |
| 源版本更高且内容不同 | 插入 `max(version)+1`。旧的生效行先写 `superseded_at` 并清掉 `is_current`，再推进水位 |

`createdAt` 截断到毫秒，和现有 `TIMESTAMP(3)` 一致。

## 生命周期

| 动作 | 行为 | 本轮是否接到账户流程 |
| --- | --- | --- |
| 保存 | 只追加。不改旧行的 JSON。相同内容不追加 | 没有接到入驻。生产开关关闭，匹配不会调用 |
| 授予处理 `grantProcessing` | 没有记录时插入代次 1。已经授予则返回原代次。从撤回恢复时代次加一 | 没有接到用户操作 |
| 撤回处理 `revokeProcessing` | 授权改为 `revoked`，代次加一，当前行写 `retracted_at` | 没有接到匹配同意的撤回 |
| 撤回当前 `retractCurrent` | 当前行写 `retracted_at`，代次加一。处理授权保持原样。旧代次不能把行再插回来 | 不改 `AccountService` |
| 模型外发 `grantModelExport` | 只改 `model_export_authorization` | 不授予处理，也不授予匹配 |
| 删除 `erase` | 删掉该用户全部版本，处理授权改为 `revoked`，代次加一。生命周期行保留 | `AccountService` 只把状态改成 `deleting` 并登记 `purge_at`。没有清除任务调用 `erase`。本轮测试直接调用 |
| 反馈修正 | 新证据追加，矛盾保留为 `CONTESTED`，撤回的证据失效但不物理覆盖 | 不接 `FeedbackLearningService`。留给后续，不在本轮生成观察 |

注销中的用户不能再保存。`erase` 不删除 `profile` 和 `user_taste`，那两张表仍走原来的账户与隐私路径。

精确地址、联系方式、生日、性别选项、证件号、学号、私人 URL 和其余自由文本不进这张表的 JSON。性别期望只存 `profile.expected_genders_enc`。职业和开场白只存 `onboarding_answer.occupation` 与 `onboarding_answer.first_message`。
