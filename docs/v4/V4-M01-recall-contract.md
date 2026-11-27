# V4-M01 召回输入契约

召回读的是 `RecallSubject`，不是一份新的画像体系。资格视图复用 `Candidate`。特征是否能进入召回，由 `MatchingFeatureGate` 决定：只有 `USER_CONFIRMED` 或 `SEMANTIC_SUPPORTED` 可以留下。

| 材料 | 召回里怎么放 |
| --- | --- |
| 自身枚举 | `selfEnums`，只参与 R2，以及作为别人偏好的被匹配方 |
| 明确伴侣偏好 | `preferenceEnums`。R3 用寻求者的偏好看对方自身，R4 反过来 |
| 合法爱好 | 爱好集合。未通过门控的爱好整组丢弃 |
| 未确认推断、`PENDING`、`REFERENCE_VALID` | 不进入特征图 |
| 已撤回 | `withdrawn=true`，不参与检索 |
| 旧版本 | `current=false`，不参与检索 |
| UNKNOWN、空值 | 不写入。两个人都缺同一个字段，不算一致 |
| UNBOUNDED、WITHHELD | 不写入，也不能当成「和谁都匹配」 |
| 自由文本、职业、开场白 | 门控直接丢掉，向量文本里也不出现 |

合成夹具的入口是 `RecallIngress.SYNTHETIC_FIXTURE`，来源必须以 `synthetic-fixture:` 开头。未来生产入口是 `RecallIngress.FUTURE_PRODUCTION`，来源必须以 `recall-contract:` 开头。后者只是契约，不连接用户库。

`RecallProfileContract` 复用 `FeatureRegistry` 和 `MatchingFeatureGate`。它检查特征码、值域、自身或偏好分节、证据来源和语义状态。同一特征码的两个不同确定值整组丢弃，不按 Map 的最后一次写入当成事实。无语义支持的模型推断和反馈观察不能进入。身份上，`userId`、`Candidate.id` 和 `MatchingConsent.userId` 必须相同。

向量同步除了 `searchable()`，还要求代次、匹配同意为 `GRANTED`，以及 `PoolGate` 通过。撤回、过期和未授权的人只会留下水位，不会被检索。

模型推断不能在召回阶段变成硬约束。硬约束只来自既有的账户、同意和 `HardFilter`。

`MatchingFeatureGate` 仍然只看语义支持状态。它还没有被生产 `Scorer` 调用。本轮召回是它的第一个离线调用方，生产打分仍然不读它。
