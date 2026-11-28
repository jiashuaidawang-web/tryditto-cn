# V4-L02 API

这些路径仍在 `/api/match-lab` 下，沿用 L01 的环境开关、管理员和工作区检查。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/global` | `{objective, userIds}`。空列表使用当前工作区全部虚构用户。目标可用 `G0` 到 `G4`，默认 `G2` |
| POST | `/global/compare` | 用真实求解器跑 `local-optimum` 数学夹具的 G0 和 G2。`executionMode` 是 `MATH_COMPARE`，不能打开反馈 |
| POST | `/feedback/open` | `{runId, userA, userB}`。配对必须来自可继续的沙箱结果 |
| POST | `/feedback/act` | `{ledgerKey, action, actorUserId, supersedesEventId}`。动作是 view、accept、decline、timeout、schedule、cancel、attended、missed、continue、stop、correct、preference、retract、revoke。重复的同一有效动作返回当前账本 |
| GET | `/learning?ledgerKey=&userId=` | 这个用户在该账本上的信号 |
| POST | `/learning/propose` | `{userId, featureCode, newValue, sourceEventId, ledgerKey, origin}`。`origin` 省略或 `DIRECT` 表示用户明确选择，不能同时带反馈事件。`FEEDBACK` 必须绑定真实、有效、未撤回的 `PREFERENCE_CORRECTION_SUBMITTED`。接受事件返回 `INSUFFICIENT_SEMANTICS` |
| POST | `/learning/decide` | `{proposalId, action, actorUserId}`。action 是 confirm、reject 或 expire。行动者必须是提案所属用户 |
| POST | `/learning/retract` | `{userId, sourceRef}`。撤回一条来源，不拷回整份旧画像 |
| POST | `/rematch` | `{parentRunId, objective}`。新运行带父运行编号 |
| POST | `/cases/{code}` | 执行案例 A 到 F |

全局响应包含初始人数、合格边、求解边、配对数、未匹配原因、双向分数、覆盖、平均和最低契合、总权重、图覆盖、求解状态、求解毫秒、安全复查、边列表、`inputHash`、`resultHash` 和 `semanticResultHash`。后两个摘要不包含运行编号和求解毫秒。分数不同则摘要不同。

输入快照 `schema=sandbox-input-1` 写入全部参与用户、四层里已经保存的特征、版本、代次、授权、年龄硬约束、候选策略、评分策略、全局目标版本、观察日和种子。反馈上下文读取这次输入，不再套固定日期或默认 G2。`labRunId` 仍是工作区运行编号，`matchingRunId` 是求解输入编号。

学习确认只替换有 `confirm.` 来源的那一项。其余特征、操作符、强度、未确认状态、行为和硬约束保持。直接编辑画像后，下一次学习按新版本重新取证。过期提案返回 `STALE`，不写画像。反馈撤回或授权撤回后，绑定该账本的未确认提案返回 `RETRACTED_SOURCE`。重复确认不再增加版本。

计划、账本、事件和提案分别不超过 48、48、240、48。工作区重置后旧运行不能再打开反馈。

错误码增加 `INSUFFICIENT_SEMANTICS`、`SOURCE`、`STALE`、`RETRACTED_SOURCE` 和 `SANDBOX_LIMIT`。父运行已经不在工作区时仍返回 `RUN`。这些都是 422。
