# V4-F01 学习资格

`FeedbackLearningEligibility` 只判断一次配对的事实以后能不能被读取。它不调用 `TasteRewriter`，也不调用 `JudgeCalibrationService`。

三个结果始终分开：

- `matchFactEligible`：来源、授权、快照和争议这些门都过了。
- `profileUpdateAllowed`：固定 false。构造时如果有人把它设成 true，会直接拒绝。
- `negativeLabelAllowed`：固定 false。一次拒绝不是稳定偏好，对方的评价也不是这个人自己的特质。

## 会关掉资格的原因

| 原因 | 含义 |
| --- | --- |
| `NOT_PUBLISHED` | 上下文或事件里没有发布 |
| `NO_FEEDBACK_CONSENT` | 任一方没有反馈处理授权 |
| `ERASED` | 参与者已删除，或某行被阻断学习 |
| `PROCESSING_REVOKED` | 有人撤回了处理授权 |
| `RETRACTED` | 有人撤回了反馈 |
| `CORRECTED` | 有事件被更正。旧事件不能再消费 |
| `CONTESTED` | 双方见面报告相反 |
| `SNAPSHOT_MISMATCH` | 事件上的画像版本不是这张匹配快照 |
| `STRATEGY_MISMATCH` | 策略版本不是上下文里的版本 |
| `ALREADY_CONSUMED` | 已经有事件被声明消费 |
| `DUPLICATE_CONSUMPTION` | 同一事件被第二次声明 |

撤回不能被一条更正重新打开。已经更正的事件编号不能进入消费。

案例 2 里 B 的拒绝可以通过事实资格，因为来源清楚、没有争议、也没有撤回。它仍然不能更新画像，也不能写成负向标签。案例 7、9、10 连事实资格都没有：分别是争议、更正和处理授权撤回。
