# V4-M03 推理契约

版本 `reasoning-prompt-1`。摘要算法是 SHA-256。提示哈希和数据集哈希写在评估报告里，由代码计算，不手填。

## 谁可以进入

推理资格来自 `CompatibilityPolicies`，版本 `compat-decision-1`。证据不足和矛盾可以研究。下面这些不能进入，也不能留下画像行：

- 未授权、同意不是明确授予、不在池内、资料不是当前版本、不是合成夹具。
- `HardFilter` 任一规则失败。

推荐资格更严：还要求 B5 聚合存在、最低方向分至少 60、两个覆盖率至少 0.60、没有矛盾、没有无法评估的 `SUPER` 偏好。推理资格不能把这些门槛改成通过。

## 一条判断

必须同时有偏好码、目标特征码和至少一条证据引用。校验还要求：

- 特征码在 `FeatureRegistry` 中，偏好属于偏好段，目标属于自身段。
- 已登记的对齐关系不能换成另一条自身特征。
- 方向、选择者和目标与这次调用一致。
- 证据引用出现在这个方向的视图里。引用存在只说明材料在场。
- `SUPPORTED_COMPATIBILITY` 还要有 M02 的 `SATISFIED` 或 `PARTIALLY_SATISFIED`。
- `POTENTIAL_CONFLICT` 还要有 `UNSATISFIED`。
- `INSUFFICIENT_EVIDENCE` 对应缺失或 `UNKNOWN`。
- `CONTRADICTION` 对应 `CONTESTED`。
- `POTENTIAL_COMPLEMENT` 只接受 `partner.support=close` 对 `self.communication=frequent` 这条未验证假设，并且不能把它写成已验证契合。
- 陈述里不能出现未提供的特征值，也不能出现性别、职业、MBTI、内向、外向、硬约束或「一定会喜欢」。

未通过的判断进入 `unsupportedClaims`，不进入可用列表，也不能改变 C 方案的分数。

## 独立校验器

`ReasoningValidator` 是确定性校验，不是另一个模型。P02 的 E4 结果不作为它的可信证明。误接受率用单独的对抗样本衡量：错误特征码、不存在的引用、串向身份、性别推断、由缺失推出性格、新增硬约束、把节奏偏好对到沟通特征。分母是这 7 条样本，分子是仍留在支持、互补或冲突列表里的条数。

证据引用存在不等于语义支持。上面的类别规则就是这条边界。
