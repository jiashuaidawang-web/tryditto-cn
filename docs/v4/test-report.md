# V4-AUDIT-001 测试报告

执行时间：2026-11-25 15:19–15:22（UTC+8）。
代码：`c74921b527166bdef9e3577cf55338170377cdde`，分支 `master_agent_v4`。
JDK：编译目标 17。Maven 使用的运行时是 21。领域测试在沙箱内通过；需要本机监听端口的三个测试在沙箱外重跑通过。

没有读取生产库，没有打印密钥。`docs/eval/见一面-Go-No-Go-报告.md` 被 `SimulationRunTest` 重写后，已按审计前 SHA-256 `d7226039…` 还原。`阶段B-深水区复核.md` 未被改写。

合成结果不是真实用户有效性。

## 已通过

### aiditto-domain 全部测试

命令：`mvn -pl aiditto-domain test`

结果：437 项，失败 0，错误 0，约 18 秒。

与本审计直接相关的包括：

| 类 | 项数 | 结果 |
| --- | --- | --- |
| MatchingSolverTest | 12 | 通过 |
| ScorerTest | 10 | 通过 |
| HardFilterTest | 26 | 通过 |
| AiScoreMatchingTest | 6 | 通过 |
| MatchingSeededTest | 80 | 通过 |
| MatchingPropertiesTest | 6（含 300 次求解不变量） | 通过 |
| PoolGateTest | 5 | 通过 |
| SyntheticFixtureTest | 10 | 通过 |
| TasteFactsTest | 6 | 通过 |
| TasteWeightsTest | 6 | 通过 |
| FeedbackSignalTest | 7 | 通过 |
| RrfFusionTest | 4 | 通过 |
| RecallMatrixTest | 5 | 通过 |
| CohortFilterTest | 3 | 通过 |

`SyntheticFixtureTest` 打印：200 人合成夹具，83 对，34 人未匹配，822 条边，求解 BLOSSOM，事后不变量违规 0，墙钟 62 ms（建边 49 ms，求解 13 ms）。这是空 AI 分的规则求解。

### aiditto-app 离线单测与仿真

命令见 `baseline.json` 的 `reproduce.appUnitAndSim`。

第一次在沙箱里跑时，`VisionEmbeddingServiceTest`、`OpenAiCompatEmbeddingServiceTest`、`OpenAiCompatModelTest` 因不能 `bind` 本机端口失败（20 项：14 错误 + 6 失败）。同一命令在允许绑定本地端口后重跑这 21 项，全部通过。失败原因是沙箱，不是断言。

重跑后这批评选的测试合计 95 项，通过 95。

| 类 | 项数 | 结果 |
| --- | --- | --- |
| AiJudgeTest | 10 | 通过 |
| TasteWriterTest | 6 | 通过 |
| VoiceAssetServiceTest | 4 | 通过 |
| ScenarioPromptsTest | 6 | 通过 |
| FeedbackPriorTest | 5 | 通过 |
| GenderNormsTest | 3 | 通过 |
| AiCopyGeneratorTest | 14 | 通过 |
| AiCopyServiceTest | 9 | 通过 |
| LlmGatewayTest | 12 | 通过 |
| ResonanceReasonTest | 4 | 通过 |
| VisionEmbeddingServiceTest | 5 | 通过（沙箱外） |
| OpenAiCompatEmbeddingServiceTest | 6 | 通过（沙箱外） |
| OpenAiCompatModelTest | 10 | 通过（沙箱外） |
| SimulationRunTest | 1 | JUnit 通过，闸门 NO-GO |

`SimulationRunTest` 的 JUnit 通过只表示回放跑完、报告写得出。测试源码写明阈值不作为断言。本轮 stdout 的闸门结果：

- 通过：12 周期跑完、末周期 regret 0.1137 ≤ 0.15、冷启动 top1 0.5150 ≥ 0.35、taste_recovery 0.9077 ≥ 0.70、口是心非收敛 1.0、恶意差评稳定性 ≤ 0.15、重复推送 0、独占路占比约 0.44 ≤ 0.60、长尾公平约 1.0、P0 用例 8/8。
- 失败：regret 单调下降 5 < 8；斜率 +0.0020 不小于 0；周期 12 top1 0.4588 < 0.60。
- 总判：NO-GO。

