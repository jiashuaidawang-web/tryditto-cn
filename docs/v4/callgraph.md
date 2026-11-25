# V4-AUDIT-001 真实调用链

基线 commit：`c74921b527166bdef9e3577cf55338170377cdde`。

「真实」指 `src/main` 里能从定时任务、HTTP 或 Spring 事件走到的调用。只被测试调用的方法单独标出。

## 1. 生产周匹配（定时任务真的会走）

```text
MatchScheduler.tick                         match/MatchScheduler.java:23
  WeekRunner.tick                           match/WeekRunner.java:111
    drive → runStep
      LOCK   People.city                    match/People.java:59
             PoolGate.check                 domain/matching/PoolGate.java:50
             week_participant 快照
      SOLVE  People.city（同一次 drive 会复用 LOCK 的缓存；下一次 tick 会重载）
             MatchingInput(过去配对, reported ∪ forbidden)
             MatchingSolver.buildEdges      domain/matching/MatchingSolver.java:100
               HardFilter.adult / violations
               Scorer.score
               门槛 pairThreshold
             AgentRerankService.rerank       仅当 bean 存在；方法内默认 enabled=false 则立即返回
               全局边权 Top-15
               user_taste.persona/taste/weights
               VoiceAssetService.voiceHint
               LlmGateway simulate_date → AiJudge.validateTranscript
               LlmGateway judge_chemistry → AiJudge.parse
               JudgeCalibrationService.apply
               INSERT recall_candidate（low → high 一行）
             MatchingInput.withAiScores / withLearnedScores
             MatchingSolver.solve → BlossomMatcher（超时则 GreedySolver）
             INSERT match / match_side / date_plan（state=draft）
      COPY   MatchCopyWriter（AI 文案默认关闭）
      PUBLISH MatchDb.publishDrafts         不重新跑 HardFilter
      CLOSE
  MatchFlow.tickDeadlines
```

`BlossomMatcher` 不读画像、不读向量，只对已经建好的边做最大权匹配。

## 2. 写进了主链路、但默认不产生分数

```text
WeekRunner.solve
  → AgentRerankService.rerank
      if (!props.enabled()) return 空 Map          AgentRerankService.java:92
```

配置：`aiditto.match.agent.enabled`，环境变量 `MATCH_AGENT_ENABLED`，默认 false（`application.yml` 第 74–78 行，`MatchAgentProperties.java` 第 17 行）。

关闭时 `solve()` 的 input 不加 AI 分、不加学习分，边权退回 `objective * 1000 + tiebreak`。

## 3. 实现了、但没有任何生产调用方

下面三个 public 方法，`src/main` 中零引用。调用只存在于 IT。

```text
VectorSyncService.syncAll
  读 user_taste WHERE is_active AND vector_sync_status='pending' AND user.status='active'
  EmbeddingService.embed(taste_text), embed(persona_text)
  QdrantRecallService.upsert / delete
  回写 embedding_id、vector_sync_status
  purgeDeactivated：status ∈ deactivated,purged,banned,deleting,suspended 时删点

RecallPipeline.recall
  读 active user_taste + profile
  QdrantRecallService.taste / persona          R1、R2
  StructuredRecallService.routes               R3 标签、R4 偏好、R5 探索
  RrfFusion.merge
  coarseRank（条件 0.40 + RRF 0.30 + 公平 0.10 + FeedbackPrior 0.20）
  INSERT recall_candidate，并把 coarse_score 写入 final_score

FeedbackLearningService.scanAndRewrite
  读尚未写入 taste_log.trigger_feedback 的 feedback
  TasteRewriter.rewrite
  PairMemoryService.record（双方 again=no 时 forbidden=true）
  JudgeCalibrationService.recompute
```

`WeekRunner` 会读 `MatchDb.forbiddenPairs()`，所以 forbidden 行如果已经在库里，求解会排除。没有任何生产代码写入这些行。

`TasteBatchService.refreshMissing` 同样只被 IT 调用。它是画像提取失败后的补偿入口，没有定时任务。

## 4. 画像写入（生产事件，真的会走）

