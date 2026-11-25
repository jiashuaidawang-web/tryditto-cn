# V4-AUDIT-001 源码审计

审计对象：AI 关系智能引擎相关实现（画像、召回、周匹配、精排、反馈学习）。
本轮只读。没有改业务算法，没有连生产库，没有部署。

## 基线

| 项 | 值 |
| --- | --- |
| 工作区 | `/Users/null/IdeaProjects/AI/tryditto-cn` |
| 审计时分支 | `master_agent_v4` |
| HEAD | `c74921b527166bdef9e3577cf55338170377cdde` |
| 与指定基线 | 相同。`master_agent_v3` 也停在这个 commit |
| 审计前工作区 | 干净，无未提交修改 |
| 已有 `docs/v4` | 不存在，本轮新建 |
| 已有 `docs/eval` | 仿真测试会重写 `见一面-Go-No-Go-报告.md`。跑完后已按审计前字节还原（SHA-256 `d7226039e6f048ed02f55aae97f0b4f4eccd813bbbac522bae88ef6c66c8bb10`） |

下面的结论都来自这个 commit 的源码和本轮实跑。合成用户指标不能当成真实用户有效性。

## 生产周匹配实际在跑什么

定时任务只有 `MatchScheduler.tick()`：`WeekRunner.tick()` 加约会截止处理。

```23:26:api/aiditto-app/src/main/java/cn/aiditto/match/MatchScheduler.java
    @Scheduled(fixedDelayString = "${aiditto.scheduler.delay-ms:30000}", initialDelayString = "${aiditto.scheduler.delay-ms:30000}")
    public void tick() {
        weeks.tick(clock.instant());
        matches.tickDeadlines(clock.instant());
    }
```

`WeekRunner.solve()` 的候选来自 `People.city()`（一座城市里已入驻、状态为 active/paused/suspended 的全量用户），然后 `MatchingSolver.buildEdges()` 做全对硬过滤和规则打分。`RecallPipeline`、`VectorSyncService.syncAll()`、`FeedbackLearningService.scanAndRewrite()` 在 `src/main` 里没有任何调用方。全仓库 Java 里，这三个方法只出现在各自的类定义和 `src/test` 的 IT 里。

因此：向量召回、向量同步、反馈学习都是可运行的服务，但没有接进周三匹配主链路，也没有自己的定时任务。

默认开关（`application.yml`）：

- `aiditto.match.agent.enabled` = `MATCH_AGENT_ENABLED`，默认 `false`
- `aiditto.match.agent.top-pairs` 默认 15，`min-total` 默认 72
- `aiditto.learning.enabled` 默认 `true`，但没有调度器调用它
- `aiditto.recall.enabled` 默认 `true`，同样没有调度器调用 `RecallPipeline`
- `aiditto.llm.mode` 默认 `mock`
- `aiditto.recall.embedding-mode` 默认 `hash`

画像提取是接上的：入驻/改答案发 `ProfileWrittenEvent`，`TasteListener` 在事务提交后调用 `TasteExtractor.ensure()`。

## 十二个问题

### 1. 用户画像有没有真正区分自身特征和伴侣偏好？

结论：**文本上分成了两段，可计算表示没有分开。**

`user_taste` 有两个文本列：`persona_text`（这个人是什么气质）、`taste_text`（这个人真正想要什么）。见 `V6__ai_taste_recall.sql` 第 9–10 行。`TasteWriter` 的提示词要求模型分别写这两段（`TasteWriter.java` 第 31–35 行）。模板兜底也按字段拆开：兴趣、相处风格进 persona；期待关系、预算、期望年龄进 taste（第 78–90 行）。

发给模型的事实却是一张扁平白名单，自身特征（`hobbies`、`occupation`、`firstMessage`）和择偶条件（`relationshipLooking`、`expectedAgeRange`、`educationImportance`）混在同一个 `TasteFacts.values()` 里（`TasteFacts.java` 第 34–42 行）。

可计算的 `user_taste.weights` 只有一份 10 维向量（`V10__feedback_learning.sql` 第 8–10 行）。`TasteWeights` 的类注释把它定义为排序权重/偏好估计，没有「自身特质向量」。隐藏真值 `HiddenTruth` 倒是把 `weights` 和 `attributes` 分开了（`HiddenTruth.java` 第 11–17 行），但这只存在于仿真包，不进生产表。

### 2. RecallPipeline 是否进入生产周匹配主链路？

结论：**否。**

