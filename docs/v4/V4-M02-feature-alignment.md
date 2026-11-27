# V4-M02 特征对齐

版本 `align-1`。每一条都对照 `FeatureRegistry` `features-1` 检查：偏好码必须属于偏好区，自身码必须属于自身区。允许部分满足的刻度必须和登记值域一致。

| 偏好 | 自身 | 部分满足 |
| --- | --- | --- |
| `partner.pace` | `self.pace` | `slow`、`steady`、`fast` 只承认相邻 |
| `partner.communication` | `self.communication` | 不承认。`quiet`、`direct`、`frequent` 不是一把尺子 |
| `partner.social.initiative` | `self.social.initiative` | `low`、`steady`、`high` 只承认相邻 |
| `partner.schedule` | `self.schedule` | `early`、`flexible`、`late` 只承认相邻 |

`partner.pace=slow` 对 `self.pace=steady` 是部分满足。`slow` 对 `fast` 是不满足。`partner.communication=quiet` 对 `self.communication=direct` 是不满足，不是半满足。

## 不评分的假设

`partner.support=close` 并不对应 `self.communication=frequent`。高频沟通没有被证明等于更多情感支持。这条假设写在 `FeatureAlignmentRegistry.unverifiedHypotheses`，评分器找不到 `partner.support` 的对齐，记 `NO_ALIGNMENT`。

没有把不同语义维度当成同一个特征。没有使用性别或其他人群刻板印象。兴趣标签可以同时出现在案例里，但兴趣相同不自动提高偏好满足分。

`partner.age`、`partner.height`、`partner.education`、`partner.gender` 继续由 `HardFilter` 处理。它们以 `DOCUMENTED_EXISTING_RULE` 出现时不进入软分。
