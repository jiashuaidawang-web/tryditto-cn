# V4-I01 旧学习路径

这些类仍在默认配置下被调用，本轮不删除，也不把默认开关改成关闭。

| 代码 | 是否会跑 | 改什么 | 不改什么 |
| --- | --- | --- | --- |
| `TasteListener` | 入驻完成和已入驻用户改答案之后 | 触发 `TasteExtractor` | 不写 V4 画像 |
| `TasteExtractor` / `TasteWriter` | 口味开关默认开启 | `user_taste` | 不写 V4 画像。注销中的用户在提取前被跳过 |
| `FeedbackLearningService` | `aiditto.learning.enabled` 默认 true | 调用下面三项 | 不写 V4 画像。注销中的用户跳过重写 |
| `TasteRewriter` | 被上面的扫描调用 | `user_taste` 和 `taste_log`，可能调用模型网关 | 不写 `relationship_profile_version` |
| `PairMemoryService` | 同一扫描 | `ai_pair_memory` | 不写 V4 画像 |
| `JudgeCalibrationService` | 同一扫描，样本足够才启用 | `judge_calibration` | 不改 V4 评分权重 |

`LegacyLearningPolicy.mayWriteRelationshipProfile` 对 `LEGACY_ONLY` 和 `V4_INTEGRATION_TEST` 都返回 false。V4 画像的唯一写入服务仍是 `RelationshipProfileService`。一次用户操作不会由 V3 和 V4 各写一份权威画像。V3 继续写它自己的口味表。那是现有生产行为，不是新的 V4 事实。

未来如果要让 V4 接管，必须先有单独的模式，并且不能让 `TasteRewriter` 绕过 `SnapshotAdmission`。`V4_SHADOW` 和 `V4_PILOT` 现在会在启动时失败，所以不存在一条空的影子写入。
