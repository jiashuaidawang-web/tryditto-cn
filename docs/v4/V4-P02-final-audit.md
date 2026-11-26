# V4-P02 最终审计

阶段实现 `ef08182426d414c117d22ebf909baa02f0622ab1` 和文档 `b8a30b5e511e3da19fe1d6224fb559081d6b9065` 的 CI 已通过：https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45780763981。最终架构验收当时是 NO-GO。本轮只处理下面六项，不重构 P02，不进入 M01。

生产画像处理保持关闭。匹配算法没有改。没有部署，没有合并 V3。

本机领域测试：`cd api && mvn -B -pl aiditto-domain test`，492 项，0 失败，0 跳过。其中先前 488 项仍在，新增 `ProfileExtractionAuditTest` 3 项和 `ProfileLiveEvalTest` 1 项。200 人仿真：83 对，0 条硬约束违规。`ProfileExtractionParserTest` 2 项在本机通过，并已写入 GitHub Actions 的 Surefire 必跑清单。PostgreSQL 集成测试本机 NOT_RUN。代码提交 `3feeeae31d0a104236032589425e78732c4d4e53` 的运行是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45789587882 ，结论 success。

## P02-R01 证据语义

源码证据：阶段实现里，`ProfileExtractionValidator` 把任意 `SELF_STATEMENT` 当成任意 SELF 特征的支持，把任意 `PREFERENCE_STATEMENT` 当成任意 PREFERENCE 特征的支持。合法的 `ev-*` 只证明引用存在。

最小复现：用户说喜欢阅读，候选却是高社交主动性；用户说喜欢幽默的人，候选却是本人特征；用户说不喜欢频繁聊天，候选却是喜欢频繁聊天；用户引用朋友的偏好；用户使用「如果」；用户说「也许」却给出 `SUPER`。

修复：`SemanticSupport` 区分 `REFERENCE_VALID`、`SEMANTIC_SUPPORT_PENDING`、`SEMANTIC_SUPPORTED`、`USER_CONFIRMED`、`REJECTED`。只有 `SEMANTIC_SUPPORTED` 进入 `accepted()`。`StatementSemantics` 只接受整句封闭短语，并拒绝假设、转述、不确定、否定，以及短语所支持的另一个值。没有规则时保持挂起。问卷条目不能被模型引用成推断。`MatchingFeatureGate` 只允许已语义支持或用户确认的特征进入以后的正式匹配分。当前评分器不调用它。`USER_CONFIRMED` 本轮不写。

新增测试：`ProfileExtractionAuditTest.aLegalCitationDoesNotProveTheConclusion`。120 份工程评估仍由 `ProfileEvalTest` 守住，门槛没有下调。

剩余风险：这不是通用语言理解。挂起特征不会进入正式分，但也不会被自动证成。用户确认路径还没有产品入口。

## P02-R02 外部模型授权代次

源码证据：`grantModelExport` 和 `revokeModelExport` 原先只翻转 `model_export_authorization`。重新授权后，旧的在途结果仍可能按原来的许可写入。

最小复现：授予，启动任务，在模型调用期间撤回再重新授予，旧任务返回后仍可能写入。上下文建好之后、真正调用之前的撤回也要拦住。

修复：`V17` 给 `relationship_profile_lifecycle` 增加 `model_export_epoch`。从撤回变为授予，以及从授予变为撤回，各加一。重复的相同操作不加。处理授权首次写入时外发仍是撤回，代次从 1 开始，所以第一次授予后是 2。代次进入 `ExtractionPermit`、`ProfileContext` 和输入哈希。发送前重新读取账户、源版本、处理代次和外发代次。这一读是发送判定。模型调用期间不持有用户行锁。写入时要求当前代次等于这次尝试捕获的代次。已经送出的字节不能宣称被本地撤回，但旧代次不能写入。匹配同意、处理授权和外发授权仍然分开。`saveModelRevision` 仍拒绝。

