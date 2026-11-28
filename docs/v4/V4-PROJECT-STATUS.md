# V4 项目状态

易变事实写在这里。长期约束写在根目录 `AGENTS.md`。

## 定位

TryDitto 国内版是 AI Relationship Intelligence Engine 的离线研发线。当前结束集中式算法模块的离线工程，下一步是内部研发控制中心 Match Lab。Match Lab 不是运营后台，也不是静态演示页。

## 架构原则

画像、双向匹配、反馈和评估与具体模型分开。模型可以替换。生产评分、生产发布和真实用户数据不由实验页面写入。

## 模块状态

| 模块 | 状态 |
| --- | --- |
| S01 发布安全 | 工程验收通过。算法质量仍是 NO-GO。 |
| P01 / P02 画像 | 离线提取和证据模型可用。真实模型理解未过门槛。 |
| M01 / M01-RANK 召回 | 合成对照可用。召回没有进入 `WeekRunner`。 |
| M02 双向评分 | 冻结合成对照按规则通过。 |
| M03 关系推理 | 确定性解释可回放。真实模型排序增益 NO-GO。C1、C2 关闭。 |
| M04 全局匹配 | 小规模求解通过。端到端合成匹配质量和大规模性能 NO-GO。 |
| F01 / F02 反馈与学习 | 离线工程通过。真实反馈学习未启用。 |
| F03 群体评估 | 离线工程通过。F03-FINAL 收口观察来源、标签口径和校准。真实校准 NOT_RUN。 |
| Match Lab L01 | 已快进到 `master_agent_v4` 的 `f7bdcdc44c43d8081336788e62c169e625091cce`。浏览器作业 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46127772617 ，五个作业 success。第一次失败记录是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46126626515 。 |
| Match Lab L02 | 已快进到 `master_agent_v4` 的 `3f822f86820f7e90710ba710a899938a1e4b94ea`。五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46132647004 。浏览器证据 Artifact `15258557731`。 |
| Match Lab L03 | `feature/v4-match-lab-evaluation` 不自动合并。浏览器通过的提交是 `8173410f6efa18b3ea8fef8b87658058feffc3d1`，五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46134624886 。浏览器 Artifact `15258449746`。 |

## 冻结策略

已冻结且本轮不改写的策略是候选策略 E、双向评分 B5、全局目标 G2。冻结案例包括 P01/P02 画像、M01 召回、M02 评分、M03 推理、M04 全局匹配、F01 反馈、F02 学习和 F03 的 `f03-synthetic-1`。

## 集成基准

- `master_agent_v4`：`c101a04bbc5b75c5ca255297e92a7271ebdc8dce`。I01-FINAL 从 `767d65fbb3d67d17f6c3c7608e01b00fc918b115` 快进。五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46150970883 ，domain-tests、integration-tests、qdrant-isolation、match-lab-e2e、test-reports 都是 success。合并前远程仍是 `767d65f`，没有并行提交。
- 此前的离线引擎基准是 `767d65fbb3d67d17f6c3c7608e01b00fc918b115`。L03-FINAL 从 `3f822f86820f7e90710ba710a899938a1e4b94ea` 快进。五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46137692591 。没有合并 `master_agent_v3`。
- L01-FINAL 浏览器 CI 仍是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46127772617 。
- F02-FINAL 实现：`c74e9c1b5db1aa2c5c985da4a0984fd5521e0325`
- F03 实现：`094e161c20198aef5a23a8dce76cf0b58e77d858`
- F03 文档尖端：`92cdd768bd4aec413c6adfab0a94bd4e162ab968`
- F03 尖端 CI：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46118462694
- F03-FINAL 实现：`b82429b34bc145a26dd54ddde27c3d4fc5ca5be1`
- F03-FINAL 文档：`5a23e862aa85bb7f65355b565f5032eb3d094d48`
- F03-FINAL 分支 CI：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46120946449
- `master_agent_v4` 已快进到 `5a23e862aa85bb7f65355b565f5032eb3d094d48`。集成 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46121262740 ，四个作业 success。

F03 尖端上已经核实：593 项领域测试，93 项 PostgreSQL 集成测试，13 项应用层单元测试，1 项真实 Qdrant 集成测试，`ProfileLearningIT` 19 项，`StrategyEvaluationIT` 7 项，失败、异常、跳过都是 0。F03-FINAL 本地领域测试是 599 项。它的集成作业成功，清单里的 14 个 `StrategyEvaluationIT` 方法已执行。远程 Surefire 总数没有管理员权限，不另报一份。

原有 200 人生产算法仿真是 83 对、34 人未匹配、822 条候选边、0 违规。那是旧仿真，不是 V4 质量证明。

## 下一阶段

I01 已快进到 `master_agent_v4` 的 `c101a04bbc5b75c5ca255297e92a7271ebdc8dce`。实现从 `9229f14ba58b4416438c11ec0898221018c673da` 到锁顺序提交 `8f3ee57e34ff0018f17e96a80d0fdcd39e87ad7e`。`46145461882` 里群体观察误拒绝了不存在的合成用户，入驻脚本也按 button 去点 radio。随后的修复是 `c48cb54a548d07936946ff728ed11f905a341dbc`、`fa04a1f302b2afa6c8e20b9c30181b9fe28a713b`、`d93795526ca741173156298bfda2b1e03b6419b4` 和 `c101a04bbc5b75c5ca255297e92a7271ebdc8dce`。绿的五作业是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46150970883 。用户端浏览器作业这次 success。外部擦除执行器仍是 NOT_RUN。

I02 实现提交是 `a894bc8787989648ce361fc46ccacfbb1e76619c`，分支 `feature/v4-shadow-matching`，从上面的 `master_agent_v4` 拉出，不自动合并。它只在 `V4_SHADOW_TEST` 连接隔离合成用户和冻结的 E、B5、G2。本地领域测试 620 项，失败 0。其中新增的资源预算测试 3 项。22 个已映射特征与 4 个 M02 对齐目标的重叠是 0，特征覆盖质量 NO-GO。PostgreSQL 影子集成随这次推送进入 CI，结果以该作业为准。Blossom 仍不能取消线程。生产启用保持 NO-GO。

## 未解决的生产风险

真实校准、真实策略提升和真实关系质量都没有验证。生产 V4 匹配保持关闭。默认 `LEGACY_ONLY` 的注销仍不调用 `eraseActor`。`V4_INTEGRATION_TEST` 会调用，但该模式不是生产配置。重复次数 3 次和 30 天窗口没有写进评分权重。召回没有进入周匹配。生产启用 NO-GO。最终匹配算法质量 NO-GO。真实用户画像质量 NOT_RUN。

## 接手

1. 读本文件和 `AGENTS.md`。
2. 在 `api/` 下执行 `mvn -B -pl aiditto-domain test`。
3. 应用层使用 `mvn -B -pl aiditto-app -am test`。不要单独安装旧领域包后再测应用层。
4. PostgreSQL 集成测试只在 CI 的 Postgres 16 上跑。本机没有数据库时，把集成结果标成 NOT_RUN。
5. 不覆盖其他分支上的未提交改动。合并前先看远程是否有并行提交。
