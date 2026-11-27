# V4-F02 现有学习审计

只读 V3 学习链路。本轮没有调用它，也没有关掉它。

## V3 怎样把拒绝写成偏好

`FeedbackLearningService.scanAndRewrite` 读取还没被 `taste_log` 消费的 `feedback` 行，解密 `comment_enc`，再调用 `TasteRewriter`、`PairMemoryService` 和 `JudgeCalibrationService`。`TasteRewriter` 可以走模型，也可以走确定性兜底。一次反馈最多改一个权重，幅度不超过 0.15。双方都拒绝时，`ai_pair_memory` 可以写成 `forbidden`。学习输入没有匹配当时的画像版本，也没有策略版本。

## 原因和偏好是混在一起的

`FeedbackSignal` 从拒绝原因和文本里解析维度和方向，再变成口味增量。原因类别本身不是画像特征。F01 的 `PREFERENCE_CORRECTION_SUBMITTED` 同样只是原因类别。本轮不把这些类别解释成 `self.communication` 或任何其他特征值。

## 权重和版本

`TasteWeights` 把增量写进用户口味。`taste_log` 的唯一键是用户和配对，不是事件，也不是学习用途。它不检查画像生命周期代次，也不保留被撤回反馈和派生权重之间的对应关系。

## P01 和 P02 已经有的保护

`SnapshotAdmission` 和 `EvidenceTrust` 只接受服务端能找到的来源。模拟来源不能进入画像。模型推断保持 `PROVISIONAL`。硬约束只允许已确认的直接回答指向 `partner.age`、`partner.height`、`partner.education`、`partner.gender`。`RelationshipProfile.of` 把互相矛盾的有效证据留成 `CONTESTED`，不按最后一次写入消解。`MatchingFeatureGate` 只放行语义上已支持或用户已确认的特征，而且当前生产评分并不调用它。

P02 的模型修订走 `commitProvisional`。调用方不能提交自己拼的模型快照。本轮确认偏好不走那条模型修订。

## 哪些反馈可以成为哪一类材料

| 材料 | 去向 |
| --- | --- |
| 用户明确选择的合法特征值 | 确认后可以成为本人画像证据 |
| 用户多次表达的方向 | 待确认假设，不进评分 |
| 没有原因的拒绝、超时、未见面 | 关系结果，不解释成人格或偏好 |
| 对方的评价 | 只属于对方的事件，不写进本人画像 |
| 群体表现 | 留给 F03。本轮 `GROUP_EVALUATION` 不能构造 |

本轮不复用 V3 的自动改写。`FeedbackLearningService` 保持原样，没有被新的确认事务调用。
