# V4-L01 API

前缀 `/api/match-lab`。开关关闭时返回 403，`code` 是 `FORBIDDEN`。开关打开后，未登录返回 401，非管理员返回 403。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/overview` | 环境、目录、限制、生产状态 |
| GET | `/registry` | `FeatureRegistry` 的码、中文名、类型、合法值和分区 |
| GET | `/profiles` | 内存中的虚构画像 |
| POST | `/profiles` | 按注册表创建 |
| PUT | `/profiles/{userId}` | 编辑并增加版本 |
| GET | `/diff?left=&right=` | 特征差异 |
| POST | `/score` | `{userA, userB}`，调用双向引擎 |
| POST | `/recall` | 四个方案的本次执行 |
| POST | `/reasoning` | 夹具回放 |
| GET | `/runs` | 最小 `ExperimentRun` 列表 |

运行记录包含 `runId`、`experimentType`、`datasetVersion`、`inputHash`、`policyVersion`、`commitSha`、`executionMode`、`status`、开始和结束时间、结果摘要、错误和限制。`commitSha` 来自 `GIT_COMMIT`，没有设置时是 `UNSPECIFIED`。

评分响应里的 `executionMode` 是 `ENGINE_EXECUTED`。推理响应里的 `executionMode` 是 `MODEL_REPLAY`，`aiAdjustment` 是 false。
