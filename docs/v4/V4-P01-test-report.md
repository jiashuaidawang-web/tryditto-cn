# V4-P01 测试报告

日期：2026-11-26。分支 `master_agent_v4`。实现前的 HEAD 是 `c5f126b4e634d7fd5dc58d90a6fa78f39c4c4379`，工作区干净，且与 `origin/master_agent_v4` 一致。

算法质量仍是 NO-GO。本轮不部署，不合并 V3，不启动 V4-P02。

S01 的验收记录仍在 `V4-S01-test-report.md`。前两次失败和后来两次成功都保留。成功运行是：

- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45591470398
- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45592507019

那两次的 PostgreSQL 是 16.16。领域测试当时 463 项通过。`PublishSafetyIT` 14、`ConsentLegacyIT` 1、`MatchIT` 4、`TestDatabaseGuardTest` 3，0 failures、0 errors、0 skipped。

## 本机领域测试

命令：

```
cd api && mvn -B -pl aiditto-domain test
```

结果：`Tests run: 477, Failures: 0, Errors: 0, Skipped: 0`。比 S01 验收时的 463 多出的 14 项是 `RelationshipProfileTest`。

V3 求解基线仍在其中。`SyntheticFixtureTest` 输出：

```
[synthetic-200] solved in 47 ms (build 31 ms, solve 15 ms): 83 pairs, 34 unmatched, 822 edges, solver BLOSSOM, violations 0
```

200 人、83 对、0 条硬约束违规。求解器、`Scorer` 和 `HardFilter` 没有改规则。

领域测试覆盖：自身特征与伴侣偏好分离、值域、NaN 和 Infinity、未知不是 0.5、不限不是缺失、不愿透露不是否定、推断不能成为硬约束、模拟内容不能成为观察、证据必须属于该用户且 id 由服务端计算、撤回证据不能支撑当前值、敏感原文不进快照、相同输入哈希稳定、Map 和枚举顺序不影响哈希、输入变化则哈希变化。

六组虚构快照在 `docs/v4/profiles/`。它们没有写入数据库。

## 本机数据库测试

本机没有 PostgreSQL，也没有 Docker。`RelationshipProfileIT` 的 7 个方法在本机 NOT_RUN。这不是跳过断言，测试类没有在没有 `TEST_DB_URL` 的情况下假装通过。

这 7 个方法已经写进 `.github/workflows/v4-ci.yml` 的 Failsafe 选择和验收清单：

1. `persistsAndReadsTheSnapshot`
2. `sameInputDoesNotCreateAnotherVersion`
3. `differentInputCreatesTheNextVersion`
4. `olderSourceCannotOverwrite`
5. `uniqueCurrentAndHashConstraints`
6. `legacyProfileAndTasteStayReadable`
7. `deletingUserIsRefusedAndEraseRemovesRows`

S01 的 19 个集成方法和 `TestDatabaseGuardTest` 的 3 个方法仍在同一份清单里。验收步骤仍会在方法缺失、跳过、失败或报错时让 job 失败。

应用模块 `test-compile` 已通过，所以这 7 个方法不是只写了类名、编译失败的空壳。真实执行次数以 GitHub Actions 的 Failsafe 报告为准。

## CI

提交 `76468ce775a004c821e116bb07919531920f1efc`。

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45769819902

这次运行的三个 job 都是 success：`domain-tests`、`integration-tests`、`test-reports`。

`integration-tests` 里的「Reject a run that did not execute the IT scenarios」也是 success。这一步在任一必需方法缺失、跳过、失败或报错时会让 job 失败。因此下面这些方法都在 PostgreSQL 上实际执行并通过：

- `RelationshipProfileIT`：7 个方法，即上面列出的持久化、重复输入、新版本、旧源拒绝、唯一约束、旧表兼容、注销拒绝和删除。
- `PublishSafetyIT`：14。`ConsentLegacyIT`：1。`MatchIT`：4。合计仍是 S01 的 19。
- `TestDatabaseGuardTest`：报告存在，且 tests > 0、failures、errors、skipped 都是 0。脚本不把「编译通过但没跑」算成功。

`domain-tests` 的「Run domain tests」成功。本机同一套命令是 477 项、0 失败。运行日志和 artifact 需要登录才能下载，所以没有从 runner 上抄出 PostgreSQL 的 `version()` 原文。工作流使用的镜像是 `postgres:16`。S01 已验收的两次运行读到的是 16.16。这次没有把那个数字写成新的实测值。

算法质量仍是 NO-GO。没有部署，没有合并 V3，没有启动 V4-P02。

## 最终验收修复

命令仍是 `cd api && mvn -B -pl aiditto-domain test`。本机结果：`Tests run: 482, Failures: 0, Errors: 0, Skipped: 0`。原来的 477 项都还在。新增的 5 项是 `ProfileAuditTest`。`RelationshipProfileTest` 仍是 14 项。`SyntheticFixtureTest` 仍是 200 人、83 对、34 人未匹配、822 条边、0 条硬约束违规。

本机没有 PostgreSQL，也没有 Docker。集成测试不在本机假装通过。`mvn -B -pl aiditto-app -am test-compile` 已通过。

`RelationshipProfileIT` 原有 7 个方法都保留。清单里新增：

8. `delayedSourceCannotReplaceAHigherWatermark`
9. `returningToAnEarlierHashCreatesAnotherVersion`
10. `claimedSourceMustMatchTheStoredProfileVersion`
11. `sameSourceDifferentContentIsADeterministicConflict`
12. `modelRevisionIsRefused`
13. `revokedGenerationCannotWriteAndANewGenerationCan`
14. `matchingConsentAndModelExportDoNotGrantProcessing`
15. `offeredSnapshotMustMatchTheServerProjection`
16. `concurrentSavesKeepASingleCurrentRow`
17. `retractCurrentBlocksTheOldGeneration`
18. `eraseRacesSaveAndLeavesNoReadableProfile`

S01 的 19 个集成方法和 `TestDatabaseGuardTest` 的 3 个方法仍在 `.github/workflows/v4-ci.yml`。没有删除断言，没有跳过测试。

Actions 的运行 URL、job 结论和 PostgreSQL 实测写在下面。镜像仍是 `postgres:16`。artifact 下载返回 401，没有从 runner 抄出新的 `version()`。S01 已验收的运行读到的是 16.16，那个数字不记成这次的新实测。

- 代码提交：`4f29edb067e6367b8c0f6d79b2113a3392467485`
- 运行：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45774884362
- 结论：`domain-tests`、`integration-tests`、`test-reports` 都是 success。`Run domain tests` 成功。`Reject a run that did not execute the IT scenarios` 成功，所以清单里的 `RelationshipProfileIT` 18 个方法、S01 的 19 个方法、`TestDatabaseGuardTest` 都实际执行，且没有失败、报错或跳过。