其中「召回 Recall@50 ≥ 0.90，actual=0.9900」和「LLM 全挂 / Qdrant 不可用」两条被测试写成常量通过，注释说它们来自更早的 Step 5/6/7 记录，不是本轮重测。本报告不把它们算成 V4-AUDIT-001 的实测。

固定池末周期（同一测试里的另两次 `SimDriver.run`）：学习 top1 0.6200、regret 0.0649、ρ 0.7837；不学习 top1 0.5600、regret 0.0843、ρ 0.7821。

学习组这一次回放墙钟 16672 ms。整个测试方法 62.59 s，因为还跑了不学习、固定池、固定池不学习三组。模型调用 0 次。费用没有测量点。

仿真覆盖率 `cov=1.0` 是「有至少一条过硬门槛的边的用户比例」，不是 Qdrant 召回覆盖率。配对率在封闭池接近饱和（周期 1 为 100 对 / 200 人），所以配对率不能用来判断质量。

双向契合：测试没有这个指标。记为缺失。

## 未运行（NOT_RUN）

### 需要 PostgreSQL 的集成测试

本机 `127.0.0.1:5432` 连接失败。这些测试还会 `flyway.clean()` 测试库，在库不可用时没有执行。

| 类 | 未运行原因 |
| --- | --- |
| AuthIT、AccountIT、OnboardingIT、PhotoIT、MatchIT、TasteIT | 继承 `ApiIT`，需要 `jdbc:postgresql://127.0.0.1:5432/aiditto_test` |
| AgentRerankIT | 同上，且需要 `AIDITTO_STEP6_IT=1` |
| AiCopyIT | 同上，且需要 `AIDITTO_STEP7_IT=1` |
| FeedbackLearningIT | 同上，且需要 `AIDITTO_STEP8_IT=1` |
| MetricsIT | 同上，且需要 `AIDITTO_METRICS_IT=1` |
| VoiceSessionIT | 同上，且需要 `AIDITTO_VOICE_IT=1` |
| VectorSyncIT | 同上，且需要 `AIDITTO_QDRANT_IT=1` |

环境变量一个都没有设置。

### 需要 Qdrant 或真实模型、且会写外部系统的测试

`127.0.0.1:16333` 可以连通。项目注释写这是 SSH 隧道。本轮没有设置开启开关，也没有向该端口写入，避免改到隧道另一端的向量库。

| 类 | 未运行原因 |
| --- | --- |
| QdrantClientIT、RecallEvalIT | `AIDITTO_QDRANT_IT` 未设置；且可能写入隧道后的 Qdrant |
| RecallExperimentIT | `AIDITTO_RECALL_MATRIX` 未设置 |
| AgentRerankLiveE2ETest、AiCopyLiveE2ETest、TasteExtractE2ETest、LiveEmbeddingE2ETest、DeepSeekFallbackE2ETest | `AIDITTO_LLM_E2E` 或 `AIDITTO_STEP7_LIVE` 未设置；不会在审计里使用真实密钥 |

因此本轮没有模型成功率、降级率、人民币成本的实测。代码里的 `llm_cost_rmb` 写入值是常数 0，不能拿来填这个空。

### 重实验

`OpenPoolExperimentTest` 需要 `AIDITTO_OPENPOOL_EXP=1`，默认 24 个种子。未设置，未跑。生产口径 `learnedScoreTopN=15` 的仿真也没有在本轮单独跑。`baseline.json` 里的学习净收益是「全边注入学习分」的固定池，高于生产 Top-15 口径。

### 前端

`web/` 的测试不覆盖这条 Java 匹配链，本轮没有跑。

## 本轮测试没有覆盖到的生产缺口

通过的测试没有否定下面几件事，因为它们测的是组件，不是接线：

- `RecallPipeline` 不在 `WeekRunner` 里。没有任何通过的测试断言它被周任务调用。
- `FeedbackLearningService` 不在调度器里。`SimulationRunTest` 通过也不表示生产在消费反馈。
- `AiScoreMatchingTest` 通过，证明的是 AI 分会压过规则分，这正是尺度不统一。
- 发布步骤没有对应的「求解后拉黑/注销仍能发布」回归测试。`MatchIT` 本轮 NOT_RUN。
