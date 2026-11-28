# V4-L01 测试报告

本地 `mvn -B -pl aiditto-domain clean test`：603 项，失败 0，异常 0，跳过 0。其中 `LabWorkbenchTest` 仍是 4 项。这一轮把目录字段、非法特征状态、行为层、开发集与保留集召回、合法池、`NO_ROUTE_SIGNAL` 和推理的无依据列表补进了原有用例。

`mvn -B -pl aiditto-app -am test -Dtest=MatchLabFilterTest`：2 项通过。开关关闭时请求不会进入后续链路。

`cd web && npm run build`：`tsc -b && vite build` 再次通过。

`MatchLabIT` 覆盖未登录、普通用户、管理员评分，以及评分后 `relationship_profile_version`、`matching_observation`、`relationship_feedback_event` 行数不变。本机没有 PostgreSQL，也没有 Docker，所以这项集成测试本地是 NOT_RUN。CI 的 Postgres 16 跑过：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46122030761 ，提交 `1534812797aee4e8cb8490ac625b5ae51b1707a2`，四个作业 success。`MatchLabIT` 和 `MatchLabFilterTest` 都在必需清单里。

浏览器端到端是 NOT_RUN。没有数据库就起不来 API，五个页面无法在真实会话里点完。`docs/v4/V4-L01-screenshots/` 因此没有运行截图。静态页面不能代替这次操作。

前端没有现成的 Playwright 或 Vitest 脚本。本轮没有为了补一个空测试而新加框架。
