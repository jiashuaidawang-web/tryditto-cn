# V4-F03 测试报告

本地 `mvn -B -pl aiditto-domain test` 在 F03-FINAL 之后重新跑过。Surefire 合计 599 项，失败 0，异常 0，跳过 0。F03 实现当时是 593 项。新增 6 项在 `ObservationCloseoutTest`：单方接受不能变成双方结果、缺失授权和已撤回或未知来源被拒绝、新旧标签口径的样本量、保序与输入顺序无关、观察文档往返、发布时间拆分不等于周期字段。原有 `StrategyContractTest` 的 6 项仍在。

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
- `oneAcceptDoesNotCreateMutualLabels`
- `correctedOrUnknownSourceIsRefused`
- `oneConsentDoesNotAuthorizeBoth`
- `withdrawLeavesTheOtherObservation`
- `replayDoesNotDuplicateOrRestore`
- `concurrentBuildsLeaveOneObservation`
- `datasetChangeInvalidatesProposalAndCalibration`

这些名字在 `.github/workflows/v4-ci.yml` 的必需清单里。原有的 `FeedbackEventIT` 和 `ProfileLearningIT` 方法都还在。V22 没有改。来源表和校准失效列在 V23。

本机没有 PostgreSQL，所以上面 14 个集成方法在本地是 NOT_RUN。应用模块 `mvn -B -pl aiditto-app -am test-compile -DskipTests` 通过。测试编译不是集成测试通过。

F03 实现提交 `094e161c20198aef5a23a8dce76cf0b58e77d858`。当时的 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46118005635 ，四个作业 success。F03-FINAL 的提交和 CI 写在 `V4-F03-final-audit.md`。远程 Surefire 总数需要管理员权限才能读取，这里不另报一份远程合计。
