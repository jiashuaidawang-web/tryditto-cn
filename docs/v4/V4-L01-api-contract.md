# V4-L01 API

前缀 `/api/match-lab`。开关关闭时返回 403，`code` 是 `FORBIDDEN`。开关打开后，未登录返回 401，非管理员返回 403。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET | `/overview` | 环境、研究环境、工作区、目录、冻结策略、限制、生产状态 |
| POST | `/workspace/reset` | 丢弃当前管理员的工作区并新建一份带种子画像的工作区 |
| GET | `/registry` | `FeatureRegistry` 的码、中文名、类型、合法值和分区 |
| GET | `/profiles` | 内存中的虚构画像 |
| POST | `/profiles` | 按注册表创建 |
| PUT | `/profiles/{userId}` | 编辑并增加版本。请求体必须带 `expectedVersion`，与当前版本不一致时返回 422，`code` 是 `VERSION` |
| GET | `/diff?left=&right=` | 特征差异 |
| POST | `/score` | `{userA, userB}`，调用双向引擎 |
| POST | `/recall` | `{split}` 为 `dev` 或 `holdout` 时重跑冻结对照。`{mode:"SYNTHETIC_SANDBOX", seekerId}` 用当前工作区画像生成候选 |
| POST | `/reasoning` | 夹具回放 |
| GET | `/runs` | 最小 `ExperimentRun` 列表 |

目录行包含 `experimentId`、`module`、`title`、`description`、`datasetVersion`、`datasetIdentityHash`、`datasetContentHash`、`commitSha`、`catalogIntegrationCommit`、`policyVersion`、`sourceType`、`executionMode`、`executionStatus`、`qualityStatus`、`metrics`、`limitations`、`caseIds`。

哈希规则：

- `datasetIdentityHash` 是 `SHA-256(experimentId + 换行 + datasetVersion)` 的前 16 个十六进制字符。它只区分目录身份。
- `datasetContentHash` 是 `docs/v4/` 下冻结 JSON 文件字节的完整 SHA-256。没有这份文件时写成 `CONTENT_HASH_UNAVAILABLE`。不补造历史内容。
- `catalogIntegrationCommit` 固定为已经收进冻结数据集的集成提交 `5a23e862aa85bb7f65355b565f5032eb3d094d48`。
- 每一行的 `commitSha` 是该文件自己的来源提交，不是集成提交。召回夹具没有单独 JSON，来源提交是 `587539d9ccf9eac889055fd2c4b3be0ca02bd577`，内容摘要不可用。生产门没有文件，提交写成 `NONE`。
- 一次实际运行的 `inputHash` 是输入快照 UTF-8 字节的完整 SHA-256。`resultHash` 是结果摘要 UTF-8 字节的完整 SHA-256。运行上的 `commitSha` 来自 `GIT_COMMIT`，没有设置时是 `UNSPECIFIED`。

请求可以带 `X-Match-Lab-Workspace`。缺省时使用当前管理员自己的工作区。写成别人的工作区编号返回 403，`code` 是 `WORKSPACE`。

`executionStatus` 用 `RECORDED`、`REPLAY_READY` 或 `NOT_EXECUTED`。历史 JSON 保持 `FROZEN_RESULT`。候选页和评分页的本次调用才是 `ENGINE_EXECUTED`。推理回放是 `MODEL_REPLAY`。

总览另有冻结策略 E、B5、G2。每条实验的指标单独列出。冻结召回只接受 `dev` 和 `holdout`，其他切分返回 422，`code` 是 `DATASET`。沙箱模式不使用这两个切分。响应含寻求者、初始池、合法边、未进合法池，以及每条边的资格。冻结对照才带 R1–R5 来源。

画像写入可以带 `behavior`。特征状态只接受 `CONFIRMED`、`PROVISIONAL`、`CONTESTED`。

运行记录包含 `runId`、`workspaceId`、`parentRunId`、`experimentType`、`datasetVersion`、`inputSnapshot`、`inputHash`、`resultHash`、`policyVersion`、`commitSha`、`executionMode`、`status`、开始和结束时间、结果摘要、错误和限制。沙箱最多保留 48 次运行。请求体已知长度超过 64KiB 时，过滤器直接返回校验错误 `PAYLOAD`。

召回响应另有 `population` 和 `routeContract`。冻结对照是 `FROZEN_COHORT` 与 `R1_R5`。沙箱候选是 `SYNTHETIC_SANDBOX` 与 `NOT_APPLICABLE`，只跑完整合法池 `FULL_POOL_M02`，不编造 R1–R5，也不写 `NO_ROUTE_SIGNAL`。每一行带画像版本和生命周期代次。总览里的 `storage` 是 `MEMORY_ONLY_LOST_ON_RESTART`。

评分响应里的 `executionMode` 是 `ENGINE_EXECUTED`。推理响应里的 `executionMode` 是 `MODEL_REPLAY`，`aiAdjustment` 是 false。无依据推断和被拒绝判断分开返回。
