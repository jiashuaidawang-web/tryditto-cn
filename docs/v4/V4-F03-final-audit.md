# V4-F03-FINAL 收口审计

这一轮收口群体评估的观察来源、撤回、标签口径、校准和可回放契约。没有改 V1–V22，没有改冻结的 `f03-observations.json` 和 `f03-lab-cases.json`，没有把校准结果写回评分，也没有接入生产匹配快照。

## R01 权威观察

`ObservationBuilder.fromLedger` 从 `FeedbackLedger.project()` 取结果标签，从 `MatchFeedbackContext` 取匹配身份、画像版本、生命周期代次和策略版本，从有效的 `MATCH_PUBLISHED` 取发布时间，从 `MatchScoreSnapshot` 取分数。调用方提交的来源编号必须等于有效事件集合。

只有 A 的接受事件时，B 的回应保持 `UNKNOWN`，见面不是 `BOTH_ATTENDED`，继续意愿不是双方表达。缺少任一方反馈授权返回 `NO_FEEDBACK_CONSENT`。不存在或已被取代的编号返回 `SOURCE_MISMATCH`。有效撤回返回 `RETRACTED_SOURCE`。

合成夹具仍走 `MatchingObservation` 构造器，文档入口标记为 `FIXTURE`。

## R02 派生与撤回

`matching_observation_source` 记录观察和来源事件。`recordBuilt` 只领取可消费事件。`withdrawLinked` 撤销该观察链接的凭证，并把观察标成撤回。另一条观察的凭证保持 `APPLIED`。再次领取或再次写入不能把已撤回观察恢复成合法样本。

`invalidateDataset` 把该数据集版本的策略提案和 `group_calibration_run` 标成无效。其他版本保持有效。没有重新打开 `valid` 或 `activated` 的方法。`activated` 仍由数据库约束保持为 false。

## R03 标签

`RelationshipLabels` 和 `f03-synthetic-1` 保留。新口径是 `ExplicitOutcome`，版本 `f03-labels-2`。

同一 128 条上，旧契约双方拒绝 24 条，新契约未达成双方接受 44 条。多出来的 20 条是旧口径的单方接受。超时、未成熟、无人响应、缺失和无授权各 8 条。一方接受加另一方未知不会被写成明确拒绝。

## R04 校准

保序回归先按分数聚合，再做 PAV。相同分数、不同标签在两种输入顺序下得到同一组概率。输出包含样本数、状态、Brier、Log Loss 和校准误差。新增训练集常数频率对照。未校准的 `score/100` 只作为数学对照。

冻结划分上，修复后的保序保留集数字与修复前的记录相同。见面和继续交往仍是 `INSUFFICIENT`。真实校准是 `NOT_RUN`。这些数不进入 `Scorer`、M02 或 M04。

## R05 回放

`ObservationDocument` 版本 `f03-observation-2`。序列化后再解析，规范串、`ExplicitOutcome` 和漏斗计数一致。只改回应截止时间会改变 SHA-256 摘要。`TimeSplit` 使用 `publishedAt`。同一匹配周期、不同发布时间的两行可以被切开。不同周期、同在截止点一侧的两行留在同一侧。

## 测试

本地 `mvn -B -pl aiditto-domain test`：599 项，失败 0，异常 0，跳过 0。`mvn -B -pl aiditto-app -am test-compile -DskipTests` 通过。

`StrategyEvaluationIT` 新增 7 个方法，连同原来的 7 个都在 CI 必需清单里。本机没有 PostgreSQL，集成测试本地 NOT_RUN。

## 结论

F03-FINAL 的本地领域工程通过。代码提交 `b82429b34bc145a26dd54ddde27c3d4fc5ca5be1`。集成测试要等 CI 的 Postgres 16。真实校准、真实策略提升和真实关系质量未验证。生产启用 NO-GO。最终匹配算法质量 NO-GO。