新增测试：`oldExportEpochCannotWriteAfterRegrant`、`revokeBeforeSendDoesNotCallTheModel`。原有的调用期间撤回测试仍在，并断言代次上升、模型确实被调用了一次、画像哈希不变。

剩余风险：发送判定返回之后、请求字节离开本进程之前，仍有一段撤回看不见的窗口。提交会拒绝写入，但不能把已发送的数据说成未发送。

## P02-R03 任务租约与执行隔离

源码证据：`ExtractionTaskStore.tryStart` 没有 attempt、租约或 fence。`running` 可以一直占着。`complete` 不区分是哪一次执行结束。

最小复现：进程停在 running；旧执行者晚于新执行者返回；没有任务行就提交；已完成后再提交；完成更新失败却留下新画像；模型抛异常后任务仍是 running。

修复：不改 `V16`。`V17` 增加 `attempt_id`、`fence`、`lease_until`、`model_export_epoch`。新领取使用 fence 1 和约 30 秒租约。活租约是 busy。过期且次数小于 3 时换新 fence。次数达到 3 的过期 running 标成 discarded。`degraded` 和 `discarded` 在次数小于 3 时可以重试。完成、降级和丢弃都按调用方的 fence 更新。`commitProvisional` 在写入画像之后要求完成更新恰好一行，否则抛错并回滚同一事务。没有任务、fence 不对或任务已完成时不能留下新的模型画像。

新增测试：`expiredRunningAttemptIsReclaimedAndStaleFenceCannotWrite`、`exhaustedLeaseDoesNotStayRunning`、`missingOrCompletedTaskCannotCommit`、`modelExceptionDoesNotLeaveTheTaskRunning`。原有的重试、并发和降级测试仍在。

剩余风险：租约是 30 秒的恢复手段，不是分布式时钟证明。没有人来领取时，过期行要到下一次提取才被标掉。

## P02-R04 情景语义

源码证据：`ProfileInputLoader` 原先只读 `voice_answer.chosen_key`。校验器接受任意 `behavior.scenario.signal` 值。选项字母被当成行为。

最小复现：同一选项字母 A，一道题的批准含义是 low，另一道题是 high。只看字母会把两题收成同一个行为。

修复：不新增列。现有表是 `voice_question.id`、`prompt_version`、`scenario`、`options_json`，以及 `voice_answer.chosen_key`、`trait_signal`、`custom_text_enc`。加载时只连接当前用户的回答和题目，解析 `options_json`。所选 `text` 正好是 `low`、`steady` 或 `high` 才保留。服务端条目带有 `scenarioId`、`scenarioVersion`、`choiceKey`、`approvedMeaning`、`sourceReference`。数字主键不放进外发正文，证据引用仍是 `ev-*`。题面、trait 和自定义密文不外发。无法还原批准含义的题不参与提取。情景回答仍是 `SCENARIO_RESPONSE`。

新增测试：`ProfileExtractionAuditTest.theSameChoiceLetterDoesNotCarryAMeaning`，以及 `scenarioChoiceCanSupportASignalWithoutExportingThePrompt`。120 份工程集里的情景样本改为携带批准含义，不再在运行时把 A 映射成 high。

剩余风险：现有题库的选项原文大多不是 `low|steady|high`。那些题会被跳过，直到有单独的批准含义，而不是临时把 trait 映射过来。

## P02-R05 自由文本与隐私

源码证据：`extract(userId, statements)` 把调用方语句当成用户记录。`FreeTextPolicy` 用字母类别和长度放行，纯中文姓名可以通过。

最小复现：纯中文姓名、文字地址、工作单位、私人身份、另一位用户的语句、已撤回或已过期的语句、Prompt Injection。这些都不能因为「看起来像普通句子」就外发。

修复：生产 `ProfileContextBuilder.build` 忽略调用方语句。`productionExportsFreeText()` 为 false。评估集走 `buildEvaluation`，并跳过撤回和已过期语句。生产提取签名改为 `extract(long userId)`。`commitProvisional` 不再接收调用方语句，只重新加载服务端输入。字母检查只留在评估路径，并写明它不是匿名化。`JdbcLlmCallLogger` 仍只记录调用点、模型、token、耗时和状态。

