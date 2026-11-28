# V4-F02 测试报告

本地 `mvn -B -pl aiditto-domain test` 的 Surefire 合计 587 项，失败 0，异常 0，跳过 0。F02 实现时是 585 项。F02-FINAL 在 `ProfileLearningEngineTest` 新增 2 项：矛盾报告不是可用事实，以及重复报告必须对应真实事件、所有权、方向、时间窗口和撤回。72 条冻结标签仍由 `LearningEvalTest` 对照原文件。

PostgreSQL 集成测试在 CI 的 Postgres 16 上执行，本机没有数据库。应用模块 `mvn -B -pl aiditto-app -am test-compile -DskipTests` 通过。测试编译不是集成测试通过。

`ProfileLearningIT` 保留原来的 13 个方法，并新增：

- `questionnaireEvidenceSurvivesOnePreferenceUpdate`
- `reprojectionKeepsTheConfirmedPreference`
- `revokedGenerationDoesNotUseTheCommandFlags`
- `concurrentProfileWritesLeaveOneCurrentSnapshot`
- `storedProposalCannotBeExtendedOrRebound`
- `retractingTheSourceRemovesDerivedEvidence`

这些方法名在 `.github/workflows/v4-ci.yml` 的必需清单里。F02-FINAL 实现提交 `c74e9c1b5db1aa2c5c985da4a0984fd5521e0325`。CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46115262767 ，四个作业 success。集成作业成功，所以清单里的 `ProfileLearningIT` 方法已执行且没有失败或跳过。远程 Surefire 总数需要管理员权限才能读取，这里不另报一份远程合计。

F02 实现提交 `e24aae1f7f7b23a10aaec2a4f035fb0f7ed786bc` 的 CI 是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46061670392 。那次运行覆盖的是收口之前的代码。