`RecallPipeline.recall()` 定义在 `RecallPipeline.java` 第 82 行。`src/main` 中没有调用。生产调用链是：

`MatchScheduler.tick` → `WeekRunner.tick` → `drive` → `solve` → `People.city` → `MatchingSolver.buildEdges` → 可选 `AgentRerankService.rerank` → `MatchingSolver.solve`。

见 `WeekRunner.java` 第 208–239 行。这里不读 `recall_candidate`，不调用 Qdrant。

`recall()` 的唯一 Java 调用在 `VectorSyncIT`（测试，且要 `AIDITTO_QDRANT_IT=1`）。`QdrantRecallService` 只被 `RecallPipeline` 和 `VectorSyncService` 使用，这两条都没有生产入口。

`aiditto.recall.enabled=true` 只是配置绑定，不构成调用。

### 3. AI 双向匹配是否分别计算了 A→B 和 B→A？

结论：**否。生产只给一条无向边一个分。**

规则分里，年龄和距离是两个方向的平均（`Scorer.java` 第 81–86 行、第 95–99 行）。硬过滤对性别、年龄、学历、AI 约束做了双向检查（`HardFilter.java` 第 22–42 行）。

AI 与学习分不是这样：

- `AgentRerankService.rerank` 对每条边只算 `wa.rankAgainst(wb)`，`wa` 是 `edge.low()`，`wb` 是 `edge.high()`（第 108–112 行）。不计算 `wb.rankAgainst(wa)`，也不取平均。`TasteWeightsTest` 第 78–84 行证明这个函数是有向的，两边不相等。
- `AiJudge` 一次调用产出一个 `total`（第 165–177 行）。提示词同时看到 A 和 B，但没有 A→B、B→A 两个分。
- 留痕只写 `user_id=low, cand_id=high`（`AgentRerankService.java` 第 261 行），没有反向行。

仿真里反馈更新是双向的（`SimDriver.learnFromFeedback` 对 a、b 各调一次，第 343–347 行），打分仍然只用 `learned.get(a).rankAgainst(learned.get(b))`（第 294、304 行），`a` 是较小 id。

### 4. 真实择偶偏好是否完整传入 AI 精排？

结论：**否。**

`loadPersonas()` 在 persona 非空时丢掉 taste：

```183:185:api/aiditto-app/src/main/java/cn/aiditto/match/AgentRerankService.java
                        String base = persona.isBlank() ? taste : persona;
                        String voice = voiceAssets == null ? "" : voiceAssets.voiceHint(id);
                        String withVoice = voice.isBlank() ? base : base + "。【语气参考】" + voice;
```

`taste_text` 正是「想遇到什么人」的那一列（`V6` 第 10 行）。只要画像提取写出了 persona，裁判和分身都看不到 taste。

结构化择偶条件（年龄区间、身高、学历、预算、爱好、期望性别）留在 `Candidate` 上，只参与 `HardFilter` 和 `Scorer`，不进入 `AiJudge.judgeUser()`。裁判拿到的是画像文本、红线标签 id、可选模拟对话、以及写死的 `isoReachable=true`、`slotsOverlap=true`（`AgentRerankService.java` 第 137–138 行）。红线原文按设计不进提示词，只送 `ConstraintTags` 的 id（第 140–143、227–236 行）。

### 5. 有 AI 分和没有 AI 分的候选是否在同一尺度？

结论：**否。**

边权（`MatchingSolver.java` 第 31–32、154–157 行）：

`ai * 1_000_000 + learned * 100_000 + objective * 1000 + tiebreak(0..999)`

- `ai == null` 时 AI 项为 0。一旦某条边写入 AI 分，1 分就等于规则目标分差 1000 分。展示分 `ScoreBreakdown.score()` 上限 100，公平加分每人最多 9（`Weights.java` 第 47–48、85–86 行），所以规则目标最大约 118，乘 1000 后约 118_000。
- 学习分即使是 1，也已经压过规则项的上限。中性权重互打大约 0.39（`rankAgainst` 公式），乘 100 再乘 `100_000`，约 3.9e6，仍高于任何没有学习分的边。
- 没有进入 Top-N 的边，`aiScores` 和 `learnedScores` 都没有键，两项都是 0。
- 注入阈值是 `minTotal` 默认 72（`MatchAgentProperties.java` 第 19 行）。能写入的 AI 分至少 72，对应 7.2e7。

