# V4-F03 测试报告

本地 `mvn -B -pl aiditto-domain test` 在校准改成开发集拟合、保留集评分之后重新跑过。Surefire 合计 593 项，失败 0，异常 0，跳过 0。F02-FINAL 是 587 项。新增 6 项都在 `StrategyContractTest`：标签和分数分离、漏斗分母、空样本和单一类别、策略不编造未发布结果、提案不能激活、十个实验室案例锁定 JSON。

72 条 F02 冻结标签仍由 `LearningEvalTest` 对照 `docs/v4/f02-learning-cases.json`。F03 的观察和实验室 JSON 由 `StrategyContractTest` 对照 `docs/v4/f03-observations.json` 和 `docs/v4/f03-lab-cases.json`。

本机没有 PostgreSQL。`StrategyEvaluationIT` 只在 CI 的 Postgres 16 上执行。应用模块 `mvn -B -pl aiditto-app -am test-compile -DskipTests` 通过。测试编译不是集成测试通过。

`StrategyEvaluationIT` 的方法：

- `publishedSnapshotStaysBoundAfterALaterProfile`
- `missingLearningConsentStillRecordsTheGroup`
- `profileLearningDoesNotBlockGroupEvaluation`
- `revokedSourceWithdrawsTheObservation`
- `secondClaimDoesNotRestoreAWithdrawnObservation`
- `erasureDropsTheObservationFromTheLegalSet`
- `proposalCannotBeActivated`

这些名字在 `.github/workflows/v4-ci.yml` 的必需清单里。原有的 `FeedbackEventIT` 和 `ProfileLearningIT` 方法都还在。

CI 地址在本分支推送并跑完四个作业之后回填。远程 Surefire 总数需要管理员权限才能读取，这里不另报一份远程合计。
