# V4-F02 提案

`ProfileLearningProposal` 带有提案编号、用户、画像版本、生命周期代次、特征、分区、旧值、建议值、操作符、强度、来源事件、证据类型、证据状态、理由、策略版本、状态、创建时间和过期时间。

状态是 `PENDING_CONFIRMATION`、`CONFIRMED`、`REJECTED`、`EXPIRED`、`RETRACTED`、`SUPERSEDED`、`STALE_PROFILE`、`INVALID_EVIDENCE`。

`matchingMayRead` 只在 `CONFIRMED` 时为真。待确认假设即使已经保存，也不进入正式匹配评分。

明确修正命令带有合法 `featureCode`、旧值、用户选择的新值、强度、操作符、确认或拒绝、当前画像版本、生命周期和两项授权。F01 的原因类别不能当成这个命令的新值。确认动作只能来自这个用户。模型和其他用户不能代确认。