```text
Onboarding 完成 / 答案编辑
  ProfileWrittenEvent
    TasteListener.onProfileWritten            AFTER_COMMIT，taste.enabled 默认 true
      TasteExecutor
        TasteExtractor.ensure
          AnswerStore.load
          TasteFacts.from                     白名单脱敏
          LlmGateway taste_extract             失败则 TasteWriter.template
          pg_advisory_xact_lock(userId)
          INSERT user_taste 新版本
          继承旧 weights，vector_sync_status='pending'
          INSERT taste_log changed_dim=taste_extract
```

`pending` 会一直挂着，直到某个进程调用 `VectorSyncService.syncAll`。本仓库的调度器不调用它。

## 5. 反馈若被手动调用时的链

这条链在 IT `FeedbackLearningIT` 里是通的。生产调度器不到达这里。

```text
FeedbackLearningService.scanAndRewrite
  TasteRewriter.rewrite
    FeedbackSignal.derive          决定维度和 ±0.15 以内的 delta
    锁外读取 active user_taste
    LlmGateway taste_rewrite       失败则规则追加一句
    pg_advisory_xact_lock
    失效旧 is_active，INSERT 新版本 weights
    taste_log.trigger_feedback = match_id
  PairMemoryService.record
  JudgeCalibrationService.recompute
    读 recall_candidate.final_score 当作预测分
    样本 ≥ 30 才把 bias 夹到 ±10
```

下一周如果精排开启，`AgentRerankService.loadTasteWeights` 会读新的 active `weights`，但只对全局 Top-15 边调用 `rankAgainst`。

## 6. 仿真回放实际调用了什么

`SimulationRunTest` → `SimDriver.run`。它用生产的 `MatchingSolver`、`RrfFusion`、`FeedbackSignal`、`TasteWeights.apply`。它不调用：

- `RecallPipeline` / `QdrantRecallService` / `VectorSyncService`
- `AiJudge` / `AgentRerankService` / `LlmGateway`
- `FeedbackLearningService` / `TasteRewriter` / `PairMemoryService`

仿真里的「五路召回」是 `SimDriver.recallFusion`：五路用同一把 `TrueScore.compatibility(learned, learned)` 排序，探索路加抖动（`SimDriver.java` 第 463–484 行）。这不是 `StructuredRecallService` 的 R3/R4/R5。

仿真里的「AI 分」是 `rankAgainst` 和这条召回先验的线性组合，未召回的边写成 0。0 和「没有 AI 分」在边权公式里的 AI 项相同，都会让有正分的边压过它们。

## 7. 分数在链上的位置

```text
People.city 的问卷字段
  → Scorer → ScoreBreakdown.score     0–100，写入 match，用户看到的规则分
  → ScoreBreakdown.objective + tiebreak
        若精排开启且该边在 Top-15：
          + AiJudge.total * 1_000_000          裁判分，还可加 calibration bias
          + rankAgainst * 100 * 100_000        学习分
        → Blossom 只看这个 long weight

RecallPipeline（未接入）
  → rrf_score / coarse_score
  → 同时把 coarse_score 写入 final_score

反馈表 feedback.rating / again / met
  → 只在 scanAndRewrite 被调用时进入 FeedbackSignal
  → 不直接进入 Blossom
```

## 8. 表

| 表 | 谁写 | 谁在生产周任务里读 |
| --- | --- | --- |
| `user_taste` | TasteExtractor、TasteRewriter | AgentRerank（精排开启时）；Recall/VectorSync 不在周任务里 |
| `taste_log` | 上面两个写入方 | FeedbackLearning 的「已消费」判断；该服务未被调度 |
| `recall_candidate` | RecallPipeline 或 AgentRerank | 周求解不读。校准和学习会读 `final_score` |
| `ai_pair_memory` | 仅 FeedbackLearningService | WeekRunner.solve 读 forbidden |
| `judge_calibration` | JudgeCalibrationService.recompute | AgentRerank 在精排开启时 `refresh/apply` |
| `match` | WeekRunner | 发布、首页、反馈 |
| `feedback` | 用户提交链路 | 仅 FeedbackLearningService 批量读 |
| `voice_profile` / `voice_sample` | 情景采集 | AgentRerank → VoiceAssetService |

迁移：`V6__ai_taste_recall.sql` 建画像和召回留痕，`V10__feedback_learning.sql` 加 `weights` 和 `judge_calibration`，`V12__voice_scenario.sql` 加语气资产。
