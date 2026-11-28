# V4-L01 测试报告

本地 `mvn -B -pl aiditto-domain test`：605 项，失败 0，异常 0，跳过 0。`LabWorkbenchTest` 是 6 项，覆盖目录身份摘要和内容摘要、来源提交、沙箱候选、相同输入的结果摘要，以及两个线程用同一期望版本更新时只保留一个版本。

`mvn -B -pl aiditto-app -am test -Dtest=MatchLabFilterTest`：5 项通过。关闭、只开开关、生产标记，以及命名的 `ci` 环境都有断言。

`cd web && npm run build`：`tsc -b && vite build` 通过。

`MatchLabIT` 增加了跨工作区请求必须返回 403。本机没有 PostgreSQL，也没有 Docker，这项集成测试本地仍是 NOT_RUN，由 CI 的 Postgres 16 执行。

浏览器验收改由工作流作业 `match-lab-e2e` 执行。它使用该次运行自己的数据库口令，启动刚构建的应用和 `vite preview`，再用 Playwright Chromium 完成登录、五个页面、普通用户拒绝和跨工作区拒绝。功能关闭由同一工作流里的 `MatchLabFilterTest` 证明，因为正在跑浏览器的那一个进程必须处于打开状态。截图、HTML 报告和两端日志上传为 artifact `match-lab-e2e`。这个作业失败时，不能把 L01 浏览器验收写成通过，也不能用前端构建代替它。合并前的作业地址写在 `V4-PROJECT-STATUS.md`。
