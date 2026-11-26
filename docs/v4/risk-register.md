# V4-AUDIT-001 风险登记

基线：`c74921b`。优先级按「会让下一阶段在错误链路上开发」和「会让不合格配对被发出」来排。每条都给复现、影响、修复方向和验收。修复方向是建议，本轮没有改代码。

## P0

### P0-1 召回没有进入周匹配

现象：`RecallPipeline.recall`、`VectorSyncService.syncAll` 在 `src/main` 中没有调用方。`WeekRunner.solve` 对 `People.city()` 的全量候选做两两建边。

复现：在 `api/` 下搜索 `recall(`、`syncAll(` 的 Java 引用。生产引用只有方法定义。调用点只在 `VectorSyncIT`。再读 `WeekRunner.java` 第 208–239 行，确认求解输入是 `people.city(...)` 的 `Candidate` 列表。

影响：五路召回、Qdrant、粗排 Top-N、召回侧的年龄/forbidden 过滤，都不影响周三发出的配对。画像上的 `vector_sync_status='pending'` 也不会被消费。V4 若在「召回已在线上」的前提上加双向打分，会打在一条没人调用的链上。

修复建议：在 `WeekRunner.solve` 里，对每个入池用户调用召回（或一次批量召回），只用召回候选建边；Qdrant 不可用时走已经写好的结构化降级，并在 `week.stats` 里记录 `degraded`。向量同步要有自己的定时任务，在召回之前把 `pending` 刷完。不要把全城笛卡尔积保留成默认路径。

验收：

- 集成测试里，一座城市 130 个可匹配用户，召回每人最多 `coarseTopN`（默认 15）个候选；`MatchingSolver` 的边集合是这些候选的交，而不是 C(n,2) 过硬过滤后的全集。
- 关掉 Qdrant 时周任务仍发布，`week.stats.recallEngine=structured-only`，且硬约束违规仍为 0。
- `vector_sync_status='pending'` 的用户在下一次同步任务后变为 `synced`，或失败可重试且有计数。

### P0-2 全局 Top-15 加上 1e6/1e5 的边权，多数人得不到评估，被评估的边垄断排序

现象：精排开启时，按规则边权取全局 15 条边（`AgentRerankService.java` 第 96–99 行）。学习分也只算在这 15 条上。边权把 AI 分乘 1_000_000、学习分乘 100_000，规则目标分乘 1_000（`MatchingSolver.java` 第 154–157 行）。

复现：用 `AiScoreMatchingTest.aiScoreDominatesRuleScoreInFinalPairing` 的构造：给少数边 AI 分 95、其他边 10，求解结果只保留高 AI 边。再把「其他边」从 Map 里去掉（生产对非 Top-N 就是这样），高 AI 边仍然胜出。默认 `minTotal=72`，能写入的 AI 分至少带来 7.2e7 的权重，规则项上限约 1.2e5。

影响：一座城里最多 30 人进入裁判。这 30 人的边会压过其余所有人的规则高分边。其余用户没有裁判分，也没有学习分。这不是统一排序下的「精排更准」，而是两套尺度。

修复建议：

- 精排名额按人分配（每人 K 条），而不是全城 15 条。
- 边权改成同一尺度后再融合，例如都映射到 0–1 再加权；没有 AI 分的边使用显式先验，而不是 0。
- 学习分与 AI 分都要覆盖同一批「此人的候选」，不能只覆盖全局头部。

验收：

- 在 200 人、精排开启的夹具上，每个至少有一条过硬门槛的边的用户，都有不少于 K 条边带有同一尺度的评估分。K 由配置给出，测试读配置。
- 一条规则分 90、无 AI 分的边，不得仅因另一条规则分 40、AI 分 72 而在权重上落后超过融合公式所声明的权重。测试把公式里的系数写成断言。
- `week.stats` 记录 `usersWithAiScore / participants`。该比例在夹具上达到测试声明的下限，而不是固定 15 条边。

### P0-3 `rankAgainst` 把同一份偏好权重当成对方的特质，并且只算 id 较小的一方

