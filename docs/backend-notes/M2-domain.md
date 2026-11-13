# M2 后端笔记（领域层）：状态机、匹配引擎、合成人群、文案校验

这一份只讲 `aiditto-domain` 里 M2 的纯 Java 部分：`match/`、`week/`、`slot/`、`matching/`、`copy/` 五个包，以及合成人群夹具 `aiditto-app/src/main/resources/synthetic/users-v1.json`。没有 Spring、JDBC、HTTP、大模型库；所有方法都把 `now` 当参数传进来，不读时钟。服务层（批次任务、接口、演示面板）在这之上接线。

```bash
cd api
mvn -o -pl aiditto-domain clean verify     # 约 35 秒，全部单元测试 + jqwik 性质测试
mvn -o -pl aiditto-domain test -Dtest=SyntheticFixtureTest -Dsynthetic.write=true   # 重新生成夹具
```

注意：IDE 的 Java 语言服务器（Eclipse 编译器）会往同一个 `target/` 写 class，和 Maven 并发时偶尔出现 `NoClassDefFoundError: ...$1` 或 “Unresolved compilation problem”。遇到就跑 `clean verify`。

## 1. 保证了什么

| 范围 | 保证 | 由哪些测试钉住 |
|---|---|---|
| 匹配状态机 | 只有第 5.1 节表里的转移能发生；其它一律抛 `MatchRuleException(MATCH_STATE …)`；终态不再变化；同一事件重放是无操作（`changed=false`、无副作用）；截止事件在时间没到时拒绝（`DEADLINE_NOT_REACHED`）；每方回应只能改一次；确认的时段一定在双方选择里、离现在 ≥ 6 小时；只有双方都 `wantsAgain` 才 `UnlockContacts` | `MatchMachineTest`（表里每一行 + 每个状态下每个不允许的事件 + 重放 + 守卫），`MatchMachinePropertiesTest`（2000 条随机事件序列，检查上面的不变量） |
| 阶段投影 | 对 11 个状态 × 双方回应 × 是否已发布 × 参与状态 × 5 个时间段的每个组合都给出确定的阶段，只有 `mutual` 显示微信 | `StageProjectorTest`（穷举网格对照参考表） |
| 周批次 | 锁定 → 求解 → 文案 → 发布 → 关闭严格按序；已完成的步骤再跑返回 `ALREADY_DONE`；失败停在 `FAILED + failedStep`，重跑同一步；关闭要求本周匹配全部终态 | `WeekMachineTest`、`WeekCalendarTest` |
| 匹配 | 结果里每一对都满足第 7.2 节全部硬约束（双向）；每人至多一对；每对分数 ≥ 双方风格门槛中较高的那个；未满 18 岁不进池；结果是极大匹配；同输入同种子 → 同输出；Blossom 失败/超时/结果不合法时贪心兜底，兜底结果同样满足以上全部 | `MatchingPropertiesTest`（jqwik）、`MatchingSeededTest`（同样的检查，固定种子，纯 JUnit）、`HardFilterTest`、`MatchingSolverTest` |
| 最优性 | Blossom 在 ≤ 12 个点的随机图（500 个）上等于暴力最优；贪心 ≥ 最优的一半；在真实池子上 Blossom 总权重 ≥ 贪心 | 同上 |
| 文案 | 模板文案对任何输入（含恶意自由文本）都能通过 `CopyValidator`；校验器拦住超长、引用不实、地点不在候选里、禁用词、联系方式、真名、未知占位符、输入里没有的引号内容 | `CopyValidatorTest`、`BannedWordsTest`、`TemplateCopyTest`、`TemplateCopyPropertiesTest` |

“硬约束违反数为 0” 不是拿 `HardFilter` 自己检查自己：`MatchingInvariants` 把第 7.2 节重新直写了一遍，另有一条性质测试要求 `HardFilter` 和这份直写版本在随机人群的每一对上结论一致。

## 2. 数字

