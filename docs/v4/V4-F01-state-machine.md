# V4-F01 状态

事件只追加。有效状态由投影计算，不覆盖旧行。

## 可以发生的顺序

1. 上下文已经标记发布，系统才能写入 `MATCH_PUBLISHED`。
2. 发布之后，参与者可以查看、接受或拒绝。
3. 双方都有一条未更正的接受时，系统追加一次 `MUTUAL_ACCEPTANCE_REACHED`。
4. 任一方可以记录安排见面，另一方或同一方可以记录取消。
5. 任一方可以报告见到了或没见到。两边说法不同则是争议。
6. 任一方可以表达继续或不继续。一边愿意不代表另一边愿意。
7. 更正必须带 `supersedesEventId`，然后再追加新的有效事实，例如把接受改成拒绝。
8. 撤回处理授权或撤回反馈可以发生在接受之后。撤回不能被更正掉。

发生时间可以早于已经记入的事件。投影按记入顺序。迟到的查看不会改写已经存在的接受。

## 拒绝的命令

| 情况 | 代码 |
| --- | --- |
| 上下文未发布 | `NOT_AUTHORIZED` |
| 还没有发布事件就接受 | `NOT_PUBLISHED` |
| 不是这对人 | `NOT_PARTICIPANT` |
| 声称的对方不是上下文里的对方 | `FORGED_COUNTERPART` |
| 参与者提交系统事件 | `FORGED_SYSTEM_EVENT` |
| 同一人同一类有效事实再写一条 | `DUPLICATE_EFFECT` |
| 同一人已有相反的有效响应 | `CONFLICTING_RESPONSE` |
| 没有反馈处理授权却提交接受、拒绝、见面或继续意愿 | `NO_FEEDBACK_CONSENT` |
| 更正没有指向原事件 | `MISSING_SUPERSEDES` |
| 试图更正撤回 | `REVOCATION_STANDS` |
| 消费一条已经被更正的事件 | `SUPERSEDED` |
| 同一幂等键改了类型或行为人 | `IDEMPOTENCY_CONFLICT` |

同一幂等键的重复提交返回原来那一条，不新增接受、拒绝或见面结果。

## 投影里不会出现的事

事件里没有联系方式。投影也不生成联系方式。未发布的配对不能留下有效接受。一方报告见面、另一方还没说时，状态是 `ONE_REPORTED_ATTENDED`。两边相反时是 `CONTESTED`。

`UNKNOWN` 和 `EXPIRED` 都不是 `DECLINED`。撤回之后的响应是 `WITHHELD`。处理授权撤回之后，接受事实仍可看见，学习资格为否。
