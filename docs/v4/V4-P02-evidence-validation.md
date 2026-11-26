# V4-P02 证据校验与合并

`ProfileExtractionValidator.check` 是候选进入合并之前的必经步骤。证据出现在上下文里，只说明来源本次可见。它不证明结论成立。无法通过校验的候选被拒绝，不记成已证实。

## 校验

1. 正文先通过解析器的 JSON 形状检查。
2. `featureCode` 能在 `FeatureRegistry.v1()` 找到。
3. 特征允许该分节，并且该分节允许 `MODEL_INFERENCE`。
4. 值的类型和值域符合注册表。敏感引用特征不能由模型填写。
5. 每个 `evidenceRefs` 都能在本次 `ProfileContext.items` 里找到。
6. 引用在写入时由 `IssuedEvidenceDirectory` 绑定到当前用户和当前指纹。其他用户解析不到。
7. 写入前重新构建上下文。输入哈希不一致就丢弃。
8. 来源类型固定为 `MODEL_INFERENCE`。模型不能改这个字段。
9. 处理授权和模型外发授权在调用前、写入前各查一次。
10. 撤回的来源不能支撑当前值。确定性撤回也会挡住同一特征的模型候选。
11. 置信度是有限数并且在 0 到 1。
12. 模型证据的状态固定为 `PROVISIONAL`。
13. `hardConstraint=true`、`DOCUMENTED_EXISTING_RULE` 和模型给出的 `UNBOUNDED` 都拒绝。
14. `OBSERVED_BEHAVIOR` 和记忆节拒绝。模型不能据此生成行为观察或历史记忆。

语义对应：

| 上下文种类 | 可以支持 |
| --- | --- |
| `SELF_STATEMENT` | 同一条自述上的 `SELF` 特征 |
| `PREFERENCE_STATEMENT` | 同一条偏好上的 `PREFERENCE` 特征 |
| `SCENARIO` | `BEHAVIOR` 且 `behaviorKind=SCENARIO_RESPONSE` |
| `QUESTIONNAIRE` | 特征码相同，并且值与上下文里的分节值相同 |

直接事实沿用 P01 的投影和 `AnswerEvidenceDirectory`。语义推断把候选和它引用的 `ev-*` 一起留下，供以后独立评估。校验失败的原因记在 `ProfileExtractionValidator.Rejection`。

## 合并优先级

1. 当前代次里仍然有效的用户确认或撤回。
2. 当前源版本的结构化问卷事实。
3. 经校验的用户自述。本轮自述进入上下文；确定性事实仍以投影为准。
4. 通过校验的模型暂定推断。它只填确定性证据还没有占用的分节和特征码。

比较的是来源语义。置信度更高也不能替换第 1 到第 3 层。合并不把整份画像交给模型重写。

`commitProvisional` 在用户行锁内重读授权和输入。账户处于 `deleting` 或 `purged` 时拒绝。处理授权缺失或代次不同时拒绝。模型外发授权已撤回时拒绝。源版本与构建时不一致时拒绝。全部通过后才 `SnapshotAdmission.admit`。

参加匹配、处理画像、向外部模型提供数据是三列独立授权。画像服务不读 `matching_consent`。`revokeModelExport` 只改模型外发列。