现象：`TasteWeights.rankAgainst`（第 166–176 行）用调用方的维度当偏好、参数方的同一列当特质强度。生产只调用 `low.rankAgainst(high)`（`AgentRerankService.java` 第 111 行）。`TasteWeightsTest` 已证明反向不相等。

复现：构造 A 的 humor=0.9、expressiveness=0.1，B 相反，断言 `a.rankAgainst(b) > b.rankAgainst(a)`（现有测试）。然后在精排里只保留 `min(id).rankAgainst(max(id))`。交换两人的 user id 大小，学习分会变，配对边权会变，两人的问卷不变。

影响：反馈一次只改一份向量，却同时改变「我看重什么」和「别人看到的我」。双向匹配没有发生。id 顺序会改变谁的偏好在起作用。

修复建议：拆成两个向量，例如 `self_traits` 与 `partner_prefs`。边的学习分改为 `prefsA·traitsB` 与 `prefsB·traitsA` 的显式融合（平均或最小值，选一种并写进测试）。反馈只更新产生反馈的那个人的 `partner_prefs`，以及被评价方的 `self_traits` 时必须有独立证据，不能共用一个 delta。

验收：

- 交换两人的 id，学习分不变。
- A 的偏好向量变化只改变 A→B，不改变 B 作为「特质」被别人打分的那一项，除非测试明确更新了 B 的特质向量。
- 单元测试覆盖互补维度和中性向量，数值仍落在 0–1。
- 仿真的 pred-true ρ 使用同一套双向融合，并单独报告 A→B 与 B→A 的差。差的定义写在测试里，不能再缺失。

### P0-4 发布前不复查年龄、拉黑、注销

现象：`WeekRunner.publish` 只调用 `publishDrafts`，把该周所有 draft 改成 published（`WeekRunner.java` 第 287–291 行，`MatchDb.java` 第 332–336 行）。

复现：在求解写入 draft 之后、发布之前，把其中一方的 `user.status` 改成 `deleting`，或插入一条 `block_contact` 使双方哈希相交，或把生日改到未满 18 岁。现有 `publish()` 不会读这些表。发布后的 `match.state` 仍是 `published`。本轮没有数据库，这条用代码路径确认；`MatchIT` 为 NOT_RUN，所以还没有一条自动化测试锁住它。

影响：求解和发布之间用户拉黑、申请注销、或资料变化，仍会收到配对。召回侧和向量删除侧即使以后接上，也挡不住已经写成的 draft。

同意：代码里没有匹配同意字段。`PoolGate` 只检查入驻完成、照片、暂停、封禁、未满 18 岁。不能把「曾经完成入驻」当成发布瞬间的授权。

修复建议：`publish` 在同一事务里重新加载双方 `Candidate`（或至少状态、生日、拉黑、forbidden），对每一对 draft 再跑 `HardFilter.violations` 和 `PoolGate`。任一失败则该对不发布，记入 `week.stats.republishDropped`，并释放两人，避免占着 open match。若产品要单独的同意，先增加字段，再在这里读它。

验收：

- IT：draft 已写入后把一方改为 `deleting`，发布后该对不是 `published`，另一方回到可匹配。
- IT：发布前插入拉黑，该对被丢弃。
- IT：一方在求解后变为未满 18 岁（测试时钟或生日），该对被丢弃。
- 其余未变化的 draft 仍发布。硬约束违规计数为 0。

## P1

### P1-1 精排在 persona 非空时丢掉择偶文本，结构化偏好也不进裁判

现象：`loadPersonas` 使用 `persona.isBlank() ? taste : persona`（`AgentRerankService.java` 第 183 行）。年龄、身高、学历、预算、爱好、期望性别只存在于 `Candidate`，`AiJudge.judgeUser` 的参数里没有它们。`isoReachable` 和 `slotsOverlap` 在调用处被写成 `true, true`（第 138 行）。

复现：准备 persona 与 taste 都非空的两行 `user_taste`，对精排打开开关，捕获 `judge_chemistry` 的 user prompt。其中没有 taste 原文，也没有年龄区间。

影响：模型按「这个人是什么样」打分，不按「这个人要什么人」打分。硬过滤仍能挡住越界年龄，但裁判分不反映偏好，却以 1e6 的系数主导排序。

