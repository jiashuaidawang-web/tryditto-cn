# V4-F02 学习信号

`LearningSignal` 记录来源事件、用户、类别、方向、来源可信度、可学习对象、适用范围、授权、有效期限和证据状态。

| 类别 | 含义 |
| --- | --- |
| `EXPLICIT_SELF_CORRECTION` | 用户明确修改自己的特征 |
| `EXPLICIT_PARTNER_PREFERENCE` | 用户明确修改希望对方具备的特征 |
| `BEHAVIORAL_OBSERVATION` | 用户自己的一次方向性表达 |
| `RELATIONSHIP_OUTCOME` | 接受、安排、见面或继续意愿这类关系结果 |
| `PREFERENCE_HYPOTHESIS` | 多条同方向自述形成的待确认假设 |
| `COUNTERPART_OPINION` | 对方的事件。主体是对方，不写进这个用户的画像 |
| `AMBIGUOUS_REASON` | 有原因类别，但原因不是特征值 |
| `NO_LEARNING_SIGNAL` | 没有原因的拒绝、超时、查看 |
| `WITHHELD` | 没有学习授权，或反馈处理已撤回、已删除 |

没有原因的拒绝是 `NO_LEARNING_SIGNAL`。带原因类别的拒绝是 `AMBIGUOUS_REASON`。两者都不能生成提案。`WITHHELD` 的证据状态是不可用，优先级为 0。
