# V4-I02 安全审计

`ShadowPlanAudit` 不读取求解器的 `executablePlan` 或图上的通过标志。

它重新检查：

- 配对用户来自这次冻结的主体。
- 冻结授权和当场重读的授权都是 `GRANTED`。
- 当场账号状态是 `active`。
- 画像版本、生命周期代次和输入哈希一致。
- `CompatibilityContext.blockReasons` 里的硬约束。
- 边在求解图中、不是数学边、并且推荐资格为真。
- 每个用户最多一次。
- 不在拉黑、举报或历史配对里。
- 求解期间 `match`、`week`、`match_copy` 的行数没有增加。

任一失败都写成 `SAFETY_REJECTED` 或 `EXPIRED`，配对表保持为空。

实验上下文会把主体标成已授权。审计不使用这个标记，只使用入池时冻结的 `GRANTED` 和事务里的重读。
