# V4-M03 现有 AI 匹配代码审计

审计对象在 `feature/v4-ai-reasoning` 的起点 `04e7027` 上，没有改这些类的生产行为。

| 类 | 位置 | 结论 |
| --- | --- | --- |
| `LlmGateway` | `aiditto-app` | 可复用的是请求、超时、校验失败和调用记录。它的主备切换会把另一个模型的文本接进同一次调用。M03 不走这条切换 |
| `AgentRerankService` | `aiditto-app` | 一次任务同时读取两个人的 persona 和 taste，再按全局 `topPairs` 截断。失败时 AI 分缺失，边权回到普通规则分 |
| `AiJudge` | `aiditto-app` | 一次提示同时扮演 A 和 B，再给一个 0–100 的总分。维度权重和规则分不是同一套量纲 |
| `AiConstraints` | `aiditto-domain` | 封闭的身高、年龄和身份约束。它不是关系推理，也不能由模型临时增加 |
| `JudgeCalibrationService` | `aiditto-app` | 用历史反馈改裁判偏置。本轮不接真实反馈 |
| `TasteExtractor` | `aiditto-app` | 画像提取。它的最佳模型和关系推理不是同一个任务 |
| `RelationshipProfile` | `aiditto-domain` | 保留为画像容器。推理只读取已经确认、普通敏感度、且通过 `MatchingFeatureGate` 的特征 |
| `FeatureRegistry` | `aiditto-domain` | 特征码和值域的唯一来源。提示里的合法值域从这里动态生成 |
| `MatchingFeatureGate` | `aiditto-domain` | 只有 `USER_CONFIRMED` 和 `SEMANTIC_SUPPORTED` 可以进入正式比较 |
| `CompatibilityDecision` | `aiditto-domain` | M02 的决策入口。M03 调用它，不另写 60 和 0.60 |
| `BidirectionalCompatibilityEngine` | `aiditto-domain` | 确定性评分基线。M03 不改它的公式 |
| `DirectionalCompatibilityResult` | `aiditto-domain` | 单方向的已知分、覆盖率和明细。推理提示只带当前方向的明细 |

## V3 里不沿用的做法

- 一次模型调用同时模拟两个人，并给出一个总分。
- 全局 Top-N 候选预算。当前默认来自 `topPairs`，不是按用户和候选边分开的预算。
- persona 文本和 taste 权重放在同一次精排里。
- 模型分和规则分的量纲不同，却都能改变配对边权。
- 模型失败或冲突时，该对可以回到普通规则匹配，调用记录不一定能区分「没有推理」和「推理被否决」。

M03 留下的是单模型请求和结构化解析。评分策略、扮演双方的提示和全局名额不复制。
