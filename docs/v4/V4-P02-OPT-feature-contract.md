# V4-P02-OPT 特征契约

`profile-extract-v1` 没有把特征注册表交给模型。E1 增加 `FeatureContractCatalog`。它从 `FeatureRegistry.v1()` 生成契约，只保留同时满足下面三条的特征：敏感级别不是 `SENSITIVE_REF`，来源包含 `MODEL_INFERENCE`，并且在 SELF、PREFERENCE 或 BEHAVIOR 上允许模型推断。记忆分节不进入契约。

模型不得输出契约之外的特征码。未知码保持原样进入解析结果，再由校验拒绝，不会被改写成最近的合法码。无法判断时允许返回空列表。SELF 和 PREFERENCE 的 `behaviorKind` 必须为空。只允许问卷或确定性来源的特征不在契约里，Prompt 明确禁止重新输出它们。

## 允许模型提取的 11 个特征

| featureCode | 分节 | 合法取值 |
| --- | --- | --- |
| behavior.scenario.signal | BEHAVIOR | high, low, steady |
| partner.communication | PREFERENCE | direct, frequent, quiet |
| partner.pace | PREFERENCE | fast, slow, steady |
| partner.schedule | PREFERENCE | early, flexible, late |
| partner.social.initiative | PREFERENCE | high, low, steady |
| partner.support | PREFERENCE | close, space, steady |
| self.communication | SELF | direct, frequent, quiet |
| self.exploration | SELF | high, low, steady |
| self.pace | SELF | fast, slow, steady |
| self.schedule | SELF | early, flexible, late |
| self.social.initiative | SELF | high, low, steady |

每个特征在 Prompt 里还有 `displayName`、`semanticDefinition`、`allowedEvidenceKinds`、`permittedSourceTypes`、`explicitExamples` 和 `counterExamples`。例子只给模型看，没有写进封闭短语表，也没有抄进 live-profile-v2。`lifestyle.hobbies`、年龄、性别、学历和职业不在这张表里。

契约按 `featureCode` 排序后渲染，因此下面的摘要可以跨进程复现。完整正文在 `docs/v4/live-eval-results/opt/prompts/`。摘要是 `SHA-256(版本名 + 换行 + system 正文)`。

| 版本 | 用于 | SHA-256 |
| --- | --- | --- |
| profile-extract-v1 | E0，正文未改 | `bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195` |
| profile-extract-v2 | E1 | `b38785ae72df0efceebf302877a3dbc47e87229ba3cc2175f25e72ee67dfe5bd` |
| profile-extract-v3 | E2 | `b7b5a3b572d66f20c8b42aa62e083a445363716ef5d2cbbcdb00054b6c4ae16d` |
| profile-extract-v4 | E3、E4 提取、E5 | `2fbabb105dbd68642e5a514e68e7d66fa73199567c2c29c61d44b99603ffb48a` |

v1 的冻结摘要由 `FeatureContractCatalogTest` 固定。两次独立 JVM 算出的 v2、v3、v4 摘要一致，服务器预检打印的 v2 摘要也是同一枚。