`AiScoreMatchingTest.aiScoreDominatesRuleScoreInFinalPairing` 把这个支配关系当成既定行为。它保证的是「AI 分主导排序」，不是「和规则分同一尺度」。

另外 `recall_candidate.final_score` 被两种写入共用：召回把 0–1 的粗排分写进去（`RecallPipeline.java` 第 233–243 行），精排把 0–100 的裁判分写进去（`AgentRerankService.java` 第 263 行）。列类型 `NUMERIC(6,2)` 两者都能放下，读的人无法从列本身区分。

### 6. 全局 Top-N 精排会不会让一部分用户没有足够评估机会？

结论：**会。**

```96:99:api/aiditto-app/src/main/java/cn/aiditto/match/AgentRerankService.java
        List<Edge> top = new ArrayList<>(edges);
        top.sort(java.util.Comparator.comparingLong(Edge::weight).reversed());
        int limit = Math.min(props.topPairs(), top.size());
        top = top.subList(0, limit);
```

`topPairs` 默认 15，是整座城市、按规则边权排序后的全局前 15 条边，不是每人 Top-N。这 15 条边最多覆盖 30 个用户。其余用户的边不进裁判，也得不到学习分（学习分在同一个 `for (Edge e : top)` 里计算，第 108–113 行）。

再叠加问题 5：这最多 30 人的边被放大 1e5–1e6 倍，Blossom 会优先消化他们。不在这 15 条边上的人只能在剩余规则分里匹配。

默认 `MATCH_AGENT_ENABLED=false` 时精排直接返回空 Map（第 92–93 行），此时没有人得到 AI 评估，尺度退回纯规则。这是开关关闭，不是每人都被评估。

### 7. AI 分身模拟有没有可验证的额外预测价值？

结论：**没有可验证的增量。增量本身是 UNKNOWN，因为没有对照实验。**

开启模拟时，流程是一次 `simulate_date`，校验通过才把对话交给裁判；失败则对话为空，裁判仍只看画像（`AgentRerankService.java` 第 124–139 行）。`VoiceAssetService.voiceHint()` 把语气串拼进 persona（`VoiceAssetService.java` 第 34–49 行）。没有任何代码比较「有对话」和「无对话」对后续反馈的预测差。

本轮 `SimulationRunTest` 不调用 `AiJudge`，也不调用 LLM。它的 `aiScores` 是 `50 + 50 * (0.90 * rankAgainst + 0.10 * 召回先验)`（`SimDriver.java` 第 307–308 行）。这个数不能用来证明分身有预测价值。

`VoiceAssetServiceTest` 只断言提示词里有「语气参考」和脱敏。`AgentRerankLiveE2ETest` 本轮未跑（见 test-report）。

### 8. 反馈学习是否真正提升匹配质量，还是只改画像文本？

结论：**学习代码会改权重，权重会进边权公式；但生产周任务不会调用学习。合成实验里，固定池上排序质量有变化，封闭池上没有达到闸门。这些都不是真实用户结果。**

生产写路径：`FeedbackLearningService.scanAndRewrite` → `TasteRewriter.rewrite`。服务端用 `FeedbackSignal` 决定改哪一维、改多少，幅度夹在 ±0.15，模型只负责追加文本（`TasteRewriter.java` 第 97–98、184–202 行）。所以它不只改文本，也会改 `weights`。

这条写路径没有生产调用方。`FeedbackLearningService` 第 24 行写着「仿真回放每周期末调用 scanAndRewrite」。`SimDriver` 没有调用它，而是在内存里 `TasteWeights.apply`（第 454–460 行）。`MatchScheduler` 也不调用它。因此即使用户提交了反馈，下一周的 `WeekRunner.solve` 也不会因此更新权重，除非有本仓库之外的进程来调这个方法。本仓库内没有这样的进程。

`WeekRunner` 会读 `ai_pair_memory` 的 forbidden 对（第 218–220 行），但唯一写入是 `FeedbackLearningService` 第 119 行。写路径不跑，这张表在生产调度下保持空。

本轮合成结果（种子 `20261118`，200 人，12 周期，详见 baseline.json）：

