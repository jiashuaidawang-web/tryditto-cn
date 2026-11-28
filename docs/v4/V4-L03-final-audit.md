# V4-L03-FINAL 审计

Match Lab 第一版在此冻结。本轮不新增实验室模块。

## 分数单位

`DirectionalCompatibilityScorer` 把方向分写成 `knownSatisfaction * 100 / knownWeight`，没有已知权重时是 null。覆盖率是 `knownWeight / evaluable`，没有可评估权重时是 null。`GlobalMatchingResult.MatchedPair.fitScore` 与 `fitCenti / 100` 同一量纲，也是 0–100。

`MatchScoreSnapshot` 接收 0–100 的整数分和 0–1 的覆盖率。`SandboxEvaluation.scorePoints` 只在这个范围内四舍五入。1 分仍是 1 分。0.5 分四舍五入成 1，不乘 100。null、NaN 和越界抛出 `SCORE` 或 `COVERAGE`，不写成 0。

## 统计口径

匹配事实的分母是工作区账本。授权列是双方反馈处理授权。未成熟来自 `RelationshipLabels`：响应窗口用 Y1，见面窗口用 Y2，继续意愿窗口用 Y3。

群体评估可读的分母是成功建成的观察。建不成的观察记入缺失。学习资格使用 `FeedbackLearningEligibility.matchFactEligible`，学习授权另报，不代替反馈授权。

## 数据集和模型结论

沙箱观察的数据集是 `sandbox-ledger-1`。策略对照仍读取冻结实验 `f03-synthetic-1`。历史标签文件没有改写。

安全页把「本页模型调用 NOT_RUN」和「历史真实模型质量 NO-GO」分开。后者的来源是 `V4-M03-live-eval-report.md`，版本日期 2026-11-27。风险行带有来源文件和版本，页面不能改这些状态。

## 没有实现、因此不是 GO

| 能力 | 状态 |
| --- | --- |
| 当前引擎重放内存中的 M04 图 | LIMITED |
| 只凭文本反序列化后回放 | NOT_IMPLEMENTED |
| 特征级结果差异 | NOT_IMPLEMENTED |
| 校准曲线 | NOT_IMPLEMENTED |
| 单独重跑历史 M02 输入 | NOT_IMPLEMENTED |
| 当前账本上的 F01 投影和 F03 标签 | LIMITED |

真实关系质量 NOT_RUN。真实策略提升 NOT_IDENTIFIED。生产启用 NO-GO。最终匹配算法质量 NO-GO。
