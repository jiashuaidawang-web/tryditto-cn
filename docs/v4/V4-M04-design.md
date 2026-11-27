# V4-M04 设计

独立包是 `cn.aiditto.domain.matching.v4`。入口是 `V4GlobalMatchingEngine.solve`。它不调用 `MatchingSolver`，也不调用 `WeekRunner`。

生产匹配保持关闭。本轮没有 M03 的 AI 修正分，没有 M03 C1 或 C2，没有真实反馈权重，没有生产画像，没有真实 Qdrant，也没有 M05 关系模拟。

## 输入

`GlobalMatchingInput` 带有运行编号、周期、cohort、合成用户、画像版本、生命周期代次、同意快照、拉黑、禁止、历史限制、候选策略版本、契合策略版本和目标版本。构造时拒绝重复用户，也拒绝来源不是 `synthetic-fixture:` 的画像。没有“两个用户编号加一个分数”的构造器。

数学小图使用 `GlobalMatchingGraph.math`。那些边标记为 `mathAdmitted`，只用来检查求解器，不当成推荐。

## 候选图

`GlobalCandidateGraphBuilder` 先做双向资格检查，再调用冻结的 `BidirectionalCompatibilityEngine.v1().decide`。每条无向边只计算一次，`lowId < highId`。边上保留两个方向的分数、两个方向的覆盖率、双方版本和代次、策略名，以及 `CompatibilityDecision`。

诊断图保留被拒绝的边。求解图只收 `recommendationEligible` 且契合度落在 (0, 100] 的边。证据不足、待确认、撤回、过期、未授权和硬约束失败不能因为候选变少而回到求解图。

策略：

- E：对通过预检的全部两两组合评分。
- F：按完整画像的双向结构命中取每人 20 人，再对并集评分。

`topK` 保留任一端把该边排进自己前 K 的合格边，并把 `fullPopulation` 标成 false。

## 权重

契合度是 `fitCenti = round(harmonicWithCoverage * 100)`，范围 1–10000。优化权重由 `OptimizationWeight.edgeWeight` 按目标另算。两者同时出现在配对结果里。契合度不是接受概率。

## 求解

`V4MatchingAdapter` 把合格边交给现有的 `GreedySolver` 或 `BlossomMatcher`。求解后 `GlobalSafetyAudit` 再读一遍图和被选中的键。检查项包括未知边、重复用户、限制集、推荐资格、硬约束、撤回、过期、同意和版本。

一个人最多一对。没有自匹配。求解器不能发明原图里没有的边。孤立点、奇数用户、多个连通分量和空图都留在未匹配结果里。

## 预算

运行前写死在 `GlobalBudgets`：

- 最多 10,000 人
- 求解边最多 50,000
- 诊断边最多保留 2,000
- 墙钟 90 秒
- F 的短名单长度 20

越过边数或时间上限时，求解边被清空，状态是 `CAP_REACHED`，`optimalForThisGraph` 为 false。未匹配原因是 `RUN_ABORTED`。这个结果不能称为全局最优。

## 选定的实验策略

默认目标是 G2，版本 `global-objective-g2-1`。默认候选图是 E，而且只在边数预算内求解。

理由：推荐资格已经是质量底线。G2 先要更多合法配对，再在同样人数里要更高的契合度。G0 会停在局部重边。G1 会为了一条 100 分边放弃两条 40 分边。G3 和 G4 是对照，不是默认。F 和 Top-K 在重复调色板的 500 人图上丢掉大部分可匹配人数，所以不作为默认图。

## 明确不做

不改 M01、M02、M03 的冻结标签。不降低已经冻结的质量门槛。不部署，也不把本分支合并进集成分支。
