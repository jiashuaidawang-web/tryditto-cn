# V4-S01 测试报告

日期：2026-11-25。分支 `master_agent_v4`。审计提交 `77d2a2b9b4ab3a1495e549ddd3cdaf78785b54ff`。

三类结果分开记。领域测试跑过。真实数据库测试没有跑。真实用户效果没有评估。

算法质量仍是 NO-GO。本地没有 PostgreSQL，当时的发布闸门验收是 NO-GO。V4-INFRA-001 把同一套 IT 放到 GitHub Actions 的 PostgreSQL 16 上跑。下面「本地」和「CI」分开记。CI 结论以 Actions 的 job 结果为准，不以工作流文件为准。

## 领域测试：已执行

命令：

```
cd api && mvn -pl aiditto-domain test
```

退出码：0。`Tests run: 463, Failures: 0, Errors: 0, Skipped: 0`。没有未执行的领域测试。

V3 求解基线仍在这 463 个里面。`SyntheticFixtureTest` 输出：

```
[synthetic-200] solved in 46 ms (build 30 ms, solve 15 ms): 83 pairs, 34 unmatched, 822 edges, solver BLOSSOM, violations 0
```

200 人合成候选池，83 对，0 条硬约束违规。求解器没有为了同意闸门改规则。

| 测试 | 结果 |
| --- | --- |
| `ConsentPolicyTest` | 4 通过。缺记录是 UNKNOWN，合规模式下不允许；逃生口参数为 true 时 UNKNOWN 允许；REVOKED 在逃生口打开时仍不允许 |
| `EligibilityGateTest` | 9 通过。合规模式 UNKNOWN 失败；逃生口打开时 UNKNOWN 可以通过、REVOKED 不行；双方 GRANTED 且硬约束通过才 eligible |
| `MatchMachineTest` | 151 通过。Hold 不写 `publishedAt`、无 effect、第二次是重放 |
| `MatchMachinePropertiesTest` | 2000 次随机游走通过，事件里包含 Hold |
| `SyntheticFixtureTest` | 83 对，34 人未匹配，violations 0 |

这是领域测试，不是数据库测试，也不是真实用户效果。

## 应用编译：已执行，不是测试通过

```
cd api && mvn -pl aiditto-app -am test-compile -DskipTests
```

退出码：0。只说明 `PublishSafetyIT`、`ConsentLegacyIT`、`MatchIT` 能编译。没有运行。

## 真实 PostgreSQL：NOT_RUN

项目数据库版本写在 `deploy/docker-compose.v3.yml`：PostgreSQL 16。本轮没有启动那份 compose，没有连接 `127.0.0.1:5432`，没有改已有业务库。

本机检查：

- `command -v docker` 为空。`/Applications/Docker.app` 不存在。
- `~/.docker/bin/docker` 是断开的符号链接，指向 `/Applications/Docker.app/Contents/Resources/bin/docker`。
- 项目里没有 Testcontainers。Testcontainers 也要 Docker。
- `nc -z -G 2 127.0.0.1 5432` 退出码 1。`55432` 同样没有在听。
- `brew install postgresql@16` 停在拉取 formula。`raw.githubusercontent.com` 的 HEAD 在 15 秒后超时（curl 退出码 28，0 字节）。拿到的 formula 没有 Intel macOS 12 的 bottle，装上也会进入源码编译。本轮没有装成，没有启动实例。
- 因此没有数据库版本号可报。不是「测试通过」。

未执行的命令。数据库必须是新建的测试库。`ApiIT` 会 `flyway.clean()`，不要把 `TEST_DB_URL` 指到业务库或生产库：

```
cd api && TEST_DB_URL='jdbc:postgresql://127.0.0.1:55432/aiditto_s01_test?stringtype=unspecified' \
  TEST_DB_USER=root TEST_DB_PASSWORD= \
  mvn -pl aiditto-app -Dtest=PublishSafetyIT,ConsentLegacyIT,MatchIT test
```

Docker 可用时，应先起一个只给测试用的 PostgreSQL 16，映射到 55432，建空库 `aiditto_s01_test`，再跑上面的命令。不要启动 `deploy/docker-compose.v3.yml`。

`PublishSafetyIT` 计划覆盖、但本次 0 通过 / 0 失败 / 全部未执行：

