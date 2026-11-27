# V4-M03 设计

离线关系推理在 `cn.aiditto.domain.reasoning.v4`。领域模块不依赖应用模块，也不读取生产数据库。应用侧的 `SingleModelReasoningClient` 只包装一个 `LlmModel`。它不使用 `LlmGateway` 的备用模型。

## 上下文

`RelationshipReasoningContext` 只在 `CompatibilityDecision.reasoningEligible()` 为真时带画像。否则上下文是密封的：没有证据行，模型不会被调用。

进入上下文的内容：画像版本、生命周期代次、已确认的普通自身特征、已确认的伴侣偏好、证据引用、偏好强度、当前方向可见的 M02 明细、已知强冲突、未知、矛盾、语义限制、提示版本和摘要。

不进入上下文的内容：`WITHHELD` 的值、`SENSITIVE_REF`、候选对象上的自由文本爱好、性别、职业、位置，以及另一个方向的推理结论。

## 两个方向

`DirectionalRelationshipAgent` 每次只看一个方向。Agent A 的提示只有 A 的偏好和 B 的自身特征。Agent B 的提示只有 B 的偏好和 A 的自身特征。两次调用共享模型实现，不共享输出。

## 分类

`SUPPORTED_COMPATIBILITY`、`POTENTIAL_COMPLEMENT`、`POTENTIAL_CONFLICT`、`INSUFFICIENT_EVIDENCE`、`CONTRADICTION`、`UNSUPPORTED_INFERENCE`。

潜在互补只允许登记过、且仍标记为未验证的假设。其他互补进入 `UNSUPPORTED_INFERENCE`，不能改变分数。潜在冲突只对应 M02 的 `UNSATISFIED`，不能写成新的硬约束。

## 和 M02 的关系

策略版本 `reasoning-experiment-1`。

- A：只保留 M02 的 B5 聚合。
- B：附上通过校验的解释，排序用的分数仍是 M02。
- C：每条通过校验的支持判断加 2 分，每条通过校验的潜在冲突减 2 分，合计最多上下 10 分。这个 10 是实验参数。

推荐资格未通过、硬约束未通过或 M02 聚合为空时，C 不改发布用的实验分。模型失败时保留 M02，状态是 `MODEL_FAILED`，不写一个替代的 AI 分。`confidence` 不解释为匹配概率。

## 预算

每个方向、每条画像版本最多调用一次。另有每用户、每条边和全局上限。同一版本的重复调用记为 `REPEAT`，不再打模型。超时不重试。名额按用户已用次数轮转，避免第一个用户拿走全部调用。跳过原因写在预算记录里。

## 不做的事

不改 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom 和 S01。不接生产画像，不启用生产自由文本外发，不实现 M04 全局匹配、M05 关系模拟或真实反馈学习。不部署。
