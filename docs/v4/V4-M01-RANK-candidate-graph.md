# V4-M01-RANK 候选图

图由方案 F 和宽预算生成，策略版本 `recall-rank-1`。一条边是无向的：`lowId < highId`。双方对调不新增边。两个方向的分数和覆盖率都写在同一条边上，方向按较小 id 到较大 id 排列。

开发集 55 人，去掉寻求者后是 54 条边。其中合法 50 条，推荐合格 8 条。4 条没有进入评分：撤回、过期、年龄硬约束、历史配对。画像版本和生命周期代次在这套夹具里都是 1。

M04 读这些边。本轮不求全局匹配，也不改 Blossom。

## 边字段

| 字段 | 含义 |
| --- | --- |
| lowId, highId | 双方身份，较小者在前 |
| versionLow, versionHigh | 双方画像版本 |
| generationLow, generationHigh | 双方生命周期代次 |
| routes | 进入预算的路线，空 Map 为空字符串 |
| recallRank, coarseRank, finalRank | 融合名次、粗排名次、最终名次；没有则为空 |
| scoreLowToHigh, scoreHighToLow | M02 的两个方向分 |
| coverageLowToHigh, coverageHighToLow | 两个方向的证据覆盖 |
| recommendationEligible | `CompatibilityDecision` 的推荐资格 |
| legality | `LEGAL`、`PROFILE_WITHDRAWN`、`PROFILE_STALE`、`HARD_AGE`、`HARD_HISTORY` 或 `HARD_BLOCK` |
| miss | 合法边用召回或粗排阶段；未评分边改用合法性原因 |
| policy | `recall-rank-1` |

未评分的人不能记成“路线没有信号”。撤回和年龄硬约束的原因就是撤回和年龄。

## 开发集案例

可见强匹配仍有路线，融合名次在 42 之后，F 把他们排到最终前 4。空 Map 的强匹配没有路线，召回名次为 0，M02 仍给出双向 100 和覆盖率 1，最终名次 5 到 8。

| 边 | 路线 | 召回 | 粗排 | 最终 | 低到高 | 高到低 | 覆盖 | 资格 | 合法性 | 未入选 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- |
| 10000–18000 | R1+R2+R3+R4 | 42 | 1 | 1 | 100 | 100 | 1/1 | 是 | LEGAL | COVERED |
| 10000–18003 | R1+R2+R3+R4 | 45 | 4 | 4 | 100 | 100 | 1/1 | 是 | LEGAL | COVERED |
| 10000–19000 | 无 | 0 | 5 | 5 | 100 | 100 | 1/1 | 是 | LEGAL | COVERED |
| 10000–19003 | 无 | 0 | 8 | 8 | 100 | 100 | 1/1 | 是 | LEGAL | COVERED |
| 10000–15001 | 无 | 0 | 无 | 无 | 无 | 无 | 无 | 否 | PROFILE_WITHDRAWN | PROFILE_WITHDRAWN |
| 10000–15003 | 无 | 0 | 无 | 无 | 无 | 无 | 无 | 否 | HARD_AGE | HARD_AGE |

18001、18002、19001、19002 与上表同一形状，最终名次依次是 2、3、6、7。八条合格边的双向分都是 100，覆盖率都是 1。这是夹具里双方四项完全一致的结果，不是开放世界的契合度分布。

其余 42 条合法边被 M02 评分，推荐资格为否，留在证据不足里。他们可以出现在最终 20 人的后段，但不能绕过资格门。
