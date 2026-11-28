# V4-I02 用户池契约

`ProductMatchingPoolReader` 从产品表读取一座城市、一个周期。调用方不能传入授权、版本或画像正文。

入池同时要求：

- `PoolGate` 没有排除原因。
- `cohort` 等于本次请求。
- 匹配授权是 `GRANTED`。`UNKNOWN` 不入池，`REVOKED` 拒绝。不使用 `legacyAllowUnknown`。
- 画像处理授权有效，并且存在当前快照。
- 当前快照的来源版本和输入哈希与当场投影一致。

排除原因：

| 原因 | 含义 |
|---|---|
| ACCOUNT_INACTIVE | 暂停、停用，或状态已经离开可匹配集合 |
| ONBOARDING_INCOMPLETE | 入驻或照片未完成 |
| MATCH_CONSENT_MISSING | 没有匹配授权，含 UNKNOWN |
| MATCH_CONSENT_REVOKED | 授权已撤回 |
| PROFILE_PROCESSING_NOT_GRANTED | 没有画像处理授权 |
| PROFILE_UNAVAILABLE | 没有当前画像 |
| STALE_PROFILE | 来源版本或哈希已经变化 |
| INVALID_BUSINESS_FACTS | 年龄、性别或地点不足以构成候选 |
| OUTSIDE_COHORT | 业务 cohort 不一致 |
| OPEN_MATCH | 已有未结束匹配 |

合法但稀疏的画像，例如不愿提供、不限制、未映射问卷，不使用这些原因。它们可以入池，然后由评分契约决定能不能推荐。
