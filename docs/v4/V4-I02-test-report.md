# V4-I02 测试报告

本机没有 PostgreSQL，也没有 Docker。领域测试在本地执行。PostgreSQL 集成、真实 Qdrant 和用户端浏览器由 CI 执行。下面的通过记录对应已经结束的作业，不提前写成通过。

## 本地已执行

`mvn -B -pl aiditto-domain test`

620 项，失败 0，异常 0，跳过 0。这比冻结的 617 项多出 `ShadowResourceBudgetTest` 的 3 项。

`mvn -B -pl aiditto-domain test -Dtest=ShadowResourceBudgetTest`

3 项，失败 0，异常 0，跳过 0。

- 默认预算拒绝 201、500、5,000 和 10,000 人。
- 合格边不能超过 50,000。
- 截止时间已过时，求解结果被丢弃，配对为空。
- 数学夹具不能成为可执行业务计划。
- 22 个映射特征与 4 个对齐目标的重叠是 0。样例投影的可评分对齐是 0。

`mvn -B -pl aiditto-app -am test -Dtest=ShadowRunnerClosedTest`

1 项通过。`LEGACY_ONLY` 在读取用户池之前拒绝影子运行。

## CI

尖端 `8e427a5a0dcf517b1d35731156791868158dafa3` 的五作业是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46154283275 。domain-tests、integration-tests、qdrant-isolation、match-lab-e2e、test-reports 都是 success。PostgreSQL 作业执行了 `ProductMatchingPoolIT`、`ShadowMatchingIT`、`ShadowSnapshotIT` 和 `ShadowIsolationIT`。本机没有数据库，这些集成结果以该作业为准。

第一次集成失败是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46151963322 。单向意向用例和同一类里更早的用户混在一个城市里，滞后画像又被隔离同步写成了当前画像。随后的修正把单向用例放到已种子的上海地点，并在问卷更新之后再把业务版本向前推一版。

## CI 清单

集成作业新增：

- `ProductMatchingPoolIT`
- `ShadowMatchingIT`
- `ShadowSnapshotIT`
- `ShadowIsolationIT`
- `ShadowRunnerClosedTest`

原有领域测试、PostgreSQL 集成、Qdrant 和 Match Lab Playwright 仍在清单里。影子测试使用真实 `V4GlobalMatchingEngine` 和 `MatchingSolver`，没有用假结果替换求解器。

浏览器仍是 I01 的 `web/e2e/product-onboarding.spec.ts`。入驻会填写文本、身高和可选的说话方式页，再从那一页的「回首页」回到首页。这次 `match-lab-e2e` 是 success。
