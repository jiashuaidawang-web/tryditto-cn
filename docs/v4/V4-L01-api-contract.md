# V4-L01 API

前缀 `/api/match-lab`。开关关闭时返回 403，`code` 是 `FORBIDDEN`。开关打开后，未登录返回 401，非管理员返回 403。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/overview` | 环境、目录、冻结策略、限制、生产状态 |
| GET | `/registry` | `FeatureRegistry` 的码、中文名、类型、合法值和分区 |
| GET | `/profiles` | 内存中的虚构画像 |
| POST | `/profiles` | 按注册表创建 |
| PUT | `/profiles/{userId}` | 编辑并增加版本 |
| GET | `/diff?left=&right=` | 特征差异 |
| POST | `/score` | `{userA, userB}`，调用双向引擎 |
| POST | `/recall` | `{split}` 为 `dev` 或 `holdout`。四个方案的本次执行，并返回初始池资格 |
| POST | `/reasoning` | 夹具回放 |
| GET | `/runs` | 最小 `ExperimentRun` 列表 |

目录行包含 `experimentId`、`module`、`title`、`description`、`datasetVersion`、`datasetHash`、`commitSha`、`policyVersion`、`sourceType`、`executionMode`、`executionStatus`、`qualityStatus`、`metrics`、`limitations`、`caseIds`。`datasetHash` 是实验编号和数据集版本的目录身份摘要，不是冻结 JSON 文件的摘要。目录里的 `commitSha` 是已经收进 `master_agent_v4` 的冻结数据集提交。一次实际运行的提交写在运行记录上，来自 `GIT_COMMIT`，没有设置时是 `UNSPECIFIED`。

`executionStatus` 用 `RECORDED`、`REPLAY_READY` 或 `NOT_EXECUTED`。历史 JSON 保持 `FROZEN_RESULT`。候选页和评分页的本次调用才是 `ENGINE_EXECUTED`。推理回放是 `MODEL_REPLAY`。

总览另有冻结策略 E、B5、G2。每条实验的指标单独列出。召回只接受 `dev` 和 `holdout`，其他切分返回 422，`code` 是 `DATASET`。响应含寻求者、初始池、合法边、未进合法池，以及每条边的资格和 R1–R5 来源。

画像写入可以带 `behavior`。特征状态只接受 `CONFIRMED`、`PROVISIONAL`、`CONTESTED`。

运行记录包含 `runId`、`experimentType`、`datasetVersion`、`inputHash`、`policyVersion`、`commitSha`、`executionMode`、`status`、开始和结束时间、结果摘要、错误和限制。

评分响应里的 `executionMode` 是 `ENGINE_EXECUTED`。推理响应里的 `executionMode` 是 `MODEL_REPLAY`，`aiAdjustment` 是 false。无依据推断和被拒绝判断分开返回。
