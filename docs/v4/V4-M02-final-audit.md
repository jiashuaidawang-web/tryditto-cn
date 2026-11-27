# V4-M02-FINAL 审计

收口留在 `feature/v4-bidirectional`。基线实现是 `17d0740f04af3f0377c286710124d464666ccc0a`，上一份报告是 `19f5d4caf99697245b57836fc3aaaf1628f9fc48`。没有第三轮大规模重构。没有改 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom、S01、M01 权重或冻结评估标签。生产评分没有启用。

| 项 | 反例 | 修复 |
| --- | --- | --- |
| R01 资格上下文 | 历史配对仍在输入里；同意已撤回 | 引擎不再自己构造空的 `MatchingInput`。调用方给出 `CompatibilityContext`。先走 `PoolGate`、`ConsentPolicy.allows` 和 `HardFilter`，历史配对不会被拿掉。失败时分数和画像明细都为空 |
| R02 覆盖率 | 四条等权重有效偏好，一条可信且满足，三条目标待确认 | 已知分可以是 100，覆盖率是 0.25。目标缺失、待确认、不公开或支持不足都留在分母。偏好本身无效则不进入分母。明细不写 `WITHHELD` |
| R03 矛盾与强偏好 | 一条满足，一条 `CONTESTED`，重要性都是 `SUPER` | 矛盾进入覆盖率分母，不记满足也不记不满足。已知分可以是 100，覆盖率是 0.5。决策拒绝推荐，离线推理仍可研究。冻结标签没改 |
| R04 决策契约 | 后续阶段各自解释 60 和 0.60 | `CompatibilityDecision`，策略版本 `compat-decision-1`。推荐资格和推理资格分开。推理资格不能越过授权、隐私和硬约束 |

## 基线有没有移动

没有。同一套冻结标签上，B5 开发集仍是 264/264，保留集仍是 99/99。选择结果仍是 B5。单向误推荐、证据不足误推荐、硬约束违规、未知误处理和 WITHHELD 泄露都仍是 0。召回分离实验仍是 Top-20 为 0/8、Top-50 为 4/8、完整合法池为 8/8。

案例 JSON 有两处如实变化。M02-C7 全部矛盾时，覆盖率从空变成 0，因为矛盾权重进入分母而已知权重为 0。分数和五种聚合仍为空。M02-C8 的硬约束码从 `AGE` 改为资格契约里的 `HARD_AGE`，画像明细从 8 行改为 0 行。

有一处新契约比冻结评估里的 B5 推荐更严，数字本身没变。不公开目标的关系：已知分 100，覆盖率 0.75，B5 聚合仍是 100。`CompatibilityDecision` 拒绝推荐，原因是 `STRONG_PREFERENCE_UNEVALUABLE`，同时允许离线推理。评估标签仍是 `STRONG`，没有改成别的等级来维持或制造满分。

覆盖率分母现在是已知权重、未知权重和矛盾权重之和。公式版本仍是 `compat-1`。算术、五种聚合和 60 / 0.60 门槛没有另起版本。

## 本地验证

`mvn -B -pl aiditto-domain test` 为 532 项通过，其中包括四项收口反例。随后补上的不公开目标决策反例单独通过，`M02FinalTest` 为 5 项通过。B5 仍是 264/264 和 99/99。

GitHub Actions：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45990401708 。提交 `fbb83a5e576de711680d9a07aeb73b62a65c6e18`。domain-tests、integration-tests、qdrant-isolation、test-reports 都是 success。

## 结论

M02-FINAL 工程验收：GO。四项安全反例和 CI 都通过。冻结基线没有移动。

召回端到端质量：NO-GO。

真实模型理解质量：NO-GO。本轮没有调用模型。

生产启用：NO-GO。

最终匹配算法质量：NO-GO。
