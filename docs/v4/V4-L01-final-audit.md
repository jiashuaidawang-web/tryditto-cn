# V4-L01-FINAL 审计

审计对象是 `feature/v4-match-lab` 上的 Match Lab。基准仍是 `master_agent_v4` 的 `5a23e862aa85bb7f65355b565f5032eb3d094d48`。本轮不改 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom`，不改 Flyway，不改冻结案例标签。

## R01 工作区

`WorkspaceBoard` 按管理员编号保存一份 `LabWorkbench`。字段是工作区编号、所有者、模式、创建时间、最近访问、修订、画像、运行记录和过期时间。别人的编号返回 `WORKSPACE`。`POST /api/match-lab/workspace/reset` 只重建调用者自己的工作区。画像更新必须带 `expectedVersion`，版本不符时拒绝，已成功的版本留下。上限保持 24 份画像、48 次运行、64KiB 已知请求体、32 份工作区、空闲 2 小时。存储常量是 `MEMORY_ONLY_LOST_ON_RESTART`。服务重启后这些数据丢失。

## R02 环境

`aiditto.match-lab.enabled` 默认 false。`admits()` 还要求环境是 `local`、`ci` 或 `isolated`，并且不是生产。`prod` 或 `production` profile 由 `MatchLabProductionLock` 强制关闭。管理员仍是必要条件。研究接口不读生产画像、匹配、反馈或学习表，不发布，不通知，不调用真实模型，也不接受任意 SQL、路径或 URL。演示验证码只在演示模式的隔离环境里使用。

## R03 摘要

`datasetIdentityHash` 是实验编号加数据集版本的 SHA-256 前 16 位。`datasetContentHash` 是冻结 JSON 的实际字节。没有文件时写 `CONTENT_HASH_UNAVAILABLE`。`commitSha` 是该文件自己的来源提交。`catalogIntegrationCommit` 才是 `5a23e862aa85bb7f65355b565f5032eb3d094d48`。实时运行保存完整输入快照，并用同样的 SHA-256 记录 `inputHash` 和 `resultHash`。相同固定输入的确定性评分得到相同结果摘要。

## R04 候选

开发集和保留集的四个方案保留。新增 `SYNTHETIC_SANDBOX`，从当前工作区画像调用已有资格校验和 M02。响应给出初始池、合法池、可推荐数、双向分数、覆盖、拒绝原因、画像版本和代次。只有完整合法池 E。工作区没有召回路由，所以不跑 RRF，也不写 `NO_ROUTE_SIGNAL`。冻结目录行仍是 `FROZEN_RESULT`。这次调用是 `ENGINE_EXECUTED`。

## R05 浏览器

`v4-ci.yml` 增加作业 `match-lab-e2e`。它使用该次运行的 Postgres 16 和一次性口令，由应用自己跑 Flyway，用本次 reactor 打出的 `aiditto-api.jar` 启动后端，再用 Vite preview 和 Playwright Chromium 操作页面。截图、HTML 报告和日志上传为 artifact `match-lab-e2e`。`test-reports` 依赖这个作业。功能关闭不在那个已经打开的进程里重复演示，而由同一工作流的 `MatchLabFilterTest` 证明。

## 本地证据

- 领域测试 605 项，失败 0，异常 0，跳过 0。
- `MatchLabFilterTest` 5 项通过。
- `cd web && npm run build` 通过。
- `MatchLabIT` 和浏览器会话本机 NOT_RUN。本机没有 PostgreSQL，也没有 Docker。这两项改由 CI 执行，不能在作业变绿之前写成通过。

## 本文件写成时的判定

- L01-FINAL 工程质量：本地领域测试和过滤器测试通过。集成与浏览器仍等 CI。
- L01 浏览器验收：NOT_RUN。
- 沙箱环境隔离：过滤器与工作区单元测试通过。跨工作区的 HTTP 断言在 `MatchLabIT`，等 CI。
- 真实关系质量：NOT_RUN。
- 生产启用：NO-GO。
- 最终匹配算法质量：NO-GO。