修复建议：提示词同时包含截断后的 persona 和 taste。结构化偏好用白名单字段（年龄区间、预算档、关系期望），不要把证件、联系方式带进去。等时圈和时间槽用求解器已经算过的布尔值，不要写死 true。

验收：提示词快照测试要求 taste 与 persona 同时出现；写死的 `true, true` 从生产调用点消失；脱敏测试仍然拒绝邮箱、手机号、照片。

### P1-2 反馈学习、forbidden、裁判校准都没有调度入口，仿真也没有调用生产服务

现象：`FeedbackLearningService.scanAndRewrite` 只在 `FeedbackLearningIT` 被调用。`PairMemoryService.record` 只有这一处写入。`SimDriver` 直接 `TasteWeights.apply`，与类注释「仿真调用 scanAndRewrite」不符（`FeedbackLearningService.java` 第 24 行，`SimDriver.java` 第 454–460 行）。

复现：搜索 `scanAndRewrite` 与 `.record(`。确认 `MatchScheduler` 的方法体只有 `weeks.tick` 和 `matches.tickDeadlines`。

影响：`LEARNING_ENABLED=true` 不产生学习。双向拒绝不会变成 forbidden。校准 bias 保持 0。下一周排序与反馈无关。仿真测到的固定池 top1 +0.06 不能当成这条生产链的效果。

修复建议：在周关闭或反馈截止后由调度器调用 `scanAndRewrite`，与用户提交请求隔开（现有设计已经是批量的，保持这一点）。仿真增加一条路径，用同一个 `FeedbackSignal`→`TasteWeights.apply` 的结果与 `TasteRewriter` 在 mock LLM 下的权重断言一致。修正类注释。

验收：

- IT：两条 `again=no` 之后，下一次 `WeekRunner.solve` 的边集合不含这一对，即使精排关闭。
- IT：一条带 `good:humor` 的反馈经过一轮调度后，`user_taste.weights` 的 humor 变化绝对值 ≤ 0.15，且只变这一维。
- 调度器关闭 `LEARNING_ENABLED` 时，`taste_log` 不增加 `taste_rewrite` 行。

### P1-3 `final_score` 同时表示粗排分和裁判分

现象：`RecallPipeline.persist` 把 `coarseScore` 写入 `final_score`（第 243 行）。`AgentRerankService.persist` 把裁判 `total` 写入同一列，且只写 low→high（第 253–263 行）。校准 SQL 把该列当作预测分（`JudgeCalibrationService.java` 第 62–69 行）。

复现：对同一 `(week, user, cand)` 先走召回再走精排，看 `final_score` 从 0–1 变成 0–100。只走召回的反向行保持 0–1。用校准 SQL 会把这两种数和 `rating*20` 放在一起。

影响：一旦 P0-1 把召回接上，而又没改这列，校准会在样本达到 30 后把 bias 算错，再加到裁判分上（上限 ±10，仍然会移动排序）。

修复建议：`final_score` 只存裁判分；粗排只留在 `coarse_score`。召回插入时 `final_score` 置 NULL。校准 SQL 增加「sim_report 存在且尺度为 0–100」的条件。精排若需要双向留痕，写两行或增加方向字段。

验收：只跑召回的行 `final_score` 为 NULL，`coarse_score` 在 0–1。校准在混有粗排行时样本数不增加。低置信度与硬冲突的行不进入校准，或测试明确它们的处理。

### P1-4 画像提取与反馈重写并发会丢掉一次学习

现象：`TasteRewriter` 在拿 `pg_advisory_xact_lock` 之前读取 active 版本（第 85–100 行），锁内不重读（第 116–119 行）。`TasteExtractor` 在锁内插入下一版本（第 88–121 行）。主键 `(user_id, version)` 使后写入的一方失败回滚。失败路径 `mark()` 把 `trigger_feedback` 写上，扫描 SQL 就不再挑选这条反馈（`FeedbackLearningService.java` 第 40–42 行）。