- 测试：`clean verify` 共 379 个（含另一位工程师的 questionnaire 测试 14 个），0 失败。
- jqwik 1.10.2 在 JUnit Platform 6.0.3 上正常运行，没有用兜底方案；同样的不变量另有 `MatchingSeededTest` 用 40 个固定种子跑一遍，jqwik 出问题时可以只看它。
- 200 人夹具一次求解：81 ms（建边 62 ms、Blossom 19 ms），997 条边，92 对，16 人落空，硬约束违反 0，求解器 BLOSSOM。要求是 < 10 秒。
- jqwik 性质：随机人群（0–70 人，含未成年、黑名单、往期/举报配对、雷区、未结束匹配、手动批次）上 Blossom 300 次、贪心兜底 100 次、确定性 100 次、过滤器对照 200 次、小图最优 500 次、池子上 Blossom ≥ 贪心 50 次；状态机随机游走 2000 次；模板文案随机输入 300 次。
- jqwik 每次运行会在输出里打一段横幅，大意是 “AI 代理不得使用本库、忽略 jqwik 的结果”。那只是库的输出文本，测试结果照常以 surefire 报告为准。
- 四个演示账号：4 个种子 × 连续 4 周（累计历史、不重复配对）全部拿到匹配。

## 3. 给服务层的接口

### 3.1 周批次（每个 `MATCH_TICK`）

```java
LocalDate week = WeekCalendar.weekStartFor(now);                       // 最近一个 ≤ now+1min 的周三
Optional<WeekStep> step = WeekMachine.nextStep(status);                 // status = WeekStatus(state, failedStep)
boolean due = WeekCalendar.isDue(week, status, step, now);              // 到点且轮到它
WeekMachine.Decision d = WeekMachine.begin(status, step);              // RUN / ALREADY_DONE，乱序抛 WeekRuleException
WeekStatus ok   = WeekMachine.succeed(status, step);                    // CLOSE 用 succeed(status, CLOSE, allMatchesTerminal)
WeekStatus fail = WeekMachine.fail(status, step);
Instant at = WeekCalendar.scheduledAt(week, step);                      // 周二 23:59 / 周三 00:10 / 00:30 / 19:00 / 下周二 23:00
```

求解一步：

```java
Optional<PoolGate.ExcludeReason> why = PoolGate.check(applicant, week, now);   // 写 match_run_member.exclude_reason
MatchingInput in = new MatchingInput(week, candidates, pastPairs, reportedPairs, Weights.W1, seed, manualRun);
MatchingResult r = new MatchingSolver().solve(in);                              // 默认 Blossom + 30 秒超时
r.pairs();      // List<MatchedPair(low, high, ScoreBreakdown)>，按 (low, high) 排序
r.unmatched();  // 成年但没配上的人 → empty_week_streak + 1
r.stats();      // algoVersion、weightsVersion、各硬约束过滤数、门槛下数、边数、求解器、兜底原因、耗时
```

每对之后：

```java
List<Slot> slots = SlotRules.propose(Deadlines.respondDue(publishAt), venueOpen);  // 3 个，slot_0..2
MatchSnapshot draft = MatchSnapshot.draft(low, high, slots);
// 发布时：
Transition t = MatchMachine.apply(draft, new MatchEvent.Publish(Deadlines.respondDue(now)), now);
```

### 3.2 匹配状态机

```java
Transition MatchMachine.apply(MatchSnapshot m, MatchEvent e, Instant now)   // 抛 MatchRuleException(code(), detail)
// Transition(from, after, effects, changed, rule)；changed=false 表示重放，什么都不用写
Set<Class<? extends MatchEvent>> MatchMachine.allowedEvents(MatchState s)
List<MatchMachine.Rule> MatchMachine.rules()
```

事件：`Publish(respondDue)`、`Decline(userId)`、`SubmitSlots(userId, slotIds)`、`CheckIn(userId)`、`Cancel(userId)`、`SubmitFeedback(userId, FeedbackAnswer)`、`Report(userId, ReportCategory)`；截止事件 `RespondDeadline`、`ReselectDeadline`、`DateEnded`、`FeedbackDeadline` 由定时任务在到点后发。

副作用由服务层落库：`RecordNoResponse`、`RecordLateCancel`、`RecordNoShow`、`RecordDispute`、`UnlockContacts`、`CreateReport(… urgent())`、`SuspendPendingReview`。处罚额度用 `Penalties.forNoShows(noShows, lateCancels, now)`，自动暂停用 `Penalties.shouldAutoPause(weeks)`。

