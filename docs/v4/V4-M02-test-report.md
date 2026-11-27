# V4-M02 测试报告

本地领域测试在实现提交前跑过：528 项，失败 0，错误 0，跳过 0。其中新增双向评分测试 17 项。200 人仿真仍是 83 对、34 人未匹配、822 条边、0 条硬约束违规。

新增测试不调用模型，也不连接生产库。原有 PostgreSQL 集成测试和 `V4QdrantIsolationIT` 仍由 `.github/workflows/v4-ci.yml` 执行。该工作流现在也会在 `feature/v4-bidirectional` 的推送上运行。

历史的 `RecallEvalIT`、`RecallExperimentIT`、`QdrantClientIT`、`VectorSyncIT` 继续是 NOT_RUN。

工程质量以这次分支上的 Actions 运行为准。运行完成前不把本地 528 写成 CI 验收。