复现：两个事务。T1 读到 version=1 后停在 LLM 调用前。T2 完成提取，提交 version=2。T1 继续 `write()`，插入 version=2 失败。检查 `user_taste` 仍只有提取产生的 v2，`taste_log` 有一条 `taste_rewrite_failed`，再次 `scanAndRewrite` 不再处理该 `match_id`。需要数据库，本轮 IT 为 NOT_RUN；路径由源码确定。

影响：用户改问卷和反馈学习叠在一起时，反馈被标记为已消费，权重没有加上那次 ±0.15。活跃行唯一索引能防止两行 `is_active=true`，所以不是脏读双活，是丢更新。

修复建议：锁内重新 `SELECT ... FOR UPDATE` 当前 active 行，用锁内版本计算 `version+1`，用锁内权重做 `apply`。插入冲突时不要 `mark` 为永久失败，留下可重试状态。`weeklyCount` 不要把尚未成功的失败记进「本周已学过」，否则一次冲突会吃掉本周名额（`TasteRewriter.weeklyCount` 第 168–171 行把 `taste_rewrite_failed` 算进本周次数）。

验收：上述交错之后，要么权重含有这次 delta，要么 `scanAndRewrite` 下一次仍能捡起这条反馈。`is_active` 始终只有一行。并发测试重复 100 次无主键异常逃出事务。

### P1-5 仿真召回不是生产召回，全边学习分高于生产口径

现象：`SimDriver.recallFusion` 五路共用 `TrueScore.compatibility`（第 482–484 行）。`defaults()` 的 `learnedScoreTopN=0`（第 100–101 行）给所有边学习分。生产只给 15 条边。

复现：读 `SimDriver.java` 第 274–296 行与 `RecallPipeline.java` 的 R3/R4/R5。前者没有结构化标签分、偏好分和真实向量。

影响：本轮固定池 top1 +0.0600、regret −0.0194 是合成、全边注入的上界参考。用它决定「学习已经有效，可以做 V4 模型」会高估。封闭池闸门已经是 NO-GO。

修复建议：仿真默认改为 `learnedScoreTopN=15`，并增加与 `AgentRerankService` 相同的选边代码路径（抽一个纯函数，两边都调用）。五路召回要么调用 `RrfFusion` 时使用真正不同的排序键，要么在报告里停止称为五路生产召回。

验收：同一种子下，topN=15 与 topN=0 的 top1/regret 都写入 `baseline.json`。文档不再把全边注入的数字称为生产口径。

## P2

### P2-1 分身模拟没有预测增量实验

现象：模拟对话失败时裁判照常打分（`AgentRerankService.java` 第 129–139 行）。`VoiceAssetService` 只改提示词。没有「有对话 vs 无对话」对反馈的对照。

复现：代码搜索 `simulate_date` 的结果只有精排服务和 `AiJudge` 测试。测试检查 JSON 形状和禁用词，不检查对 rating 的相关。

影响：V4 若把分身当兼容性模型的输入，目前没有证据说明它比只看画像更好。也可能更差（幻觉对话）。大小是 UNKNOWN。

修复建议：离线对同一批合成或事后脱敏的配对，跑两次裁判，一次带合法 transcript，一次 transcript 为空，比较两者与 `feedback.rating` 的秩相关。相关没有提升就不把对话送进边权。

验收：实验报告给出两组 ρ 和样本量。ρ 之差的置信区间包含 0 时，生产 `simulate-enabled` 不得改变边权，只允许留在 `sim_report` 里供人读。

### P2-2 费用列恒为 0，降级计数与「未写入 AI 分」不是同一件事

现象：`persist` 的 `llm_cost_rmb` 参数是字面量 0（`AgentRerankService.java` 第 257 行，`RecallPipeline.java` 第 237 行）。裁判分低于 `minTotal` 时仍然 `judged++`，但不放入 `aiScores`（第 151–154 行）。`degraded` 只覆盖无画像、非法输出、低置信度，不覆盖「分太低所以没进边权」。

复现：构造 total=71、confidence=0.9、无冲突的合法裁判 JSON。`judged` 增加，`aiScores` 不含该边，`degraded` 不增加。

