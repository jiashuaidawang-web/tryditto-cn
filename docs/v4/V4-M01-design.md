# V4-M01 设计

V4 召回是离线实验。输入是带 `synthetic-fixture` 来源的标准画像，不读生产用户，不连接 `match_user_v1`，不进入 `WeekRunner`。

```mermaid
flowchart TD
  fixture["合成夹具 RecallBench / RecallCases"] --> subject["RecallSubject"]
  subject --> gate["PoolGate + ConsentPolicy + HardFilter"]
  gate --> r1["R1 爱好重叠"]
  gate --> r2["R2 自身枚举"]
  gate --> r3["R3 A.preference 到 B.self"]
  gate --> r4["R4 B.preference 到 A.self"]
  gate --> r5["R5 两条注册互补规则"]
  gate --> r6["R6 固定种子探索"]
  r1 --> rrf["RrfFusion k=60"]
  r2 --> rrf
  r3 --> rrf
  r4 --> rrf
  r5 --> rrf
  r6 --> rrf
  rrf --> budget["每人预算截断"]
  budget --> metrics["RecallMetrics"]
```

B0 现在明确叫随机合法池。它把已经通过资格的人按固定种子打乱，再截到同一预算。另外三份对照使用同一批人、同一套资格、同一个 Top-K 和同一份标签：B1 用未改动的生产 `Scorer.score`，B2 只走 R1，B3 走冻结的 R1–R5。`StructuredRecallService` 没有拿来当 B2，因为它要查 PostgreSQL 的 profile 行，并使用 V3 的单向过滤。无预算的合法全集只用来报覆盖上限，不拿去和 Top-K 比 Precision。

分数相同的时候，V4 路线和生产规则分都按较小的 `userId` 排在前面。这会让同一批小号用户长期占住名额。生产 `MatchingSolver` 里的种子 tie-break 没有被复制，也没有被修改。100 人回归的排序因此保持原样。

## 冻结规则

这些数在第一次消融运行之前写进 `RecallAblation`：

- RRF k = 60。权重 R1 1.0、R2 0.8、R3 1.0、R4 1.0、R5 0.7、R6 0.3。
- 每路 Top-K = 50，探索路 Top-K = 10，比较用的列表长度 = 50。
- 后一阶段只有在开发集 Recall@20 严格更高时才替换前一阶段。
- R6 的提升小于 0.02 就去掉。
- 保留集门槛：Recall@20 至少 0.60，硬约束违规、过期画像召回、撤回画像召回都是 0。
- 理由：召回先看合法正例有没有进预算；0.60 低于「把标注关系找齐」，又高于 B0 在约 90 人合法池里随机取 20 人的覆盖。安全计数必须是 0，因为召回不能制造新的硬约束例外。

路线选择用开发集。保留集只跑一次被选中的路线集合。

## 不在本轮

不改最终匹配目标函数，不做 M02 双向打分，不把模型未验证特征和反馈先验送进召回，不把哈希向量说成语义向量。
