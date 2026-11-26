# V4-P02 提取契约

模型输出是候选。它不是 `RelationshipProfileSnapshot`，也不能直接写库。

## 模型输出

唯一接受的正文是一个 JSON 对象，且含有 `features` 数组。代码块围栏会先去掉。缺少 `features`、字段类型不对或 JSON 损坏时，解析结果是失败，画像保持确定性投影。

每个候选的字段：

| 字段 | 含义 |
| --- | --- |
| section | `SELF`、`PREFERENCE` 或 `BEHAVIOR` |
| featureCode | 必须存在于 `features-1` |
| proposedValue | 注册表值域内的一个值。`UNKNOWN` 和 `UNBOUNDED` 被拒绝，对应维度保持空 |
| targetOperator | 服务端对偏好固定使用 `EQ` |
| importance | 偏好强度：`NONE`、`KINDA`、`SUPER`。没有明确强度时按 `NONE` |
| evidenceRefs | 本次上下文发出的 `ev-*`。空数组会被拒绝 |
| confidence | 有限数，闭区间 0 到 1 |
| reasonCode | 短记号。服务端只检查它是安全记号 |
| behaviorKind | 行为节用 `SELF_REPORTED` 或 `SCENARIO_RESPONSE` |
| hardConstraint | `true` 时整条拒绝 |

## 服务端字段

这些值由服务端写入 `ProfileEvidence`。模型声明不作为权威。

| 字段 | 服务端规则 |
| --- | --- |
| evidenceId | `EvidenceIds.compute` |
| subjectUserId | 本次上下文的用户 |
| sourceType | `MODEL_INFERENCE` |
| sourceVersion | 本次输入指纹 |
| authorizationScope | `profile-self` |
| evidenceStatus | `PROVISIONAL` |
| visibility | `OWNER` |
| constraintType | 偏好为 `SOFT`。其他分节为空 |

## 语义

`SELF` 表示这个人自己的特征。`PREFERENCE` 表示这个人希望伴侣具有的特征。「我希望对方更主动」只能支持 `partner.social.initiative` 这类偏好。它不能支持 `self.social.initiative`。

自述习惯是 `SELF_REPORTED`。用户在情景题里的真实选择是 `SCENARIO_RESPONSE`，并且只能支持 `behavior.scenario.signal`。`OBSERVED_BEHAVIOR` 只接受真实反馈观察。模型不能写记忆节。

问卷条目可以出现在模型输入里。它不能被模型引用成新的推断证据。

没有对应证据的维度保持空。不用 0.5 或其他中性值填充，也不生成默认人格标签。

矛盾的候选都保留。同一特征出现不同值时，画像键记为 `CONTESTED`。低可信推断不覆盖已经确认或已经撤回的用户事实。

## 版本

一次写入同时带有：

- 源资料版本：`profile.version`
- 处理代次：`generation_epoch`
- 注册表版本：`features-1`
- Prompt 版本：`profile-extract-v1`
- 输入哈希：外发正文加上源版本、处理代次、模型外发代次、Prompt 版本和注册表版本
- 模型外发代次：`model_export_epoch`。旧代次不能写入
- 执行隔离：`attempt_id` 与 `fence`。晚返回的旧 fence 不能完成任务
- 模型名：网关返回的适配器名称，只进调用日志

同一源版本上的模型修订和用户改资料是两件事。`WriteMode.DETERMINISTIC` 在同一源版本上遇到不同哈希时拒绝。`WriteMode.MODEL_REVISION` 在水位已经等于该源版本、并且当前快照存在时接受修订。模型修订接口不能绕过授权和水位。
