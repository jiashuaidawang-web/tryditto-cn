# V4-F02 画像版本

确认事务复用 `FeatureRegistry.v1`、`ProfileEvidence`、`RelationshipProfile.of`、`SnapshotAdmission` 和 `ProfileVersionPolicy`。不另建一套用户画像。

写入前重新核对当前画像版本、生命周期代次、反馈处理授权、学习授权、来源没有撤回、账号没有删除标记，以及确认动作属于这个用户。服务端用确认编号 `confirm.{commandId}` 构造 `SELF_REPORTED` 证据。旧值不一致时提案变成 `STALE_PROFILE`，不覆盖。

同一确认再次提交得到 `KEEP_CURRENT`，不新增版本，也不再次消费。旧提案遇到已经前进的版本时得到 `STALE_PROFILE`。调用方可以用新版本重新评估，系统不自动覆盖。

画像版本和 `PROFILE_LEARNING` 消费凭证在同一个数据库事务里提交。失败回滚后两边都不留行。`GROUP_EVALUATION` 仍是独立用途，本轮不能构造。

撤回一条已经写入的确认时，只把那条证据标成 `RETRACTED`，再折叠剩余证据。其他特征的后续合法确认留在新快照里。不把整份旧画像恢复回来。

删除标记存在时，学习事务拒绝写入。它不能把已经删掉的反馈重新学回画像。`AccountService.requestDelete` 这一轮仍不调用这个事务。
