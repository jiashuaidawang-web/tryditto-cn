# V4-F01 设计

F01 记录一次配对之后发生了什么。它不改画像，不改权重，不调用模型，也不把拒绝、超时或未匹配说成性格。

## 边界

领域规则在 `cn.aiditto.domain.feedback.v4`。库表写入在 `RelationshipFeedbackStore`。没有控制器，也没有接到 `MatchFlow`。

`MatchFeedbackContext` 由服务端提供：配对编号、运行编号、周期、双方、是否已发布、匹配时的两份画像版本、三份策略版本，以及双方各自的反馈处理授权。客户端命令里没有这些版本，也没有对方身份的可写字段。可选的 `claimedCounterpartUserId` 只能用来核对，对不上就拒绝。

匹配授权看上下文里的 `published`。反馈处理授权是另一对布尔值。没有反馈授权时，这个人仍可以留下查看记录，也可以撤回；不能留下接受、拒绝、见面报告或继续意愿。

## 不重复计数

`MUTUAL_ACCEPTANCE_REACHED` 只在双方各有一条仍有效的接受之后出现一次。它的幂等键是 `match:{id}:mutual-acceptance`。`causationEventId` 是这两次接受的事件编号，用逗号连起来。重复提交同一次接受不会再写一条派生事件。

## 投影和学习

`FeedbackLedger.project` 把有效事件收成一次配对的事实。撤回使双方响应变成 `WITHHELD`，并且不再把旧接受算作可学习事实。处理授权撤回之后，接受事实还在，学习资格关掉。争议的见面报告保持 `CONTESTED`，投影不选边。

`FeedbackLearningEligibility` 只回答以后能不能读这些事实。`profileUpdateAllowed` 和 `negativeLabelAllowed` 固定为 false。本轮没有调用 `TasteRewriter` 或 `JudgeCalibrationService`。

## 还没接上的部分

`AccountService.requestDelete` 不调用 `eraseActor`。删除能力有独立方法和测试，生产注销流程还没接。V3 的接受、拒绝和会后表也不会自动写成新事件。这两处都留到后面的接入轮，避免在这一轮改生产行为。
