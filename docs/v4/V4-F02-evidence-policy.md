# V4-F02 证据优先级

`EvidencePriority` 把信号分成四档：

1. 用户明确选择的合法偏好。
2. 已经提交、尚未确认的修正提案。
3. 多条合法来源组成的待确认假设。
4. 关系结果、没有原因的行为、对方评价和不可学习的事件。这一档不进入画像。

`UNKNOWN` 不是负向。`WITHHELD` 不参与学习。矛盾的假设都保留，证据状态是 `CONTRADICTED`，两条都不自动生效。画像折叠沿用 `RelationshipProfile.of`：有效证据不一致时特征是 `CONTESTED`，不是后写的值覆盖先写的值。

模型推断不能覆盖用户已经确认的选择。本轮不产生模型推断。对方的事件不会被写成这个用户的 `SELF_REPORTED` 证据。

硬约束特征 `partner.age`、`partner.height`、`partner.education`、`partner.gender` 直接拒绝，原因是 `ILLEGAL_HARD_CONSTRAINT`。
