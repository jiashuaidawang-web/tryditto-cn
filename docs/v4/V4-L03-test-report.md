# V4-L03 测试报告

`SandboxEvaluationTest` 检查 128 条冻结观察、未知计数没有并入负例、策略激活被拒绝、沙箱校准为 `INSUFFICIENT`、同一输入的当前引擎回放语义摘要一致、G1 与 G2 对照不写成策略增益、安全页生产状态为 `NO-GO`，以及缺失父运行返回 `HISTORY_UNAVAILABLE`。

本地 `mvn -B -pl aiditto-domain test` 是 612 项，失败 0，异常 0，跳过 0。其中 `SandboxEvaluationTest` 1 项。浏览器用例 `web/e2e/match-lab-evaluation.spec.ts` 覆盖冻结加载、未知不记成负例、沙箱 `INSUFFICIENT`、激活拒绝、历史快照、当前引擎重放、运行对照、安全页、跨工作区 403 和重置后的空运行列表。截图是 `11-strategy.png`、`12-replay.png`、`13-safety.png`。作业未绿之前，浏览器验收不算通过。本机没有 PostgreSQL，集成测试本地 NOT_RUN。

真实关系质量 NOT_RUN。真实策略提升 NOT_IDENTIFIED。生产启用 NO-GO。最终匹配算法质量 NO-GO。
