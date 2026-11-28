# V4-L02 设计

Match Lab 在同一套 React 应用和同一个管理员工作区里增加三条沙箱操作：全局匹配、反馈事件、画像学习。核心算法仍是已经验收的 `V4GlobalMatchingEngine`、`FeedbackLedger` 和 `ProfileLearningEngine`。页面不重写这些状态机。

`SandboxLoop` 挂在当前 `LabWorkbench` 上。它只接收工作区里的 `CompatibilitySubject`。传入不存在的用户编号会拒绝。求解使用完整合法池 E，默认目标是 G2。只有 `COMPLETE`、安全违规为 0、`executablePlan` 且覆盖完整合格图的结果可以打开反馈。数学夹具、`SAFETY_REJECTED`、`CAP_REACHED`、`TIMEOUT`、`ABORTED` 和截断图都不能变成可继续的匹配计划。

反馈账本由服务端追加 `MATCH_PUBLISHED`。互相接受由账本在双方接受后派生。页面不能提交系统事件类型。学习确认走 `ProfileLearningEngine.apply`。写入工作区画像时保留其他已确认特征，并带期望版本。旧匹配上下文里的画像版本不随后续学习改写。重新匹配是一条新的运行记录，`parentRunId` 指向原来的全局匹配。

内存工作区重启后丢失。这里不写 `match`、`relationship_profile_version`、`relationship_feedback_event` 或 `matching_observation`。不调用 `WeekRunner` 或 `PublishGuard`。不调用真实模型。
