# V4-F01-FINAL 审计

基准是 `feature/v4-feedback-events` 的 `9820f106172f2a75562d1d25e3c8ab2f98a7eed8`。本轮只加 V19，不改 V18。历史十个案例的原有比例保持不变。生产 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom` 和 `AccountService` 没有改。

## 更正权限

参与者只能更正自己仍然有效的事件。更正对方的事件得到 `NOT_EVENT_OWNER`。更正 `MATCH_PUBLISHED`、`MATCH_EXPIRED`、`MUTUAL_ACCEPTANCE_REACHED` 得到 `CANNOT_CORRECT_SYSTEM`。参与者命令不能把来源写成系统派生事件，得到 `FORGED_SYSTEM_EVENT`。

`FEEDBACK_CORRECTED` 只能指向自己的响应、安排、继续意愿、偏好更正或上一次更正。查看事件不能被替换，得到 `ILLEGAL_REPLACEMENT`。接受只能替换自己的拒绝或更正。拒绝只能替换自己的接受或更正。已经失效的目标得到 `NOT_EFFECTIVE`。撤回和处理授权撤回仍然得到 `REVOCATION_STANDS`，不能被重新打开。

同一幂等键如果类型、行为人、原因、被替换事件或发生时间不同，得到 `IDEMPOTENCY_CONFLICT`。相同命令重复提交仍返回原来的事件。授权撤回之后再提交原来的接受键，返回的仍是原来的接受，撤回保持有效。

## 生命周期和删除

`append` 按用户编号从小到大取事务级咨询锁，再锁这次配对，然后才读删除标记和事件。`eraseActor` 先锁这个用户，再写删除标记，再收回该用户已经应用的消费凭证，然后删除他作为行为人的事件。锁顺序固定，删除和后到的写入不会交叉插入。

任一参与者已经有删除标记时，后续写入得到 `ERASED`。旧的上下文即使仍写着学习同意，也不能在撤回事件之后重新打开学习资格。

反馈处理授权和学习授权是两件事。学习授权为否时，接受和拒绝仍然可以写入。学习资格会带上 `NO_LEARNING_CONSENT`，这条配对不能被消费。反馈处理授权为否时，接受仍然被拒绝，原因仍包含 `NO_FEEDBACK_CONSENT`。

`AccountService.requestDelete` 这一轮不调用删除。以后的接入约定是：`RelationshipFeedbackStore.FUTURE_ACCOUNT_DELETION`。注销流程先锁用户，调用 `eraseActor`，随账号事务提交，并且不启动学习器。

## 学习消费凭证

`consumed_at` 仍只服务原来的单次标记测试。新的用途记在 `relationship_feedback_consumption`。一行的键是事件、消费者类型和策略版本。字段是生命周期代次、授权代次、处理状态、消费时间和派生结果编号。

消费前重新检查事件类型、事件是否仍可学习，以及这个行为人当前的反馈处理授权和学习授权。`learning_blocked = false` 本身不能通过检查。被更正、删除、撤回、授权撤回或不适用的事件不能消费。

`PROFILE_LEARNING` 和 `GROUP_EVALUATION` 的用途键不同。本轮构造 `GROUP_EVALUATION` 得到 `F03_NOT_IMPLEMENTED`，F03 没有实现。同一用途的 `APPLIED` 或 `REVOKED` 不能再应用。`FAILED` 可以改成一次 `APPLIED`。凭证插入和业务事务一起回滚。处理授权撤回在同一把用户锁里把已应用凭证改成 `REVOKED`，派生结果编号留着。

## 可信匹配快照

`MatchFeedbackContext.experiment` 只构造 `SYNTHETIC`。`production()` 得到 `PRODUCTION_CONTEXT_NOT_WIRED`。快照带配对编号、两侧参与者、发布状态、两侧画像版本、生命周期代次、授权代次、配对周期、三种策略版本、反馈处理授权和学习授权。

客户端命令仍然不能提供画像版本、策略版本或对方身份。回放时事件上的版本和当前快照不一致，资格失败，原因仍是 `SNAPSHOT_MISMATCH` 或 `STRATEGY_MISMATCH`。本轮没有接入生产发布接口。

## 状态机

未发布时接受仍是 `NOT_AUTHORIZED`。发布后超时再接受得到 `EXPIRED`。双方接受之前安排见面，或还没有安排就取消，得到 `ILLEGAL_SCHEDULE`。

`MUTUAL_ACCEPTANCE_REACHED` 只有在原因集合正好等于当前两条有效接受时，投影才算双方接受。更正其中一次接受后，双方接受消失。新的有效接受出现后，另一次派生事件重新成立。旧的派生事件还在账本里，但不再充当当前事实。

一侧报告未见面是 `ONE_REPORTED_NOT_ATTENDED`。两侧都报告未见面是 `BOTH_REPORTED_NOT_ATTENDED`。这些报告的证据状态是 `SELF_REPORTED`。争议保持 `CONTESTED`。单方自述不会升成系统验证事实。安排见面的证据状态也改成 `SELF_REPORTED`。

迟到的查看可以追加。它不改已经生效的接受或超时。

## 指标

原有比例和十个案例的分数保持不变。新增至少一方响应率、双方均响应率、用户级接受率、约会报告覆盖率和后续意愿覆盖率。双方确认见面率和双方继续意愿率沿用 `mutual_attendance_rate` 和 `mutual_continue_rate`。

`UNKNOWN`、`EXPIRED`、`WITHHELD` 不变成拒绝，也不算约会报告已覆盖。未成熟配对进入 `immature`，不进入分母，也不算失败。`summarize` 保留原来允许重复 `matchId` 的实验入口。`summarizeDistinct` 对重复 `matchId` 抛出 `DUPLICATE_MATCH`。窗口定义写在 `V4-F01-metrics.md`。

## 本地执行

`mvn -B -pl aiditto-domain test` 的 Surefire XML 合计 576 项，失败 0，异常 0，跳过 0。相对验收时的 569 项，新增 7 项在 `FeedbackLedgerFinalTest`。`aiditto-app` 测试编译通过。本机没有 PostgreSQL，新增的 `FeedbackEventIT` 方法只在 CI 执行。方法名已经写进 `.github/workflows/v4-ci.yml` 的必需清单。

CI 地址在这次提交推送并完成后补记。