- 封闭池、学习开：周期 1 top1 0.5150、regret 0.0886；周期 12 top1 0.4588、regret 0.1137。regret 斜率 +0.0020，单调下降只有 5/12。闸门要求周期 12 top1 ≥ 0.60、斜率 < 0、单调下降 ≥ 8。三项失败，报告判定 NO-GO。JUnit 仍是绿的，因为测试故意不把阈值写成断言（`SimulationRunTest.java` 第 117–120 行）。
- 同一批人关掉学习：周期 12 top1 0.4583、regret 0.1149。封闭池里学习和不学习一起掉，不能把下降单独算成学习的功劳或过错。
- 固定池（不累积往期配对）末周期：学习 top1 0.6200、regret 0.0649、ρ 0.7837；不学习 top1 0.5600、regret 0.0843、ρ 0.7821。差值 top1 +0.0600、regret −0.0194、ρ +0.0016。权重变化确实改变了配对；预测分和隐藏真值的秩相关几乎没动。
- 画像向量和隐藏真值的 Spearman ρ 从周期 1 的 0.8483 升到周期 12 的 0.9077。这是「权重更像隐藏真值」，不是「真实用户更满意」。

默认仿真把学习分打在所有边上（`learnedScoreTopN=0`，`SimDriver.Config.defaults` 第 100–101 行）。生产只打在 Top-15。所以这份固定池净收益高于生产口径。本轮没有跑 `withLearnedScoreTopN(15)`。

### 9. `wa.rankAgainst(wb)` 是否混淆了自身特征和伴侣偏好？

结论：**是。**

```166:176:api/aiditto-domain/src/main/java/cn/aiditto/domain/taste/TasteWeights.java
    public double rankAgainst(TasteWeights other) {
        return 0.6 * baseAgainst(other) + 0.4 * interactionAgainst(other);
    }

    private double baseAgainst(TasteWeights other) {
        double sum = 0.0, wsum = 0.0;
        for (int i = 0; i < DIMS.size(); i++) {
            String d = DIMS.get(i);
            double x = get(d); // 自身对维度 d 的偏好程度（排序系数）
            double y = other.get(d); // 对方在 d 上的特质强度（以对方学习画像权重近似）
```

`x` 被当成偏好，`y` 被当成特质强度，两份数据来自同一列 `user_taste.weights`。这列的迁移注释写的是「口味权重 / 看重程度」（`V10` 第 5–7 行）。`TrueScore.base` 的注释写明「画像权重是看重程度，不是自身属性强度」（`TrueScore.java` 第 50–55 行），实现却同样用 `b.get(d)` 当属性强度（第 62 行）。`rankAgainst` 是把这个仿真函数抄进生产边权。

反馈更新也只推动这一份向量：`FeedbackSignal` 按「愿再意见面」升降同一个维度（`FeedbackSignal.java` 第 59–63 行）。一次好评会同时抬高「我有多看重幽默」和「别人眼中我有多幽默」。

### 10. 画像更新和反馈学习并发时有没有数据竞争？

结论：**有丢失更新的窗口。活跃行的唯一约束能避免两行同时为 active，不能避免一次学习被丢弃。**

两边都用 `pg_advisory_xact_lock(userId)`：

- `TasteExtractor.write` 在锁内读 active 行、算版本、失效旧行、插入（第 88–121 行）。
- `TasteRewriter.rewrite` 在锁外读 active 行和本周次数，然后调用 LLM，再进入 `write()`（第 85–100 行）。`write()` 锁内只复查本周次数，不重读版本和权重（第 116–119 行），插入的版本号是锁外读到的 `old.version + 1`。

交错顺序：Rewriter 读到 v1 → Extractor 提交 v2 → Rewriter 插入 v2。主键是 `(user_id, version)`（`V6` 第 18 行），插入失败，事务回滚，`is_active` 不会停在全 false。接着 `rewrite()` 的 catch 调用 `mark()`，往 `taste_log` 写入 `trigger_feedback`（第 105–106、149–158 行）。`scanAndRewrite` 用这列做「已消费」判断（第 40–42 行）。这条反馈之后不会再学。

两个 Rewriter 并发时，锁内的 `weeklyCount` 能挡住第二个（第 117 行）。这个窗口是 Extractor 与 Rewriter 之间，以及「先读后锁」本身。

`VectorSyncService.syncAll` 也无锁读取 `is_active AND pending`（第 37–60 行），随后按版本回写。同步不在调度器里，所以生产上这条竞争目前不会自己发生；一旦有人把同步和学习同时跑起来，可能把旧文本的向量写成 synced、新版本仍是 pending。这是后续接线时的风险，不是当前周任务里正在发生的事。

`JudgeCalibrationService.bias` 是 `volatile` 的进程内字段（第 30、45–58 行）。学习和精排不在同一调度循环里，本轮不把它算成已发生的生产竞争。

### 11. 召回分、裁判分、最终排序分和真实反馈是否严格区分？

