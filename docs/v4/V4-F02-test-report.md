# V4-F02 测试报告

本地 `mvn -B -pl aiditto-domain test` 的 Surefire 合计 585 项，失败 0，异常 0，跳过 0。其中 F01 收口后的 576 项仍在，F02 新增 9 项。PostgreSQL 集成测试在 CI 的 Postgres 16 上执行，本机没有数据库。应用模块测试编译通过。

领域侧新增 `ProfileLearningEngineTest`、`LearningEvalTest` 和 `ProfileLearningLabTest`。原有 F01 领域测试仍在。应用模块测试编译通过。

`ProfileLearningIT` 覆盖提案保存、确认写入、拒绝和过期、待确认提案不进匹配、非法特征和硬约束、跨用户和无授权、来源撤回、重复确认、旧版本、并发确认、删除竞态、确认和消费凭证一起回滚，以及写后再读。这些方法名在 `.github/workflows/v4-ci.yml` 的必需清单里。

CI 地址在分支推送后补记。
