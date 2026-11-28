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
| Match Lab L01 | `1534812797aee4e8cb8490ac625b5ae51b1707a2`。CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46122030761 ，四个作业 success。浏览器端到端 NOT_RUN。不合并。 |

## 冻结策略

已冻结且本轮不改写的策略是候选策略 E、双向评分 B5、全局目标 G2。冻结案例包括 P01/P02 画像、M01 召回、M02 评分、M03 推理、M04 全局匹配、F01 反馈、F02 学习和 F03 的 `f03-synthetic-1`。

## 集成基准

- `master_agent_v4`：`5a23e862aa85bb7f65355b565f5032eb3d094d48`
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

## 未解决的生产风险

真实校准、真实策略提升和真实关系质量都没有验证。生产 V4 匹配保持关闭。`AccountService.requestDelete` 仍不调用 `eraseActor`。重复次数 3 次和 30 天窗口没有写进评分权重。召回没有进入周匹配。生产启用 NO-GO。最终匹配算法质量 NO-GO。

## 下一阶段

F03-FINAL 已快进到 `master_agent_v4`。`feature/v4-match-lab` 从该尖端继续。L01 只做实验总览、用户画像、候选发现、双向评分和 AI 关系推理回放。不实现 L02、L03 和 M05。L01 不自动合并。

## 接手

1. 读本文件和 `AGENTS.md`。
2. 在 `api/` 下执行 `mvn -B -pl aiditto-domain test`。
3. 应用层使用 `mvn -B -pl aiditto-app -am test`。不要单独安装旧领域包后再测应用层。
4. PostgreSQL 集成测试只在 CI 的 Postgres 16 上跑。本机没有数据库时，把集成结果标成 NOT_RUN。
5. 不覆盖其他分支上的未提交改动。合并前先看远程是否有并行提交。
