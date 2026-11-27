# V4-M01 最终审计修复

修复留在 `feature/v4-recall`。基准仍是 `f6189dc517eee74edd7c6ce78e5a5ae48c155191`。没有改 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom、S01、P02 或 E3。

| 项 | 反例 | 修复 |
| --- | --- | --- |
| R01 删除后复活 | V2 写入后撤回，延迟的 V1 或 V2 再提交 | `VectorLifecycle` 记下代次和画像水位。同代再写得到 `REJECT_WITHDRAWN` 或 `REJECT_STALE`。新代次 `authorize` 之后才能写。更老的撤回得到 `REJECT_STALE`，新向量还在。并发提交和撤回结束后，可见向量要么是 2 条，要么是 0 条，随后延迟提交不能恢复 |
| R02 双向版本 | 第二次 embedding 抛错；写入中途撤回；同一版本换模型 | `V4VectorSync` 先取齐两个向量再 `commit`。失败返回 `REJECT_PARTIAL`，索引仍是 0。中途撤回返回 `REJECT_WITHDRAWN`。另一模型返回 `REJECT_CONFLICT`，旧模型仍可搜，新模型搜不到。`vector()` 返回副本 |
| R03 准入 | 同一码两个值、未支持的推断、WITHHELD、身份不一致 | `RecallProfileContract` 复用 `FeatureRegistry` 和 `MatchingFeatureGate`。矛盾值整组丢弃。夹具入口和 `recall-contract:` 入口分开。向量同步还要求同意和 `PoolGate` |
| R04 挑战集 | 原 100 人主要靠同组互为正例 | 220 人的 `RecallChallenge` 另写标签。原集、权重和 Recall 计数保留 |
| R05 对照 | 原来的 B0 只是打乱后的合法池 | B0 随机池、B1 未改动的 `Scorer`、B2 仅 R1、B3 为 R1–R5。池、资格、预算、标签和分母相同。`StructuredRecallService` 因要查 profile SQL 而没有复用 |
| R06 指标 | 缺结果被跳过；32 位键；未标注被藏进一个 Precision | 正例分母来自标签。缺结果记为未命中。同时给出 labeled precision 和按槽位的 precision，以及未标注槽位数。有向边使用完整 long。`RecallSafetyAudit` 复查最终候选。零候选分成无资格、无合法对象、稀疏和召回失败 |

隔离 Qdrant 由 `V4QdrantStore` 把同一套水位写进 `v4_it_` 集合的 payload。它不假设 Qdrant 有跨点事务：两个向量放在同一个点里，只有决策允许时才写。生产集合名仍然被拒绝。
