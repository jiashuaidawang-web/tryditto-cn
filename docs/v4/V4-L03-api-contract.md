# V4-L03 API

这些路径仍在 `/api/match-lab` 下。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/strategy?source=` | `FROZEN_EXPERIMENT` 或 `SYNTHETIC_SANDBOX`。返回计数、校准、策略对照和一份不能激活的提案 |
| POST | `/strategy/activate` | 调用 `StrategyLearningProposal.activate()`，返回 422 `STRATEGY_NOT_ACTIVATED` |
| POST | `/replay/{runId}` | 用当前引擎重放该运行已保存的输入。父运行不存在时返回 `HISTORY_UNAVAILABLE` |
| POST | `/runs/compare` | `{leftRunId, rightRunId}`。比较输入摘要、策略版本和语义结果摘要 |
| GET | `/safety` | 工作区安全违规和只读风险目录 |
| GET | `/runs/{runId}/export` | `schemaVersion=sandbox-export-1` 的合成 JSON。没有文件路径参数 |
| GET | `/production-gate` | 固定 `NO-GO`。没有写入生产开关的接口 |

计数级别是 `MATCH`。未知、过期、争议、未成熟和撤回分列，不折成负例。