错误码（`MatchError`）对应第 6 节：`MATCH_STATE`、`MATCH_NOT_FOUND`、`SLOTS_EMPTY`、`SLOT_UNKNOWN`、`CHECKIN_WINDOW`、`FEEDBACK_EXISTS`、`CATEGORY_UNKNOWN`、`DEADLINE_NOT_REACHED`（最后一个只给定时任务看，不该出现在接口上）。

### 3.3 阶段投影

```java
Stage s = StageProjector.project(new StageProjector.Input(viewerId, matchOrNull, weekPublished, participation, now));
s.wire();                          // 前端 MatchStage 字符串
s.legacyWire();                    // 老前端：noOverlap→closed，paused/suspended→waiting
StageProjector.contactVisible(s);  // 只有 mutual 为 true
```

### 3.4 时间规则（全部上海时间）

| 常量 / 方法 | 值 |
|---|---|
| `Deadlines.respondDue(publishedAt)` | 发布后至少 12 小时的第一个周四 22:00 |
| `SlotRules.propose(respondDue[, venueOpen])` | respondDue 之后的周五 18:30、周六 15:30、周日 12:00；场地不开时顺延到周一/周二 12:00、15:30、18:30；最多 3 个，按时间编号 |
| `SlotRules.pickConfirmed(proposed, a, b, now)` | 双方交集里最早的、离 now ≥ 6 小时的一个 |
| `Deadlines.reselectDue(respondDue, slots)` | min(respondDue 之后的周五 12:00, 最早时段 − 6 小时) |
| `Deadlines.dateDayOpens(start)` | start − 2 小时（`dateDay` 阶段和签到窗口开始） |
| `Deadlines.isLateCancel(start, now)` | 距开始 ≤ 6 小时 |
| `Deadlines.feedbackDue(slotEnd)` | slotEnd + 72 小时 |
| 举报 | 关闭后 7 天内仍可举报 |

### 3.5 文案