影响：上线后无法从 `week.stats` 看出多少边其实没有 AI 分，也无法对账模型费用。

修复建议：从 `LlmResult` 的 token 数写入费用；`week.stats` 分开 `judged`、`admittedToSolver`、`belowMinTotal`、`degraded`。

验收：mock 模型返回已知 token 时，`llm_cost_rmb` 与单价公式一致，且不为 0。71 分的边出现在 `belowMinTotal`，不出现在 `aiScores`。

### P2-3 校准样本门槛和进程内 bias

现象：样本少于 30 时 bias 为 0（`JudgeCalibrationService.java` 第 82–84 行）。bias 存在 JVM 字段里，`rerank` 开头 `refresh()` 从库恢复。多实例时每次重算会把旧行 `active=false` 再插入新行，没有跨实例锁。

复现：读 `recompute` 第 86–92 行。两个进程同时 `recompute`，可能有两行 `active=true`，`refresh` 的 `ORDER BY created_at DESC LIMIT 1` 只是挑一条。

影响：在 P1-2 接上调度之前，这条不会在生产跑起来。接上之后、样本混用 P1-3 的错误尺度时，bias 会开始改分。

修复建议：与 P1-3 一起做。`recompute` 用单行版本号或咨询锁。`refresh` 在多行 active 时记告警并取约定的那一行。

验收：并发两次 `recompute` 后 `active=true` 的行数始终为 1。样本少于 30 时 `apply` 不改变分数。

## 建议的实施顺序

1. P0-4 发布复查。它不依赖召回和新模型，能先挡住错误配对。
2. P0-1 把召回接进 `solve`，同时做 P1-3，避免 `final_score` 被接脏。
3. P0-3 拆开特质和偏好，再改边权。在此之前不要把 `rankAgainst` 的仿真 ρ 当作 V4 的目标函数。
4. P0-2 与新的双向分一起改尺度和名额。先改名额、沿用旧公式，仍然会垄断。
5. P1-2 接上学习调度。P1-4 的锁内重读要在调度上线前完成。
6. P1-1、P2-1 在精排真正开启前改完提示词，并先做消融再让对话影响边权。

本轮之后应停在审计。深度画像和双向引擎的实现以 P0-3 的数据模型为准，不要在现有单向量 `weights` 上继续加系数。

## V4-S01 状态（2026-11-25）

P0-4 的代码已经落到 `master_agent_v4`，但还不能销项。

- 发布前会重读同意、账户、拉黑和硬约束。`publishDrafts` 会抛错。
- V4 合规模式改为 fail-closed：只有 GRANTED 可以发布。UNKNOWN 和 REVOKED 都不可以。历史用户不回填。
- `MATCH_CONSENT_LEGACY_ALLOW_UNKNOWN` 只放行 UNKNOWN，默认关闭，不属于合规验收。REVOKED 不能被它放行。生产打开它之前要人工审批。本轮没有改生产配置，没有部署。
- 领域测试 463 通过，合成 200 人仍是 83 对、0 条硬约束违规。
- `PublishSafetyIT`、`ConsentLegacyIT`、`MatchIT` 在本机 NOT_RUN。Docker Desktop 不存在，5432 和 55432 都没有 PostgreSQL，Homebrew 的 formula 拉取超时。没有真实数据库结果，就不能说 P0-4 已经验收。
- P0-1、P0-2、P0-3 以及下面的 P1/P2 都没有在本轮改。不要开始 V4-P01。

真实用户效果没有评估。领域测试、数据库测试、线上效果是三件不同的事。

## V4-P01-FINAL（2026-11-26）

阶段性实现 `76468ce` 的领域测试、PostgreSQL 集成测试和两次 Actions 已经通过。最终验收当时是 NO-GO。源码审计的 5 项在本轮修复，记录在 `docs/v4/V4-P01-final-audit.md`。

- 源版本水位不再绑在当前快照行上。相同内容不重复插行。迟到的更低源版本被拒绝。
- 处理授权、匹配同意、模型外发授权分开。旧代次不能在撤回或清除之后写回。
- 证据要能解析到该用户的有效来源。模拟内容和字符串前缀不是真实反馈的证明。
- 伪造哈希、伪造 canonical、非法直接构造不能进入持久化边界。
- 普通快照不复制自由文本。正则没有被当成完整的脱敏。