- 管理接口、演示接口、手动插入的 UNKNOWN draft 不能正式发布
- GRANTED 的合成用户可以发布，重复发布不改版本
- 撤回先提交再发布；发布先提交再撤回，卡片和联系方式不再暴露对方
- 拉黑、注销、硬约束修改与发布并发，结束后不是 published
- 两个线程重复发布同一场，版本只加一次
- 两个场次共用用户，按 id 升序加锁，60 秒内结束
- 发布事务 `setRollbackOnly` 后仍是 draft
- Hold 不增加 `report`，不增加 `no_response_streak`
- 默认配置下 `legacyAllowUnknown` 为 false

`ConsentLegacyIT` 单独打开逃生口，计划断言 REVOKED 仍被 Hold，同时其他 UNKNOWN 仍能发布，用来证明这个开关不是合规模式。未执行。

并发用例使用独立线程、`CyclicBarrier` 和 `PROPAGATION_REQUIRES_NEW`。没有用 `Thread.sleep`。这些代码没有在 PostgreSQL 上跑过，不能据此声称没有死锁或 TOCTOU。

`MatchIT` 以及带环境变量才启用的 `AiCopyIT`、`FeedbackLearningIT`、`AgentRerankIT` 已改为发布前显式 `TestConsents.grantAll`。它们同样 NOT_RUN。默认跳过的三个 IT 在没设 `AIDITTO_STEP6_IT` / `AIDITTO_STEP7_IT` 时不会跑，这和数据库无关。

## 真实用户效果：未评估

没有真实用户流量，没有线上同意转化，没有生产发布抽样。合成夹具的 83 对不能当成真实用户效果。

## 验收对照

| 标准 | 状态 |
| --- | --- |
| 只有 GRANTED 能正式发布 | 领域测试通过。前两次 CI 的发布场景没有跑到，夹具在 `>= 8` 处失败 |
| UNKNOWN、REVOKED 不能正式发布 | 领域测试通过。`unknownCannotPublishAndAdminOrDemoCannotBypass` 在前两次 CI 通过。`ConsentLegacyIT` 通过 |
| 历史用户不自动授权 | V13 无回填。代码路径如此。没有拿历史库演练 |
| 普通、管理、手动、演示发布不能绕过 | `unknownCannotPublishAndAdminOrDemoCannotBypass` 在前两次 CI 通过 |
| 并发与回滚 | 前两次没有执行到。不能记为通过 |
| V3 基线 83 对、0 违规 | 领域测试通过 |
| 生产配置 | 未改，未部署 |

## CI：V4-INFRA-001

工作流 `.github/workflows/v4-ci.yml`。Runner 是 GitHub-hosted Ubuntu，Java 17，服务镜像 `postgres:16`，库名 `aiditto_s01_test`。细节见 `docs/v4/V4-INFRA-001.md`。

架构侧读到的前两次完整日志：

- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45586978463/job/139110954907
- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45588478771/job/139116095886

PostgreSQL `16.16 (Debian 16.16-1.pgdg13+1)`。领域 job 成功。`TestDatabaseGuardTest` 3 通过。`PublishSafetyIT` 2 项，1 通过 1 失败。`ConsentLegacyIT` 1 通过。`MatchIT` 4 通过。合计 7 项，6 通过，1 失败，0 errors，0 skipped。

失败方法 `grantedDraftsPublishAndUnsafeRacesDoNot`，`PublishSafetyIT.java:122`。实际草稿数 3，断言 `>= 8`。异常是 `AssertionError`。后面的回滚、撤回、拉黑、注销、改硬约束、重复发布和锁顺序都没有跑到。

3 对的来源：北京池 130 人里只有 3 个 demo 账号。synthetic 不能和 synthetic 配对，所以求解最多 3 对，这次正好 3 对。领域测试的 83 对没有带上这个 cohort 规则。不改算法。

本轮把那一个方法拆成独立场景，每个场景用自己的 demo 成年用户和 draft。`WeekRunner` 仍单独跑真实周匹配，只检查有草稿、发布后硬约束为空、再发布一次数量和 version 不变。

上面的数字来自架构侧读到的完整日志。未登录时日志是 403、artifact 是 401，所以早先写过的「版本未读到、三个类结果未知」以这次日志为准。

本地那次三个 IT 仍然是 NOT_RUN。真实用户效果仍未评估。

## 建议

S01 保持 NO-GO。算法质量保持 NO-GO。不启动 V4-P01。拆开的场景要在新的 PostgreSQL 运行里全部执行并通过，才能再谈 GO。不删断言，不跳过测试。
