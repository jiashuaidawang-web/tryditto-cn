# V4-I02 测试报告

本机没有 PostgreSQL，也没有 Docker。领域测试在本地执行。PostgreSQL 集成、真实 Qdrant 和用户端浏览器由 CI 执行，结果以 CI 为准，不在这里预写通过。

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

## CI 将执行

集成作业新增：

- `ProductMatchingPoolIT`
- `ShadowMatchingIT`
- `ShadowSnapshotIT`
- `ShadowIsolationIT`
- `ShadowRunnerClosedTest`

原有领域测试、PostgreSQL 集成、Qdrant 和 Match Lab Playwright 仍在清单里。影子测试使用真实 `V4GlobalMatchingEngine` 和 `MatchingSolver`，没有用假结果替换求解器。

浏览器仍是 I01 的 `web/e2e/product-onboarding.spec.ts`。选项控件是 radio 和 checkbox，定位按钮按可见的 aria-label 点击。
