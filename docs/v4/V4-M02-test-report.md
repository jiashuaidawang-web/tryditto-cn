# V4-M02 测试报告

实现提交 `17d0740f04af3f0377c286710124d464666ccc0a`。

Actions：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45972256911

四个作业都是 success：`domain-tests`、`integration-tests`、`qdrant-isolation`、`test-reports`。作业日志没有公开下载权限，所以下面的条数来自同一提交的本地运行，不是从日志抄出来的。

本地领域测试 528 项，失败 0，错误 0，跳过 0。其中新增双向评分测试 17 项。200 人仿真仍是 83 对、34 人未匹配、822 条边、0 条硬约束违规。

新增测试不调用模型，也不连接生产库。历史的 `RecallEvalIT`、`RecallExperimentIT`、`QdrantClientIT`、`VectorSyncIT` 继续是 NOT_RUN。

工程质量 GO。这个 GO 只表示上述 CI 作业通过。离线评分质量、召回端到端、真实模型、生产启用和最终匹配算法另见评估报告。
