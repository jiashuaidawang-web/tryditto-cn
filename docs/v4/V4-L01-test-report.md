# V4-L01 测试报告

本地 `mvn -B -pl aiditto-domain test`：603 项，失败 0，异常 0，跳过 0。其中 `LabWorkbenchTest` 4 项，覆盖非法特征、双向评分、软偏好修改、单向和证据不足拒绝、硬约束、四方案召回，以及推理回放不加分。

`mvn -B -pl aiditto-app -am test -Dtest=MatchLabFilterTest`：2 项通过。开关关闭时请求不会进入后续链路。

`cd web && npm run build`：`tsc -b && vite build` 通过。

`MatchLabIT` 覆盖未登录、普通用户、管理员评分，以及评分后 `relationship_profile_version`、`matching_observation`、`relationship_feedback_event` 行数不变。本机没有 PostgreSQL，也没有 Docker。这项集成测试本地是 NOT_RUN，留给 CI 的 Postgres 16。

浏览器端到端是 NOT_RUN。没有数据库就起不来 API，五个页面无法在真实会话里点完。`docs/v4/V4-L01-screenshots/` 因此没有运行截图。静态页面不能代替这次操作。

前端没有现成的 Playwright 或 Vitest 脚本。本轮没有为了补一个空测试而新加框架。
