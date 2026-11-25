# V4-INFRA-001：GitHub Actions 持续集成

本地 Intel Mac 不再安装 PostgreSQL。集成测试只在 GitHub-hosted Ubuntu Runner 的一次性 PostgreSQL 16 上跑。

不启动 V4-P01，不改匹配算法，不部署，不合并 `master_agent_v3`。

## 工作流

文件：`.github/workflows/v4-ci.yml`。

触发：`master_agent_v4` 的 push、目标是该分支的 pull request、以及手动 `workflow_dispatch`。

权限：默认 `contents: read`。上传测试报告的 job 另加 `actions: write`。工作流不能推代码，也不使用生产服务器密钥或 LLM API Key。

三个 job：

1. `domain-tests`：`mvn -B -pl aiditto-domain test`。Java 17，Temurin。
2. `integration-tests`：PostgreSQL 16 service，库名 `aiditto_s01_test`，账号 `aiditto_ci`。密码是本次 `github.run_id` 和 `github.run_attempt` 拼出来的，只活在这个 Runner 里，不写进源码字面量。步骤里会 mask，脚本不打印它。`pg_isready` 通过后，再用 `psql` 确认 Runner 能连上，并把 `SELECT version()` 写入 `ci-meta/postgres-version.txt`。
3. `test-reports`：无论前两个 job 成功还是失败，都收集 Surefire、Failsafe 和上面的环境文件。

集成测试命令：

```
mvn -B -pl aiditto-app -am verify \
  -Dtest=TestDatabaseGuardTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=PublishSafetyIT,ConsentLegacyIT,MatchIT \
  -Dfailsafe.failIfNoSpecifiedTests=true \
  -DforkCount=1 \
  -DreuseForks=false
```

`-Dsurefire.failIfNoSpecifiedTests=false` 只是因为这条命令会同时经过 `aiditto-domain`，而领域模块里没有 `TestDatabaseGuardTest`。领域测试由前一个 job 全量执行。验收步骤仍要求 app 的 Surefire XML 里这份防误操作测试 `tests > 0`，缺了就失败。

`failIfNoSpecifiedTests=true`：三个类有任何一个没被发现，构建失败。后面还有一步读 Failsafe XML，`tests <= 0`、有 skipped、有 failure 或 error 都算验收阻断。不能靠删断言或跳过测试变绿。

三个类顺序执行，每个类一个 JVM。`ApiIT` 在类结束后关闭 Spring 上下文。下一类启动时才会 `flyway.clean()`，避免两个类同时清同一库。

Surefire 不跑 `*IT`。`mvn test` 不会走到 clean。

## 测试库保护

`TEST_DB_URL` 和 `TEST_DB_USER` 必须显式给出。没有默认地址，也不会再回到 `127.0.0.1:5432/aiditto_test` 或 `aiditto`。

`TestDatabaseGuard` 只允许：

- 协议 `jdbc:postgresql`
- 主机 `127.0.0.1`、`localhost`、`::1`、`postgres`
- 库名正好是 `aiditto_s01_test`
- URL 里不能带用户名或密码

`FlywayMigrationStrategy` 先读当前 DataSource 的真实 URL，再决定能不能 clean。名字不对就抛错，不执行 clean。`TestDatabaseGuardTest` 锁住旧默认库、`aiditto_pg` 和带密码的 URL。

生产 compose 里的库名是 `aiditto_pg`，主机也不是这份允许列表里的业务地址。本轮没有改 `deploy/` 或 `application-prod.yml`。

## 前两次运行

两次都跑完了，失败相同。PostgreSQL `16.16 (Debian 16.16-1.pgdg13+1)`。

| 运行 | 结果 |
| --- | --- |
| https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45586978463 | failure |
| https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45588478771 | failure |

`domain-tests` 成功。`TestDatabaseGuardTest` 3 通过。`PublishSafetyIT` 2 项里 1 通过、1 失败。`ConsentLegacyIT` 1 通过。`MatchIT` 4 通过。集成合计 7 项，6 通过，1 失败，0 errors，0 skipped。

失败在 `PublishSafetyIT.grantedDraftsPublishAndUnsafeRacesDoNot` 第 122 行：北京这周只有 3 对草稿，断言要求至少 8。并发断言没有执行。这不能说明并发通过，也不能说明并发有缺陷。

原因在现有硬约束，不在数据库。`HardFilter.cohortsCompatible` 规定 synthetic 只能和 demo 配对，synthetic 不能互配。夹具 200 人里 demo 只有 4 个：`13800000001`、`13800000002`、`13800000003` 的地点在北京，`13800000004` 在上海。北京因此最多 3 对。领域测试的 83 对是因为 `FixtureCandidates` 没有写入 cohort，构造器默认 REAL，200 人可以互配。应用路径会写入真实 cohort。本轮不改这条规则，也不为了凑数量改求解器。

发布安全场景改为 `PublishSafetyFixtures` 自建 demo cohort 成年用户、完整 profile、显式 GRANTED、独立 week 和 draft。正常发布仍调用 `PublishGuard`。`WeekRunner` 的端到端测试只要求真实求解有草稿并重复发布不变，不再要求固定 8 对。

Job Summary 写入每个类的执行数、通过失败错误跳过、失败方法、断言摘要、源码行、commit 和 run URL。跳过或缺少方法时验收失败。

## 夹具改造后的运行

提交 `c6072290200411241302e5274281f71745f9beb6`：

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45591470398

总结论 success。总时长 2 分 18 秒。`domain-tests` 45 秒，success。`integration-tests` 2 分 6 秒，success。验收步骤「Reject a run that did not execute the IT scenarios」success。这一步会在缺少方法、有跳过、有失败或有错误时退出 1，所以下面这些方法都执行了，而且没有失败、错误或跳过：

- `TestDatabaseGuardTest`：类里 3 个测试，门槛要求 `tests > 0` 且无失败、无跳过。
- `PublishSafetyIT`：14 个独立方法，含 6 个并发或锁顺序场景。
- `ConsentLegacyIT`：1 个方法。
- `MatchIT`：4 个方法。

`SELECT version()` 仍由成功的记录步骤写入 artifact。未登录不能下载，所以这次没有重读版本字符串。同一 `postgres:16` 镜像在前两次运行里是 `PostgreSQL 16.16 (Debian 16.16-1.pgdg13+1)`。

`WeekRunner` 方法通过，只说明真实求解产出了草稿，并且重复发布没有改变数量和 version。日志里的具体草稿数要登录才能看。前两次、在没有这些夹具用户时，北京是 3 对。

## 不在本轮

Qdrant、真实模型、生产凭证。召回和算法质量仍是 NO-GO。
