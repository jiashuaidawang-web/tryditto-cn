# V4-M02 双向契合度设计

包名是 `cn.aiditto.domain.compatibility.v4`。它不替换生产评分，也不进入 `WeekRunner`。

## 评分对象

`CompatibilitySubject` 带有用户 id、画像版本、生命周期代次、是否当前、是否撤回、合成来源、`SelfProfile`、`PreferenceProfile`，以及特征旁路的 `SemanticSupport`。候选人对象只用于现有 `HardFilter`。支持状态不写进新的画像模型。

召回 Map 不是评分输入。候选边确定之后，评分读取这份完整合成画像。

## 单向计算

`DirectionalCompatibilityScorer` 只计算 chooser.preference → target.self。反向是另一次调用。每条保留的偏好给出 preferenceCode、目标特征、期望值、实际值、重要性、操作符、证据状态、满足状态、权重和 reasonCode。

满足状态：

- `SATISFIED`
- `PARTIALLY_SATISFIED`，只在对齐表登记了相邻刻度时使用
- `UNSATISFIED`
- `UNKNOWN`，偏好已知、对方这项自身特征缺失。它不是 0，也不是 50
- `NOT_APPLICABLE`
- `CONTESTED`，报告矛盾，不把它当成满足或不满足

`WITHHELD`、`PROVISIONAL`、未通过 `MatchingFeatureGate` 的支持（含 `SEMANTIC_SUPPORT_PENDING`、`REFERENCE_VALID`、`REJECTED`）不进入可用明细。`UNBOUNDED`、`ANY`、受控引用和已有硬规则不记成匹配。没有对齐的特征保持 `NO_ALIGNMENT`，不用字符串替换去发明映射。

## 硬约束

`BidirectionalCompatibilityEngine` 只接受调用方给出的 `CompatibilityContext`。它先复用 `PoolGate`、`ConsentPolicy` 和 `HardFilter`。历史配对留在输入里，不会像发布资格那样先把当前配对从历史中拿掉。任一安全条件失败时，可排序分数为空，画像明细也为空，后续推理读不到这些行。硬约束失败的状态是 `HARD_VETO`。资料不是当前版本时是 `NOT_CURRENT`。其余未授权状态是 `NOT_ELIGIBLE`。

## 结果

`PairCompatibilityResult` 记录双方身份、两个画像版本、两个代次、硬约束结果、两个方向分、两个覆盖率、五种聚合、强偏好冲突、未知项、证据明细、`compat-1`、`features-1` 和 `align-1`。

相同输入得到相同结果。交换双方后，两个方向分对调，两侧都有定义时聚合结果不变。

一个方向没有已知分时，聚合为空。不把缺失补成 0 或 100。分数不是喜欢概率、接受概率或关系成功率。
