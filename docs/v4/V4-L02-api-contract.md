# V4-L02 API

这些路径仍在 `/api/match-lab` 下，沿用 L01 的环境开关、管理员和工作区检查。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/global` | `{objective, userIds}`。空列表使用当前工作区全部虚构用户。目标可用 `G0` 到 `G4`，默认 `G2` |
| POST | `/global/compare` | 用真实求解器跑 `local-optimum` 数学夹具的 G0 和 G2。`executionMode` 是 `MATH_COMPARE`，不能打开反馈 |
| POST | `/feedback/open` | `{runId, userA, userB}`。配对必须来自可继续的沙箱结果 |
| POST | `/feedback/act` | `{ledgerKey, action, actorUserId, supersedesEventId}`。动作是 view、accept、decline、timeout、schedule、cancel、attended、missed、continue、stop、correct、retract、revoke |
| GET | `/learning?ledgerKey=&userId=` | 这个用户在该账本上的信号 |
| POST | `/learning/propose` | `{userId, featureCode, newValue, sourceEventId}`。只生成待确认提案 |
| POST | `/learning/decide` | `{proposalId, action, actorUserId}`。action 是 confirm、reject 或 expire。行动者必须是提案所属用户 |
| POST | `/learning/retract` | `{userId, sourceRef}`。撤回一条来源，不拷回整份旧画像 |
| POST | `/rematch` | `{parentRunId, objective}`。新运行带父运行编号 |
| POST | `/cases/{code}` | 执行案例 A 到 F |

全局响应包含初始人数、合格边、求解边、配对数、未匹配原因、双向分数、覆盖、平均和最低契合、总权重、图覆盖、求解状态、求解毫秒、安全复查和边列表。边列表同时给出未进入求解器的排除原因。未匹配原因是求解器写下的诊断。

反馈响应包含双方时间线、来源、有效状态、因果、更正、投影和学资格。学习响应包含信号、不可学习说明、提案、变更前后特征和限制句。每次运行仍写入 L01 的 `LabRun`，带输入快照、`inputHash`、`resultHash` 和 `parentRunId`。

错误码沿用 `PLAN`、`PAIR`、`ACTOR`、`PROPOSAL`，以及 F01/F02 抛出的 `CROSS_USER`、`NOT_PARTICIPANT` 等。它们返回 422。
