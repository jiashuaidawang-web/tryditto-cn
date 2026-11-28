# V4-I02 现有匹配业务审计

影子引擎不调用下面任何会写业务状态的方法。

## 调用图

```text
AdminMatchController / MatchScheduler
        |
        v
WeekRunner
  LOCK   People.city(week.pool, weekStart, now)
         PoolGate.check
         MatchingConsentService.allowsPool
         MatchDb.insertParticipants
  SOLVE  MatchingInput(Weights.W1, pastPairs, reportedPairs + ai_pair_memory.forbidden)
         AgentRerankService.rerank   # 只在生产求解里；影子不调用
         MatchingSolver.solve
         MatchDb.insertDraft / insertSide / insertPlan
  COPY   写 match_copy
  PUBLISH PublishGuard 发布，并进入通知与约会状态
  CLOSE  关闭本周
```

`week.pool` 是城市，现有入口使用 `bj` 和 `sh`。`People.city` 只读 `active`、`paused`、`suspended`，再由 `PoolGate` 排除未成年、未完成、照片不足、本周暂停、停用和未结束的匹配。`onboarding`、`deleting`、`deactivated`、`purged`、`banned` 不在这份城市清单里。

注册用户的 `cohort` 是 `real`。`HardFilter` 只让 real 配 real。年龄、性别、匹配意向、距离和历史拉黑哈希来自 `profile` 与 `place`，由 `People` 收成 `Candidate`。历史配对来自 `match`。举报来自 `report`。永久禁止来自 `ai_pair_memory.forbidden`。

硬约束在 `HardFilter` 和 `PoolGate`。软评分在 `Scorer`，权重是 `Weights.W1`。求解结果先写成 `draft`。`COPY` 和 `PUBLISH` 才会产生文案、发布和后续通知。

## 影子隔离

`V4ShadowRunner` 只在 `V4_SHADOW_TEST` 下读取 `People.city`、`MatchDb.pastPairs`、`reportedPairs`、`forbiddenPairs`，然后调用现有的 `GlobalCandidateGraphBuilder` 和 `V4GlobalMatchingEngine`。它不调用 `WeekRunner`、`PublishGuard` 的发布、通知、短信、邮件或约会创建，也不写 `match`。

V3 对照使用 `new MatchingSolver().solve(MatchingInput)`，权重仍是 `Weights.W1`，不写草稿。