结论：**列和变量大多分开了，但有两处混用，不能称为严格区分。**

| 量 | 存在哪里 | 尺度 |
| --- | --- | --- |
| RRF / 粗排 | `recall_candidate.rrf_score`、`coarse_score` | 粗排被压到 0–1 |
| 裁判分 | `sim_report.total`，以及精排写入的 `final_score` | 0–100 |
| 规则展示分 | `match` 上的 `ScoreBreakdown.score()` | 0–100，不含 AI |
| 求解边权 | `MatchingSolver.weight`，不落单独的列 | 1e6 / 1e5 / 1e3 混合 |
| 学习分 | 只活在当次 `learnedScores` Map 里 | 0–100，再乘 1e5 |
| 真实反馈 | `feedback.rating` 1–5、`again`、`met` | 与上面都不同 |

混用：

1. `RecallPipeline.persist` 把 `coarseScore` 写入 `final_score`（第 243 行）。精排之后这条列才变成裁判分，而且只覆盖 low→high 那一行。反向行如果由召回写下，会一直留着 0–1 的粗排分。
2. `FeedbackLearningService` 和 `JudgeCalibrationService` 把 `final_score` 当作「裁判预测分」（`FeedbackLearningService.java` 第 34、98–99 行；`JudgeCalibrationService.java` 第 62–74 行）。若列里实际是粗排分，校准会把 0–1 和「评分×20」放在一起平均。

反馈原文不进边权。边权用的是权重向量和可选裁判分，不是 rating 本身。

### 12. 发布前是否重新检查年龄、同意、拉黑、撤回？

结论：**发布前不重新检查。年龄、拉黑、暂停、未满 18 岁只在入池和求解时查一次。没有单独的匹配同意字段。注销状态在入池 SQL 里被排除，发布时不再看。**

求解时：

- 未满 18 岁：`PoolGate`（第 51–52 行）和 `HardFilter.adult`（`MatchingSolver.java` 第 54–56、103 行）。
- 年龄区间、拉黑、已举报/forbidden：`HardFilter.violations`（第 30–35 行）。拉黑用的是入池当时读出的 `block_contact` 哈希（`People.java` 第 61、218 行）。
- 暂停、封禁未到期、未完成入驻、照片：`PoolGate.check`（第 54–59 行）。
- 入池 SQL 只取 `status IN ('active','paused','suspended')`（`People.java` 第 76 行）。`deleting` / `deactivated` / `purged` 不会进入候选。

发布：

```287:291:api/aiditto-app/src/main/java/cn/aiditto/match/WeekRunner.java
    private void publish(MatchDb.WeekRow week, Instant now) {
        db.publishDrafts(week.id(), Deadlines.respondDue(now), now);
        db.saveWeek(week.id(), WeekMachine.succeed(week.status(), WeekStep.PUBLISH), WeekStep.PUBLISH, null, null, null, now);
    }
```

`publishDrafts` 把该周所有 `draft` 改成 `published`（`MatchDb.java` 第 332–336 行），不读用户状态、不读拉黑、不再跑 `HardFilter`。

同意：问卷域和 `PoolGate` 都没有 consent 字段。能进池的条件是入驻完成、状态可用、照片够数。这不能当作一次单独的匹配授权复查。

召回过滤器里的年龄和 forbidden（`RecallPipeline.filter` 第 167–175 行，`QdrantRestClient` 第 195–216 行）不保护这条发布路径，因为召回不在周链路里。`VectorSyncService.purgeDeactivated`（第 269–278 行）会删向量，同样没有被调度，也不能挡住已经写成 draft 的配对。

## 与 V4 目标的差距

V4 要做的五件事里，当前代码的位置是：

1. 深度画像：有脱敏提取、persona/taste 文本、10 维权重。权重不区分「我是谁」和「我要谁」。
2. 双向匹配：硬约束和规则分的部分维度是双向的。AI 分和学习分是单方向、单标量。
3. 兼容性评估：裁判 rubric 存在，默认关闭，且只评估全局 15 条边。
4. 反馈学习：服务端夹紧和版本追加已实现，没有接进周任务。合成固定池有排序变化，封闭池闸门未过。
5. Match Lab：`SimDriver` 是测试里的合成回放，不是生产召回，也不是 `AiJudge`。

缺陷、复现和验收标准见 `risk-register.md`。数字见 `baseline.json`。调用链见 `callgraph.md`。测试见 `test-report.md`。
