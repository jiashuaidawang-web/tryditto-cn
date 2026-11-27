# V4-M04 现有求解审计

审计对象是生产求解链路。本轮没有改 `WeekRunner`、`Scorer`、`MatchingSolver`、`BlossomMatcher`、`GreedySolver` 或 S01。V4 只通过 `V4MatchingAdapter` 把一条 long 权重放进现有的 `Edge.weight()`。

## 1. V3 Blossom 实际最大化的目标

`BlossomMatcher` 调用 JGraphT 的 `KolmogorovWeightedPerfectMatching`，目标是最大权完美匹配。Blossom V 要求完美匹配，所以实现先把图加倍：每个顶点 `v` 有影子 `v'`，原边 `(u,v)` 复制到 `(u',v')`，`(v,v')` 的权重是 0。限制回原顶点后，一个人可以不匹配。它最大化的是传入边的 `weight()` 之和，不是展示分，也不是匹配人数。

## 2. 边权量纲

`MatchingSolver.weight` 把几套分数叠在一个 long 上：

- AI 分乘 `1_000_000`
- 学习分乘 `100_000`
- 规则目标分乘 `1_000`
- `tiebreak(seed, low, high)` 落在 0–999

没有 AI 分和学习分时，公式退回规则分乘 1000 再加 tiebreak。展示分 `ScoreBreakdown.score()` 不参与这个 long。

## 3. AI 分、规则分、学习分

三套分不在同一尺度。AI 分差 1 分约等于规则分差 1000 分。学习分只覆盖进入精排的边。V4 不读取这三项，也不把 RRF、用户编号或未验证反馈加进边权。

## 4. 未匹配

加倍图上的 0 权影子边允许用户不匹配。`MatchingSolver` 把没有进入配对的候选人放进 `unmatched`。V3 没有区分未匹配原因。

## 5. 同一用户多对

Blossom 和贪心都跳过已经使用的顶点。生产求解器不允许同一用户进入两对。

## 6. 零权和负权

`MatchingSolver` 在建边时丢掉不达门槛的边。`V4MatchingAdapter` 在调用求解器前丢掉 `fitCenti <= 0` 和由此得到的非正权重。贪心按权重从高到低取边。零权和负权不会为了增加人数而被捡回来。

## 7. 并列最优

贪心的次序是权重、`low`、`high`。Blossom 在权重完全相同的时候不提供业务层的并列契约。V4 在送入求解器前按 `lowId`、`highId` 排序，同输入、同目标和同种子下复跑同一对集合。17 人开发集正序和反序的 G2 配对集合相同。权重完全相同的最大权匹配可以有多组最优边。确定性指的是这条排序后的求解路径稳定，不是“任意并列最优解里只有一组边”。

## 8. 非二部图

`BlossomMatcher` 建的是 `SimpleWeightedGraph`，不是二部图算法。任意合法互惠关系可以进同一张图。

## 9. 精度和权重范围

JGraphT 把边权存成 double。双精度整数在 `2^52` 以内是精确的。V4 的编码测试在 10,000 人的上限上检查 G2 和 G4 的匹配总权重仍小于 `2^52`。V3 的 AI 乘 `1_000_000` 不在这条保证里。

## 10. 可复用部分

复用 `BlossomMatcher`、`GreedySolver`、`PairKey` 和 JGraphT。不复制图算法。

`Edge` 必须带 `ScoreBreakdown`，而且 `MatchingSolver` 自己计算 V3 权重。适配器构造全 0 的 `ScoreBreakdown`，只把 V4 权重放进 `Edge.weight()`。`BlossomMatcher` 只读 `weight()`。生产求解器没有被调用。

穷举检查器 `ExhaustiveMatcher` 只用于最多 12 人的测试。它不是生产求解器。
