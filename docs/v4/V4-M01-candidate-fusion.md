# V4-M01 候选融合

每路先在合法候选人里按自己的原始分排序，原始分不相加。R1 到 R5 各留 50 名，R6 留 10 名。`RrfFusion` 用 k=60 和冻结权重把名次合成一个分数，同分按 `userId` 升序。同一个人出现在多路时，来源都保留，分数相加。

每人比较列表最长 50。Recall@10 和 Recall@20 是这条列表的前缀，不是另一次检索。

一条命中保留：

| 字段 | 含义 |
| --- | --- |
| userId / candidateId | 寻求者和候选人 |
| recallRoute | 命中的路线，多路用 `+` 连接 |
| routeRank / rawRouteScore | 最好的那路名次和原始分 |
| fusedRank / fusedScore | 融合名次和 RRF 分 |
| sourceProfileVersion | 候选人画像版本 |
| eligibilityResult | 进入列表时为 `PASS` |
| recallReasonCode | 单路代码、`FUSED`，或 B0 的 `B0_LEGAL_POOL` |
| routes | 各路名次明细 |

被拒绝的人单独放在 `rejected`，原因码来自 `POOL_*`、`CONSENT_*`、`HARD_*`、`PROFILE_WITHDRAWN`、`PROFILE_STALE`。

双向记录的是有向边：对方的列表里也有自己，这条边算互相进入 Top-K。只在一边出现，仍然只是召回假设，不是互相喜欢。

合法候选人不足时，列表比预算短，不从非法池里补。R6 被选择规则去掉之后，没有路线能打分的人就是空列表。开发集里这占 19/70，他们没有标注正例。

B0 用种子 `20261118` 打乱合法池再截断。它和 R1–R6 使用同一个预算。