新增测试：`ProfileExtractionAuditTest.productionContextDropsCallerTextThatLooksOrdinary`，以及 `unapprovedFreeTextAndSameLetterDoNotBecomeMeaning`。

剩余风险：仓库里没有一张受控的、带用户、版本和撤回状态的自由文本陈述表。本轮不虚构这张表。因此生产模型特征目前只能来自上面定义的批准情景含义。不能仅凭 Unicode 类别宣称没有私人信息。

## P02-R06 真实模型质量

120 份工程集和 `ProfileEvalThresholds` 保留。开发集和保留集的 Precision、Recall 仍是手工标签经过校验器后的 1.0，不是实时模型分数。

另建 `ProfileLiveEval`，版本 `live-profile-v1`，66 条独立虚构样本。数据集哈希 `4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c`。Prompt 版本 `profile-extract-v1`，Prompt 哈希 `bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195`。标签和哈希先冻结。模型没有生成评估答案，也没有给自己打分。

没有安全配置。`aiditto.llm.mode` 默认 mock，`LLM_PRIMARY_API_KEY` 为空。不读取生产资料，不提交密钥，也不用工程夹具的分数填实时表。因此没有第二个供应商的替换运行。

| 项 | 模型原始输出 | 服务端校验后 |
| --- | --- | --- |
| provider / model | NOT_RUN | NOT_RUN |
| Precision / Recall | NOT_RUN | NOT_RUN |
| SELF/PREFERENCE 混淆率 | NOT_RUN | NOT_RUN |
| 无证据推断率 | NOT_RUN | NOT_RUN |
| 非法硬约束接受 | NOT_RUN | NOT_RUN |
| 未授权外发 | NOT_RUN | NOT_RUN |
| 敏感信息泄露 | NOT_RUN | NOT_RUN |
| 结构化输出成功率 | NOT_RUN | NOT_RUN |
| P50 / P95、Token、费用 | NOT_RUN | NOT_RUN |

实验门槛：Precision 至少 0.90，Recall 至少 0.75，混淆率至多 0.03，无依据推断进入可用画像的比例至多 0.02，非法硬约束接受数、未授权外发数和可识别敏感泄露数都是 0。这是待确认的研发门槛，不是行业标准，也不是本次结果。

剩余风险：真实模型理解质量保持 NOT_RUN。阻塞就是没有非生产的真实密钥和 live 模式配置。

## CI

代码提交：`3feeeae31d0a104236032589425e78732c4d4e53`

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45789587882

运行结论 success。三个 job 都成功：`domain-tests` 从 09:11:00Z 到 09:11:51Z，`integration-tests` 从 09:11:01Z 到 09:13:47Z，`test-reports` 从 09:13:54Z 到 09:14:00Z。整次运行从 09:10:57Z 到 09:14:00Z。

`integration-tests` 里的「Run the guard and the publish-safety integration tests」和「Reject a run that did not execute the IT scenarios」都是 success。后者会在必需方法缺失、跳过、失败或报错时让 job 失败。因此这次 PostgreSQL 上实际执行并通过的包括原来的 37 项集成测试、`ProfileExtractionIT` 的 20 个方法、3 项 `TestDatabaseGuardTest`，以及 `ProfileExtractionParserTest` 的 2 个方法。没有出现在这份必跑清单里的测试不计数。

没有从 runner 日志抄 PostgreSQL 的 `version()`。工作流镜像是 `postgres:16`。

## 四项结论

1. 工程质量：GO。范围是这条提取链路的领域测试、解析器测试和这次 PostgreSQL 集成测试。
2. 真实模型理解质量：NOT_RUN。
3. 生产启用：NO-GO。
4. 算法质量：NO-GO。

停在这里，等待最终架构验收。
