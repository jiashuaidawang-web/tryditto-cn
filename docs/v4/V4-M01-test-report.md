# V4-M01 测试报告

领域测试在工作区 `/Users/null/IdeaProjects/AI/tryditto-cn-v4-recall` 执行：`mvn -pl aiditto-domain test`。退出码 0。本次是 511 项，其中原有 506 项仍在，另加 5 项审计测试。200 人仿真仍是 83 对、34 人未匹配、822 条边、0 条硬约束违规。`V4VectorSyncTest` 6 个方法通过。

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| `V4RecallContractTest` | 通过 | 门控、未知不算一致、正向不等于反向、生产集合被拒绝、版本幂等和并发旧写、自身/偏好文本分离 |
| `RecallBenchTest` | 通过 | 100 人、来源标记、相关不少于 100、硬否决不与相关并存 |
| `RecallCasesTest` | 通过 | 6 个案例的路线、融合、年龄拒绝、预算和方向 |
| `RecallAblationTest` | 通过 | 选定路线优于 B0，保留集门槛，安全计数为 0，合法全集上限 1 |
| `V4VectorSyncTest` | 通过 | 哈希同步幂等、旧版本拒绝、撤回后索引为空、200 人人口摘要未变 |
| `RecallAuditTest` | 通过 | 代次水位、延迟写入、矛盾特征、缺结果分母、64 位有向对、挑战集安全计数 |
| `V4VectorSyncTest` 新增方法 | 通过 | 第二次 embedding 失败不发布；写入中途撤回后旧向量不可见 |
| `V4QdrantIsolationIT` | CI 通过 | `qdrant-isolation` 成功。本地没有单独启动 Qdrant。哈希向量不是语义评估 |
| `RecallEvalIT` | NOT_RUN | 仍不在 CI 过滤列表里，且需要 Qdrant 变量 |
| `RecallExperimentIT` | NOT_RUN | 需要 `AIDITTO_RECALL_MATRIX=1` |
| `QdrantClientIT` | NOT_RUN | 需要 `AIDITTO_QDRANT_IT=1` |
| `VectorSyncIT` | NOT_RUN | 需要 Qdrant 变量。它测的是 V3 五路，不是本轮质量 |

`V4VectorSyncTest` 已写进 `.github/workflows/v4-ci.yml` 的必跑清单。领域新测试由 `mvn -pl aiditto-domain test` 全量执行，不靠名单点名。

上一阶段实现提交是 `6156669736cfcdd61c4c430c52133b4f874d3f13`，工作流 [V4 CI run 15](https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45959918120)。

本轮修复提交 `9e13512a75463a94ed54fae1f41a6184150d02d5`。工作流 [V4 CI run 16](https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45966591316) 结论 success。`domain-tests`、`integration-tests`、`qdrant-isolation`、`test-reports` 都成功。`qdrant-isolation` 使用 `qdrant/qdrant:v1.12.5`，集合名以 `v4_it_` 开头。它的「Reject a skipped Qdrant test」步骤成功，所以 `V4QdrantIsolationIT` 不是跳过。哈希向量只证明写入、过滤和撤回，不证明语义近邻。

`RecallEvalIT`、`RecallExperimentIT`、`QdrantClientIT`、`VectorSyncIT` 仍不在 CI 名单里，保持 NOT_RUN。

哈希同步测试断言版本号以 `hash-` 开头。通过只说明工程链路，不说明语义近邻。

## 结论

| 项 | 结论 |
| --- | --- |
| 工程质量 | GO |
| 离线召回质量 | GO，按冻结门槛：100 人回归仍达标，挑战集开发集 B3 高于随机池且不低于生产 Scorer。绝对覆盖是 67/158，保留集 7/45 没有优于随机池 |
| Qdrant 工程集成 | GO。隔离集合上的哈希向量写入、检索和撤回通过。不是语义质量 |
| 真实语义向量质量 | NOT_RUN |
| 生产启用 | NO-GO |
| 最终匹配算法质量 | NO-GO |

剩余风险：V3 的 taste 覆盖写和反馈先验还在原实验链里，只是没有接进 V4。挑战集保留集 Recall@20 是 7/45，和随机池相同。分数相同时较小 `userId` 会长期占住名额。互补规则只有两条。生产 `WeekRunner` 仍然扫描全城，本轮没有把召回接进去。
