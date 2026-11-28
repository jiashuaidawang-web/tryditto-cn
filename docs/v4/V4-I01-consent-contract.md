# V4-I01 授权契约

注册成功不等于下面任何一项已经授予。本轮不批量回填历史用户。线上协议和隐私政策仍要在试点前单独审核。

| 目的 | 权威来源 | 默认 | 版本 | 撤回 |
| --- | --- | --- | --- | --- |
| 账号与产品功能 | `user.status` | 新建为 `onboarding` | 行上的更新时间 | 停用改 `deactivated`；注销改 `deleting` |
| 匹配 | `matching_consent` | 缺行是 `UNKNOWN` | `consent_version`，策略 `match-consent-v1` | `REVOKED`，不能进池 |
| 画像处理 | `relationship_profile_lifecycle.processing_authorization` | 无行即未授予 | `generation_epoch` | 撤回后当前快照不可读，代次加一 |
| 外部模型传输 | 同表 `model_export_authorization` | 无行，或随处理授权新建时为 `revoked` | `model_export_epoch` | 只停止导出，不撤销匹配或画像处理 |
| 反馈处理 | `MatchFeedbackContext.feedbackConsent` | 离线上下文自行给出；注册不写 | 上下文里的授权代次 | 未授权时消费被拒绝 |
| 个体学习 | `MatchFeedbackContext.learningConsent` | 与反馈授权分开，注册不写 | 同上 | 缺授权时 `PROFILE_LEARNING` 被拒绝 |
| 群体评估 | 反馈授权，加上用途 `GROUP_EVALUATION` | 注册不写 | 消费行上的策略版本 | 缺反馈授权时拒绝；`eraseActor` 撤回观察 |

`legacyAllowUnknown` 仍只是旧匹配池的迁移口，不是 V4 合规。`REVOKED` 在两种模式下都不通过。

个体学习和群体评估还没有面向用户的独立设置表。测试覆盖的是现有拒绝路径，不是一份已经上线的开关页面。