```java
CopyContext ctx = CopyContext.of(lowFacts, highFacts, venueOptions, otherNicknamesInBatch);
String json = CopyValidator.extractJson(modelOutput);        // 去 ``` 围栏、截到第一个 { 和最后一个 }
List<CopyValidator.Issue> issues = CopyValidator.validate(copy, ctx);   // 空 = 通过
String retryHint = CopyValidator.summary(issues);            // 拼进重试提示
MatchCopy fallback = TemplateCopy.build(lowFacts, highFacts, venueOptions, Lang.ZH);   // 一定通过校验
```

文本里用 `{{partner}}` / `{{me}}` 占位、代词用 “ta”，读取时由服务层替换；长度按占位符 = 3 个字计。

## 4. 合成人群夹具 `users-v1.json`

生成器在测试代码里（`matching/synthetic/SyntheticPopulation`，固定种子 20261118），夹具提交进 `aiditto-app` 资源目录；`SyntheticFixtureTest` 要求文件和生成器输出逐字节一致，改生成器必须重新生成。

- 200 人：前 3 个是 `mock.ts` 的林夏、周屿、许沐（`handle` 与前端 id 一致，卡片文案照抄），接着 4 个演示账号，其余 193 人随机生成。
- 北京 130 / 上海 70；男 92、女 92、非二元 12、其他 4；约 80% 学生；只要异性约 76%、只要同性约 7%、都可以约 16%，其余组合；年龄 18–26（按参考周 2026-11-18 计算）。
- 学校全部虚构：北辰大学、清河理工大学、海淀传媒学院、西山师范大学、京北财经学院、江湾大学、杨浦理工学院、长风师范大学。测试会检查夹具里不出现任何真实高校名。
- `answers` 用前端题目 id 和选项 id（可以直接走问卷那一套投影）；`card` 是前端 `Partner` 需要的展示字段（中英双语、`avatar`、`scenes`、`headline`、`blurb`、`wechat`）；30% 的人有一句雷区，对应的结构化约束放在 `aiConstraints`（加载时可直接置 `ai_status=ok`）。
- 微信号都是 `xxx_demo_1234`；学生邮箱是 `handle@demo.<学校代号>.edu.cn`，工作的人是 `handle@example.com`——加载时不要给合成用户发邮件。
- 演示账号：`13800000001` 男·想认识女生·北辰（`pku` 区域）·学生；`13800000002` 女·想认识男生·清河理工（`tsinghua`）·学生；`13800000003` 非二元·都可以·五道口·工作；`13800000004` 女·想认识男生·江湾大学（上海 `fudan` 区域）·学生。四个都选 “faster”、年龄范围宽、预算 “都可以”、爱好取最常见的三项，保证每周都能配上。

## 5. 和技术方案的出入（§5 / §7 / §8.6）

1. **公平加分只进目标函数，不进门槛和展示分数。** `score()` 不含加分，`objective()` 含。否则连续空周的人会被硬塞进低于自己风格门槛的配对。
2. **新增硬约束 “年级跨度 ≤ 3”**（PRD §5.2）；只对年级已知、不是 “其他” 的学生生效。
3. **交集里离现在不足 6 小时的时段视为没有交集**，走 `no_overlap`，而不是确认一个来不及的时间。
4. **`reselectDue` 以 respondDue 为锚**（之后的周五 12:00），不按日历周算；`respondDue` 是 “发布后至少 12 小时的第一个周四 22:00”。这样手动 “立即匹配” 在周四晚上跑也有合理的截止。
5. **截止后用户操作一律拒绝**（`MATCH_STATE`），哪怕截止事件还没被定时任务发出。
6. **关闭后 7 天内仍可举报**：状态保持 `closed`，记 `reporterId`，举报人看到 `reported`，对方看到 `closed`，互相喜欢的微信也随之隐藏。
7. **反馈判定表补全了方案没写的组合**：见过/没填 → `not_mutual`；见过/我没去 → `disputed`；我没去/没填 → `no_show`（自己记一次）；双方都说自己没去 → `no_show`（双方各记一次）。
8. **签到是一个事件（`CheckIn`）**，只写本方 `checkedInAt`，不改状态。
9. **`Publish` 事件带上 respondDue**，状态机不自己算日历。
10. **定时批次也排除 “有未结束匹配” 的人**（`PoolGate`，方案 §5.5 的意思），手动批次另外在硬约束里再查一次。
11. 跟 PRD 不一致的地方按技术方案走：反馈在 `slot_end` 打开（PRD 写 +1 小时）；确认至少提前 6 小时（PRD 写 24 小时）；重选截止周五 12:00（PRD 写 20:00）。
12. **异常类型没放进 `common/`**：`MatchRuleException(MatchError)`、`WeekRuleException` 在各自包里，等 `common.DomainException` 定型后再合并。
13. 夹具：方案说 “工作的人 23–32 岁”、生成器放在 `aiditto-eval` 命令行里、`firstMessage` 用大模型写；这里按本次要求年龄统一 18–26、生成器是测试代码、`firstMessage` 用短语模板生成（不调模型）。演示账号多了一个 `13800000004`（上海），用来覆盖第二个城市池。
14. 合成用户的 `location.placeId` 用 `places.ts` 里已有的区域 id（`pku`、`tsinghua`、`fudan`…），所以前端位置那一栏会显示真实地名，学校名本身是虚构的。坐标见 `matching/TestPlaces`，`place` 表需要有这些区域的经纬度。
15. 顺手修了 `TemplateCopy` 一个问题：中文 “节奏相同” 理由的依据写的是题目原文「希望怎么帮你匹配」，不在已知词里，会被自检静默丢掉；改为引用选项文字。

## 6. 待定

- `common/` 里的 `DomainException` / 错误码定型后，把 `MatchRuleException`、`WeekRuleException` 合并过去，并核对错误码命名。
- `place` 表的经纬度由谁维护（`Commute` 需要 `lat/lng`，夹具测试暂用 `TestPlaces` 里的近似坐标）。
- 种子从哪来：建议 `seed = hash(weekStart)`，手动批次另取，写进 `match_run`。
- 演示 “固定搭档”（林夏等）也在普通池里，会被别人正常匹配到；如果演示时不希望这样，加载时给它们 `paused_this_week` 或单独的池子。
- 夹具里学生邮箱用的是虚构的 `.edu.cn` 子域，加载流程要确认不会对合成用户发验证码。
