# V4-M01 测试报告

领域测试在工作区 `/Users/null/IdeaProjects/AI/tryditto-cn-v4-recall` 执行：`mvn -pl aiditto-domain test`。退出码 0。既有匹配、硬约束、画像和 P02 测试一起通过。新增测试也通过。

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `V4RecallContractTest` | 通过 | 门控、未知不算一致、正向不等于反向、生产集合被拒绝、版本幂等和并发旧写、自身/偏好文本分离 |
| `RecallBenchTest` | 通过 | 100 人、来源标记、相关不少于 100、硬否决不与相关并存 |
| `RecallCasesTest` | 通过 | 6 个案例的路线、融合、年龄拒绝、预算和方向 |
| `RecallAblationTest` | 通过 | 选定路线优于 B0，保留集门槛，安全计数为 0，合法全集上限 1 |
| `V4VectorSyncTest` | 通过 | 哈希同步幂等、旧版本拒绝、撤回后索引为空、200 人人口摘要未变 |
| `V4QdrantIsolationIT` | NOT_RUN | 需要 `AIDITTO_QDRANT_IT=1`。本次没有启动隔离 Qdrant |
| `RecallEvalIT` | NOT_RUN | 仍不在 CI 过滤列表里，且需要 Qdrant 变量 |
| `RecallExperimentIT` | NOT_RUN | 需要 `AIDITTO_RECALL_MATRIX=1` |
| `QdrantClientIT` | NOT_RUN | 需要 `AIDITTO_QDRANT_IT=1` |
| `VectorSyncIT` | NOT_RUN | 需要 Qdrant 变量。它测的是 V3 五路，不是本轮质量 |

`V4VectorSyncTest` 已写进 `.github/workflows/v4-ci.yml` 的必跑清单。领域新测试由 `mvn -pl aiditto-domain test` 全量执行，不靠名单点名。`feature/v4-recall` 的 push 会触发这条工作流。

哈希同步测试断言版本号以 `hash-` 开头。通过只说明工程链路，不说明语义近邻。

## 结论

| 项 | 结论 |
| --- | --- |
| 工程质量 | GO |
| 离线召回质量 | GO，仅限这批合成标注和冻结门槛 |
| 真实向量能力 | NOT_RUN |
| 生产启用 | NO-GO |
| 最终匹配算法质量 | NO-GO |

剩余风险：V3 的 taste 覆盖写和反馈先验还在原实验链里，只是没有接进 V4。隔离 Qdrant 集成这次没有跑。保留集 Precision 因未标注跨集命中而为空。互补规则只有两条，换一批关系就不会有 R5 的增益。生产 `WeekRunner` 仍然扫描全城，本轮没有把召回接进去。
