# V4-L02 测试报告

本地 `mvn -B -pl aiditto-domain test`：611 项，失败 0，异常 0，跳过 0。其中 `SandboxLoopFinalTest` 3 项覆盖完整画像保留、过期提案、反馈来源绑定、撤回和授权撤回、历史快照以及 G1/G2 策略绑定。`SandboxLoopTest` 仍是 3 项。

`cd web && npm run build` 通过。新增页面在现有 Match Lab 路由里。

`MatchLabIT` 增加了沙箱全局匹配、数学夹具拒绝进入反馈、学习确认，以及确认后 `relationship_profile_version`、`matching_observation`、`relationship_feedback_event` 和 `"match"` 行数不变。本机没有 PostgreSQL，这项仍由 CI 的 Postgres 16 执行。

浏览器用例 `web/e2e/match-lab-loop.spec.ts` 覆盖全局匹配、候选边进入反馈、双方接受、派生互相接受、确认提案和重新匹配，并保存 `08-global.png`、`09-feedback.png`、`10-learning.png`。状态只从时间线和提案段落读取，不从原始 JSON 读取。它和 L01 用例一起由 `match-lab-e2e` 执行。L01 用例先重置当前管理员工作区，避免和闭环共用同一份内存画像。

浏览器证据提交 `5b71e16720a2c4c787d6dd1afd03f7d1de1ff4a0`。CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46129223021 ，`domain-tests`、`integration-tests`、`qdrant-isolation`、`match-lab-e2e`、`test-reports` 都是 success。Artifact `match-lab-e2e` 的编号是 `15256154224`，页面是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46129223021 。更早的闭环作业 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46127888620 和 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46128329375 的浏览器步骤失败，不记为通过。远程 Surefire 压缩包没有公开下载权限，不另报一份远程测试总数。

真实关系质量 NOT_RUN。生产启用 NO-GO。最终匹配算法质量 NO-GO。
