# V4-F02 画像版本

确认事务复用 `FeatureRegistry.v1`、`ProfileEvidence`、`RelationshipProfile.of`、`SnapshotAdmission`、`ProfileVersionPolicy` 和 `RelationshipProfileService`。不另建一套用户画像，也不再由学习存储自己插入版本行。

写入前从数据库读取账号状态、生命周期代次和处理授权。命令里的授权布尔值和命令自己的代次不是当前事实。当前代次与命令里的期望代次不一致时，提案变成 `STALE_PROFILE`。问卷源版本来自 `profile.version`。快照版本只标识这一行画像，不写进 `source_profile_version`。

同一问卷源版本上的学习修订使用 `WriteMode.LEARNING_REVISION`。内容可以变，水位停在已经处理过的问卷源版本。确定性投影在同一源版本上改内容仍然是 `REJECT_CONFLICT`。模型修订仍走原来的 `MODEL_REVISION`。

服务端用确认编号 `confirm.{commandId}` 构造 `SELF_REPORTED` 证据。加载时先放当前问卷投影，再放确认表里的证据。一次确认只替换它点名的特征。问卷证据和其他未被修改的确认留在新快照里。`save` 和 `commitProvisional` 重新投影时使用同一段合并，所以仍合法的确认不会在下一次问卷或模型投影里消失。

同一确认、同一载荷再次提交得到 `KEEP_CURRENT`。同一 `commandId` 换成另一份载荷得到 `IDEMPOTENCY_CONFLICT`。已保存提案的过期时间以数据库那一行为准，确认命令不能把它改长。

画像版本和 `PROFILE_LEARNING` 消费凭证在同一个数据库事务里提交。失败回滚后两边都不留行。`GROUP_EVALUATION` 仍是独立用途，本轮不能构造。

撤回一条派生确认时，只把完全依赖已撤回事件的证据标成 `RETRACTED`，再按剩余证据写一版新快照。用户直接确认的值留下。不把整份旧画像恢复回来，也不覆盖之后的合法修改。

删除标记存在时，学习事务拒绝写入。`AccountService.requestDelete` 这一轮仍不调用这个事务。约定是先锁用户，调用 `eraseActor`，再调用 `RelationshipProfileService.erase`，提交时不跑学习器。
