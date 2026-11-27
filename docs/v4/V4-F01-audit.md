# V4-F01 现有反馈审计

只读现有链路。本轮没有改 `MatchFlow`、`FeedbackLearningService`、`TasteRewriter` 或生产评分。

## 反馈从哪里来

用户操作进 `MatchController`，再进 `MatchFlow`。接受、拒绝、选时段、签到、取消、见面后反馈、举报都在这里。拒绝原因是 `notMyType`、`reasonsOff`、`tooFar`、`noTime`、`knowThem`、`other`。拒绝备注和反馈自由文本都截到 500 字，自由文本加密进 `feedback.comment_enc`。

`FeedbackLearningService.scanAndRewrite` 读取还没被 `taste_log` 消费的 `feedback` 行，解密备注，然后调用 `TasteRewriter`、`PairMemoryService` 和 `JudgeCalibrationService`。`TasteRewriter` 会走 `LlmGateway`。这正是 F01 这一轮不调用的路径。

## 十个问题

1. 现有反馈有两个来源。配对过程写在 `match` 和 `match_side`：接受、拒绝、时段、签到、取消、超时。见面之后的表是 `feedback`：是否见面、1–5 分、是否再见面、是否感到安全、加密备注。学习任务只扫 `feedback`，不扫接受和拒绝。
2. `feedback` 挂在已经存在的 `match_id` 上，是一次配对的会后表，不是召回候选上的打分。接受和拒绝则是发布后的配对响应，也不是推荐候选。
3. `match_side` 和 `feedback` 的主键都是 `(match_id, user_id)`，所以 A 和 B 各有一行。方向在表结构里是分开的。学习把这行写成反馈者自己的口味变化，双方都拒绝时还会给这对人写无向的 `forbidden`。
4. 接受和拒绝在 `match_side.response`，取值 `pending`、`accept`、`decline`。超时是配对状态上的截止，不是 `feedback` 里的一种取值，也没有单独标成「不是负反馈」。
5. 计划见面在 `date_plan` 和配对状态（`confirmed`、`cancelled`）。实际见面在 `feedback.met`：`met`、`noShow`、`iSkipped`。两套事实都存在，但是会后表只有填写它的那个人的说法。
6. 每人一行，所以单方反馈可以存在。没有「双方一致」和「双方矛盾」两种状态。一个人填 `met`、另一个人填 `noShow` 时，学习仍按各自行处理，不会把这对报告留成争议。
7. `taste_log` 上有 `(user_id, trigger_feedback)` 唯一索引，`trigger_feedback` 存的是 `match_id`。同一用户同一次配对的会后反馈不会学第二次。接受、拒绝和改口不在这个唯一键里。
8. 会后反馈会改口味权重，并可能把双方都拒绝写成永久禁止。学习输入里没有匹配当时的画像版本，也没有兼容策略、目标或候选策略版本。自由文本会解密后进入改写。一次拒绝可以被写成口味增量。
9. 不能。`TasteRewriter.Input` 有用户、配对、分数、再见面、见面枚举、备注和裁判文本。没有双方在匹配时的画像版本，也没有本轮要求的三种策略版本。
10. `AccountService.requestDelete` 把账号标成删除中、收回会话，并让发布闸门放开这个人。它不删除 `feedback`、`taste_log` 或 `ai_pair_memory`。

## 本轮怎么复用

不新建第二套会后表，也不把 V3 的接受接口改接到新表。新的 `relationship_feedback_event` 只表达「发生了什么」，给隔离测试使用。生产匹配、生产反馈读取和学习任务都没有接上。
