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

推送 `master_agent_v4` 之后把运行 URL、PostgreSQL 版本、领域测试总数和 `RelationshipProfileIT` 的方法计数补进这一节。在那之前，数据库验收不能写成已经通过。
