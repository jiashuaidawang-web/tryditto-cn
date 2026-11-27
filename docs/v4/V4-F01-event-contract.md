# V4-F01 事件契约

模式版本是 `feedback-event-1`。一条 `RelationshipFeedbackEvent` 的字段：

| 字段 | 来源 |
| --- | --- |
| `eventId` | 服务端生成 |
| `schemaVersion` | 固定 `feedback-event-1` |
| `eventType` | 命令 |
| `matchId`、`matchingRunId`、`matchingPeriod` | 服务端上下文 |
| `actorUserId` | 参与者本人；系统事件为 0 |
| `counterpartUserId` | 上下文里的另一方；系统事件为 0 |
| `direction` | `A_TO_B`、`B_TO_A` 或 `JOINT` |
| `sourceType` | `SYSTEM` 或 `PARTICIPANT` |
| `occurredAt` | 命令里的发生时间，允许早于已记录事件 |
| `recordedAt` | 服务端记入时间 |
| `idempotencyKey` | 命令。同一键返回原事件 |
| `causationEventId` | 派生的双方接受事件，内容是两次接受的编号 |
| `supersedesEventId` | 更正所取代的原事件 |
| `consentScope` | 系统事件是 `SYSTEM`，其余是 `FEEDBACK_PROCESSING` |
| `actorProfileVersionAtMatch`、`counterpartProfileVersionAtMatch` | 上下文里的匹配快照，不是用户当前画像 |
| `compatibilityPolicyVersion`、`matchingObjectiveVersion`、`candidateStrategyVersion` | 上下文 |
| `reasonCode` | 可选，必须是下面的九个词之一 |
| `evidenceStatus` | 由事件类型给出 |

没有自由文本列。命令也不能带文本。

系统事件只有三种：`MATCH_PUBLISHED`、`MATCH_EXPIRED`、`MUTUAL_ACCEPTANCE_REACHED`。参与者提交这三种会得到 `FORGED_SYSTEM_EVENT`。其余事件由参与者提交。`DATE_SCHEDULED` 的证据标签是 `SYSTEM_VERIFIED`，因为时段是否订上以产品记录为准，但这一轮仍记下是哪一位参与者确认的。

原因词：`COMMON_INTEREST`、`COMMUNICATION_STYLE`、`LIFESTYLE_PREFERENCE`、`RELATIONSHIP_EXPECTATION`、`SCHEDULING_DIFFICULTY`、`NO_ROMANTIC_INTEREST`、`INSUFFICIENT_INFORMATION`、`OTHER`、`PREFER_NOT_TO_SAY`。这些是这个人选择的类别，不是测得的特质。

证据类别：`SYSTEM_VERIFIED`、`SELF_REPORTED`、`MUTUAL_CONFIRMED`、`CONTESTED`、`UNKNOWN`。投影里的争议状态另有字段，不靠改写某一方的证据来裁决。
