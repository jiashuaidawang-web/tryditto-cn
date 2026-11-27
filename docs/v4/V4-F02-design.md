# V4-F02 设计

学习引擎在 `cn.aiditto.domain.feedback.v4.learning`。持久化在 `ProfileLearningStore`。画像仍是 `relationship_profile_version`。

一条反馈先被分成信号。信号可以留下待确认提案。提案不是画像。只有用户自己的 `ExplicitPreferenceCommand` 在通过版本、代次、授权和来源检查之后，才经 `SnapshotAdmission` 写入新的画像版本。同一次事务写入消费凭证。

假设的重复阈值是 3 条不同事件，窗口是 30 天。这两个数是未验证参数，不写进特征权重，也不调用模型。

F03 不实现。生产匹配、生产反馈读取和真实模型都不接入。