尚未集成，因此生产画像处理保持关闭：

- `AccountService` 注销只写 `deleting` 和 `purge_at`，不调用 `erase`。
- 匹配同意的撤回不调用 `revokeProcessing`。
- 没有真实 LLM 提取，`saveModelRevision` 拒绝写入。
- 反馈事件还没有证据目录。

P0-1、P0-2、P0-3 未改。算法质量仍是 NO-GO。不部署，不合并 V3，不启动 V4-P02。

## V4-P01 架构验收（2026-11-26）

V4-P01 与 V4-P01-FINAL 通过当前阶段架构验收。验收基线是 `ff4cf2169f5aff3839abfacc28e2e7bac3cdc998`。修复提交是 `4f29edb067e6367b8c0f6d79b2113a3392467485`。已核实的运行：

- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45774884362
- https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45775384976

当时的基线是 482 项领域测试、37 项 PostgreSQL 集成测试、3 项数据库防误操作测试，以及 200 人仿真 83 对、0 条硬约束违规。

生产画像处理仍然关闭。打开它之前，下面三件事都要接上，而且三项授权继续分开：

- `AccountService` 的实际清除要调用 `erase`。现在注销只写 `deleting` 和 `purge_at`。
- 匹配同意的撤回要调用 `revokeProcessing`。匹配同意不能隐式打开画像处理。
- 画像处理授权必须单独授予。`aiditto.relationship-profile.processing-enabled` 在 `application.yml` 里保持 false，`application-prod.yml` 里写死 false。

向外部模型提供数据使用独立的 `model_export_authorization`。撤回它调用 `revokeModelExport`，不改变处理授权、代次或匹配同意。

## V4-P02 状态（2026-11-26）

P02 只增加有证据的画像提取。阶段实现 `ef08182426d414c117d22ebf909baa02f0622ab1` 的运行 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45780763981 为 success。最终架构验收当时是 NO-GO。

本轮只补六项审计：证据语义、外发代次、任务租约、情景含义、自由文本边界，以及独立的真实模型评估集。匹配求解、评分、召回和发布闸门没有改。生产开关保持关闭。真实模型理解评估仍是 NOT_RUN。生产启用和算法质量仍是 NO-GO。

剩余风险：

- 封闭短语不是通用理解。挂起的特征不能进正式匹配分，但用户确认路径还没有写入。
- 发送判定之后、字节离开本进程之前的撤回，不能把已经外发的数据说成未发送。写入仍会被代次拒绝。
- 生产路径没有受控自由文本表。模型特征目前只能来自批准含义正好是 `low`、`steady` 或 `high` 的情景选项。
- 账户清除仍未调用 `erase`。匹配同意撤回仍未调用 `revokeProcessing`。

## V4-INFRA-001（2026-11-25）

本地不再安装 PostgreSQL。集成测试改到 GitHub Actions：`.github/workflows/v4-ci.yml`，一次性 PostgreSQL 16，库名只能是 `aiditto_s01_test`。`TestDatabaseGuard` 在 `flyway.clean()` 之前拒绝其它库名、其它主机，以及缺少 `TEST_DB_URL` 的情况。没有生产凭证，没有 LLM API Key。

前两次运行都失败，而且失败点相同。PostgreSQL 16.16。`PublishSafetyIT` 在准备数据时断言北京草稿 `>= 8`，实际 3。这 3 对是 cohort 规则的结果：synthetic 不能互配，北京只有 3 个 demo。那两次没有覆盖到并发代码。

夹具拆开后的运行 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45591470398 总结论 success。文档提交再次运行 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45592507019 也成功。14 个发布安全方法，包括回滚、撤回、拉黑、注销、硬约束、重复发布和共用用户锁，都执行并通过。2026-11-26 架构验收通过当前阶段。P0-4 的工程验收关闭。算法质量仍是 NO-GO，不部署。P0-1、P0-2、P0-3 未改。V4-P01 从画像模型和证据开始，不改匹配算法。
