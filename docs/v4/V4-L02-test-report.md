# V4-L02 测试报告

本地 `mvn -B -pl aiditto-domain test`：608 项，失败 0，异常 0，跳过 0。其中 `SandboxLoopTest` 3 项，覆盖不合格边不进求解器、相同输入的配对可重复、数学夹具不能打开反馈、反馈更正、跨用户确认被拒绝，以及案例 B、C、D、E、F 的预期观察。

`cd web && npm run build` 通过。新增页面在现有 Match Lab 路由里。

`MatchLabIT` 增加了沙箱全局匹配、数学夹具拒绝进入反馈、学习确认，以及确认后 `relationship_profile_version`、`matching_observation`、`relationship_feedback_event` 和 `"match"` 行数不变。本机没有 PostgreSQL，这项仍由 CI 的 Postgres 16 执行。

浏览器用例 `web/e2e/match-lab-loop.spec.ts` 覆盖全局匹配、候选边进入反馈、双方接受、派生互相接受、确认提案和重新匹配，并保存 `08-global.png`、`09-feedback.png`、`10-learning.png`。它和 L01 用例一起由 `match-lab-e2e` 执行。作业未绿之前，浏览器验收保持 NOT_RUN，不能用前端构建代替。

真实关系质量 NOT_RUN。生产启用 NO-GO。最终匹配算法质量 NO-GO。
