# V4-P02 测试报告

日期：2026-11-26。分支 `master_agent_v4`。

P01 架构验收基线：`ff4cf2169f5aff3839abfacc28e2e7bac3cdc998`。修复提交：`4f29edb067e6367b8c0f6d79b2113a3392467485`。已核实运行：

- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45774884362
- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45775384976

## 本机领域测试

命令：

```
cd api && mvn -B -pl aiditto-domain test
```

结果：`Tests run: 488, Failures: 0, Errors: 0, Skipped: 0`。P01 最终的 482 项仍在。新增 `ProfileExtractionTest` 5 项、`ProfileEvalTest` 1 项。

`SyntheticFixtureTest` 本次输出：

```
[synthetic-200] solved in 38 ms (build 24 ms, solve 13 ms): 83 pairs, 34 unmatched, 822 edges, solver BLOSSOM, violations 0
```

求解器、`Scorer`、`BlossomMatcher`、`HardFilter` 和 `rankAgainst` 没有改。

应用模块：

```
cd api && mvn -B -pl aiditto-app -am test -Dtest=ProfileExtractionParserTest -Dsurefire.failIfNoSpecifiedTests=false
```

`ProfileExtractionParserTest` 2 项通过。同一 reactor 把 `aiditto-app` 编译通过，因此 `ProfileExtractionIT` 不是未编译的空壳。

## 本机数据库测试

本机没有 PostgreSQL，也没有 Docker。`ProfileExtractionIT` 在本机 NOT_RUN。类在缺少 `TEST_DB_URL` 时不会假装通过。

新增并且写入 `.github/workflows/v4-ci.yml` 的 Failsafe 选择和验收清单：

1. `provisionalFeatureIsStoredAsARevision`
2. `modelExportAbsentDoesNotCallTheModel`
3. `processingGrantIsRequired`
4. `revokedModelExportDiscardsTheInFlightResult`
5. `revokedProcessingDiscardsTheInFlightResult`
6. `changedSourceDiscardsTheModelResult`
7. `sameInputDoesNotCallTheModelTwice`
8. `invalidJsonOrTimeoutLeavesTheDeterministicSnapshot`
9. `degradedAttemptCanBeRetried`
10. `deletingUserCannotWrite`
11. `concurrentExtractsCallTheModelOnce`
12. `forgedEvidenceDoesNotChangeTheCurrentHash`
13. `scenarioChoiceCanSupportASignalWithoutExportingThePrompt`

原来的 37 项 PostgreSQL 集成测试和 3 项 `TestDatabaseGuardTest` 仍在清单里。缺方法、跳过、失败或报错都会让验收步骤失败。

## CI

实现提交 `ef08182426d414c117d22ebf909baa02f0622ab1`。

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45780763981

这次运行的结论是 success。页面上的三个 job 都完成：`domain-tests` 51 秒，`integration-tests` 2 分 3 秒，`test-reports` 5 秒。运行总时长 2 分 14 秒。

`integration-tests` 里的「Reject a run that did not execute the IT scenarios」会在必需方法缺失、跳过、失败或报错时让 job 失败。该 job 是 success，所以 `ProfileExtractionIT` 的 13 个方法、原来的 37 项 PostgreSQL 集成测试和 `TestDatabaseGuardTest` 都在这次 PostgreSQL 上执行并通过。

运行日志和 artifact 需要登录才能下载。没有从 runner 上抄出 PostgreSQL 的 `version()`。工作流镜像是 `postgres:16`。没有把以前运行读到的 16.16 写成这次的新实测。

## 三项结论

- 工程质量：GO。范围是这条提取链路的编译、领域测试、Mock 行为和这次 PostgreSQL 集成测试。
- 模型理解：NO-GO。真实模型评估 NOT_RUN。详见 `V4-P02-eval-report.md`。
- 生产启用：NO-GO。`processing-enabled` 仍是 false。账户清除还没有调用 `erase`，匹配同意撤回还没有调用 `revokeProcessing`。本轮没有生产提取、没有部署、没有合并 V3。

算法质量仍是 NO-GO。
