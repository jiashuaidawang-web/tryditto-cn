# V4-M03 最终审计

本轮在 `feature/v4-ai-reasoning` 上修正推理契约，并在隔离实验环境执行真实模型。起点是 `14a7f52ac86d14be21caa59d1401d0f6a2508efb`。生产 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom 和 S01 没有改。M01 权重、冻结标签、生产画像和自由文本外发没有动。没有实现 M04 或 M05，没有部署，没有合并 `master_agent_v4`。

## 保留的基线

| 项 | 值 |
| --- | --- |
| 原数据集 | 120 条，哈希 `ad3ac66b5aa1e4735bb034fbc231a90de4e26ef8f2a6e305a575f79a892b8944` |
| 原提示 | `reasoning-prompt-1`，哈希 `a675b35e4002fb35b10868dd8530dc34f04b68927ba1be3f6f4ee41a042792fc` |
| C1 策略 | `reasoning-experiment-1`。支持判断 +2，冲突判断 −2，修正量限制在 ±10。分数本身仍可超过 100 |
| 规则回声与 M02 B5 | 原结果保留。本轮 `M02FinalTest` 仍是开发集 264/264、保留集 99/99 |
| 200 人回归 | 同一次领域测试打印 83 对、34 人未匹配、822 条边、0 条硬约束违规 |

新增的 C2 策略是 `reasoning-experiment-2`。36 条挑战关系的来源是 `synthetic-fixture:v4-m03-challenge`，哈希 `06dd7b9d7eac5b1a5398edf91132807961f2dbe3a85b97e6776ee6d07cc4f690`。它不进入 120 条数据集的哈希。

## 契约

角色分成三类。`STRUCTURED_EXPLANATION` 复述已有 M02 行。`RELATIONSHIP_HYPOTHESIS` 只保留已注册的潜在互补。`NEW_SUPPORTED_RELATION` 留在 `experimentFindings`，不进入 `usable()`，也不改变正式分数。潜在假设不能直接进入评分。

C1 与 C2 分开记录。C2 按方向、偏好、目标特征和类别去重；假设和新发现不计分；两个方向都是 `COMPLETED` 才允许修正。`MODEL_FAILED`、`REJECTED`、`REFUSED` 不产生单侧修正，失败时保留 M02 分数。推荐资格为假，或原因含 `STRONG_PREFERENCE_UNEVALUABLE` 时，状态是 `GATE_HELD`。C2 实验分限制在 0–100。原始和超出这个区间时，状态记为 `CLAMPED`，即使四舍五入后的修正量是 0。

证据引用必须属于当前方向的选择者偏好或目标自身特征，并且对应当前 `preferenceCode` 和 `targetFeatureCode`。支持、冲突和互补在目标特征存在时还要引用该特征。引用真实存在并不等于语义支持。关键词黑名单只是辅助检查。服务端 `StructuredExplanation` 是 B0：它复述 M02 行，不调用模型，不改排序。

## 本地测试

领域测试 541 项，失败 0，跳过 0。应用测试 127 项，失败 0；8 项跳过是原有的真实调用测试，在没有凭据时跳过，不是本轮推理测试。`RelationshipReasoningTest` 8 项通过，包括 C1 分数可以超过 100、C2 被限制到 100 并记为 `CLAMPED`。

## 结论

| 项 | 结论 |
| --- | --- |
| M03 工程质量 | GO。CI 成功：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46026253764 ，提交 `8819b4314d057b37fd4caf2eff7357795f85c6a1` |
| AI 关系解释质量 | NO-GO |
| AI 新增关系发现质量 | NO-GO |
| AI 排序增益 | NO-GO |
| 召回端到端 | NO-GO |
| 生产启用 | NO-GO |
| 最终匹配算法质量 | NO-GO |

解释、发现和排序的数字在 `V4-M03-live-eval-report.md`、`V4-M03-model-comparison.md` 和 `V4-M03-ranking-incremental-report.md`。门槛没有在看到结果后下调。
