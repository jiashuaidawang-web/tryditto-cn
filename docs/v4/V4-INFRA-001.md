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

## 第一次运行

提交 `725b85df18f6d9e5c12b492c5861e568839a2d94` 推送后触发：

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45586978463

总结论 failure。`domain-tests` success。`integration-tests` failure：容器和 `psql` 记录成功，随后 Maven verify 与 XML 验收都是退出码 1。`test-reports` success，报告已留下。

未登录不能下日志（403）和 artifact（401）。页面写着要登录才能看日志。PostgreSQL 的 `SELECT version()` 文本、三个 IT 的执行条数和并发通过数因此仍空着。S01 保持 NO-GO。

## 不在本轮

Qdrant、真实模型、生产凭证。召回和算法质量仍是 NO-GO。
