# V4-P01 最终验收修复

日期：2026-11-26。分支 `master_agent_v4`。对照的实现提交是 `76468ce775a004c821e116bb07919531920f1efc`。

阶段性结论保持不变：领域测试、PostgreSQL 集成测试和当时的两次 Actions 已通过，匹配算法和 S01 发布闸门没有改。最终验收当时是 NO-GO。本轮只修审计指出的 2 个 P0 和 3 个 P1。不启动 V4-P02，不接真实 LLM，不改匹配算法，不合并 V3，不部署。

本机领域测试：`cd api && mvn -B -pl aiditto-domain test`，`Tests run: 482, Failures: 0, Errors: 0, Skipped: 0`。原来的 477 项仍在，新增 `ProfileAuditTest` 5 项。200 人仿真仍是 83 对、0 条硬约束违规。本机没有 PostgreSQL。集成测试的实际执行以本轮 GitHub Actions 为准，运行 URL 写在 `V4-P01-test-report.md`。没有把旧运行的 `PostgreSQL 16.16` 写成这次的新实测。

## P01-R01 源版本水位

优先级：P0。

源码证据：`76468ce` 的 `ProfileVersionPolicy.decide` 在哈希与当前行相同时直接返回 `KEEP_CURRENT`，返回值里没有水位。比较用的是当前快照行的 `source_profile_version`，不是已经处理过的最高源版本。`uq_rel_profile_active_hash` 把「未撤回」当成「仍在生效」，所以 A→B→A 会被 `REJECT_DUPLICATE` 永久挡住。调用方传入的源版本没有和 `profile.version` 对过。

最小复现：

1. 源版本 1，内容 A，保存成功。
2. 源版本 5，内容 A。旧逻辑保持当前行，但不记录已经看过 5。
3. 迟到的源版本 3，内容 B。旧逻辑只看到当前行上的源版本 1，3 大于 1，于是写入 B。

修复：`V15__relationship_profile_lifecycle.sql` 增加 `latest_processed_source_version`。V14 不改。相同哈希只推进水位，不新插快照。更低的源版本在比较内容之前就拒绝。声明的源版本必须等于用户行锁内读到的 `profile.version`。把 `profile.version` 改回更小的数，仍然过不了水位。确定性投影在同一源版本上改变内容是 `REJECT_CONFLICT`。`WriteMode.MODEL_REVISION` 在领域策略里是另一种决定，`saveModelRevision` 本轮直接拒绝。内容从 A 到 B 再回到 A 时，旧行写 `superseded_at`，新行可以再次使用 A 的哈希。两个同时生效的相同哈希仍被 `uq_rel_profile_live_hash` 拒绝。

测试：

- `ProfileAuditTest.sameHashAdvancesTheWatermarkAndADelayedSourceIsStale`
- `ProfileAuditTest.returningToAnOlderHashIsNotRejectedAsADuplicate`
- `ProfileAuditTest.deterministicConflictIsNotAModelRevision`
- `RelationshipProfileTest` 里原有的版本策略断言已改成水位语义
- `RelationshipProfileIT.delayedSourceCannotReplaceAHigherWatermark`
- `RelationshipProfileIT.sameInputDoesNotCreateAnotherVersion`
- `RelationshipProfileIT.returningToAnEarlierHashCreatesAnotherVersion`
- `RelationshipProfileIT.claimedSourceMustMatchTheStoredProfileVersion`
- `RelationshipProfileIT.sameSourceDifferentContentIsADeterministicConflict`
- `RelationshipProfileIT.modelRevisionIsRefused`
- `RelationshipProfileIT.uniqueCurrentAndHashConstraints` 仍断言第二行相同有效哈希失败，以及第二个 `is_current` 失败

CI：见测试报告中的本轮运行。本机没有执行这些 IT。

剩余限制：水位存在生命周期行上。账户注销还不会调用 `erase`，所以生产开关保持关闭。模型修订只有策略区分，没有写入实现。

## P01-R02 撤回与旧任务

优先级：P0。

源码证据：`76468ce` 的 `retractCurrent` 把 `is_current` 清掉。`save` 在没有当前行时走首次插入。迟到任务可以在用户撤回之后把画像写回来。`AccountService` 只把状态改成 `deleting` 并写下 `purge_at`，不调用画像删除。

最小复现：

1. 画像任务拿到代次并开始。
2. 用户撤回处理授权，当前行被清掉。
3. 旧任务带着原来的代次完成。旧逻辑看到没有当前行，插入新版本。

修复：生命周期行单独记录处理授权和 `generation_epoch`。三种授权不混用。`matching_consent` 不在画像服务里读取。`grantModelExport` 只改模型外发，不能让 `save` 成功。`revokeProcessing` 把处理授权改成 `revoked`、代次加一，并给当前行写 `retracted_at`。`retractCurrent` 也把代次加一，所以旧任务不能靠「当前行没了」恢复。新的 `grantProcessing` 给出新代次后才能写。`erase` 删快照、撤回处理授权并增加代次；注销中的 `current()` 和 `save` 抛 `ACCOUNT_INACTIVE`。写入在 `UserLock` 里做。并发用测试线程模拟，没有真实异步模型任务。

测试：

- `RelationshipProfileIT.revokedGenerationCannotWriteAndANewGenerationCan`
- `RelationshipProfileIT.matchingConsentAndModelExportDoNotGrantProcessing`
- `RelationshipProfileIT.retractCurrentBlocksTheOldGeneration`
- `RelationshipProfileIT.eraseRacesSaveAndLeavesNoReadableProfile`
- `RelationshipProfileIT.concurrentSavesKeepASingleCurrentRow`
- `RelationshipProfileIT.deletingUserIsRefusedAndEraseRemovesRows` 增加了注销状态下 `current()` 被拒绝

CI：见测试报告中的本轮运行。

剩余限制：未集成。`AccountService` 不调用 `erase`。匹配同意的撤回不调用 `revokeProcessing`。没有用户界面会调用 `grantProcessing`。`aiditto.relationship-profile.processing-enabled` 在 `application.yml` 和 `application-prod.yml` 都是 false，生产文件不提供环境变量覆盖。这些事件接上之前，不能打开生产画像处理。

## P01-R03 证据可信来源

优先级：P1。

源码证据：`EvidenceIds.compute` 只把用户、来源和特征码编成稳定 id。旧的 `ProfileEvidence` 用 `sourceRef` 前缀判断是不是模拟对话。模型可以提交一个没有这些前缀的 id，构造器不会知道来源是否存在。

最小复现：目录里把一条来源标成 `simulated=true`，引用用 `meeting-19` 这种普通记号。只看前缀会放过。旧写入也接受调用方快照上的 `evidenceId` 和 `sourceType`。

修复：`EvidenceSourceDirectory.find` 是权威。`EvidenceTrust` 要求记录属于该用户、引用和版本一致、来源类型一致、范围是 `profile-self`、不是模拟、已撤回的来源不能支撑当前特征，并且注册表允许这个来源支撑该特征。`AnswerEvidenceDirectory` 只读 `RelationshipProfileProjector.STORED_QUESTIONS` 里的 `onboarding_answer`。`save` 用服务端投影，提交的哈希必须一致。反馈和 LLM 来源本轮没有目录，信任检查失败。前缀检查留在构造器里，只是后盾。

测试：

- `ProfileAuditTest.simulatedFeedbackNeedsNoSpecialPrefixAndScopesStaySeparate`
- `ProfileAuditTest.forgedHashCanonicalAndForeignEvidenceAreRejected`
- `RelationshipProfileIT.offeredSnapshotMustMatchTheServerProjection`
- `RelationshipProfileIT.matchingConsentAndModelExportDoNotGrantProcessing`

CI：领域测试已在本机通过。PostgreSQL 上的两个 IT 见本轮 Actions。

剩余限制：真实反馈事件和 LLM 提取没有接入。问卷目录把现存答案视为未撤回、未模拟的直接答案。这是测试和当前问卷存储能支持的范围，不是一条通用的证据总线。

## P01-R04 快照完整性

优先级：P1。

源码证据：`RelationshipProfileSnapshot` 和 `ProfileEvidence` 仍是公开 record。`76468ce` 的服务把调用方给的快照 JSON 直接插入。构造器检查哈希长度，不检查哈希是不是这份 canonical 的 SHA-256。

最小复现：先做出一份合法快照，再用 record 构造器换成另一个 64 位哈希或改掉 canonical。旧的 `save` 会把这行写进去。

修复：record 保留。`SnapshotAdmission.admit` 是持久化边界。它检查 schema 1 和 `features-1`，对每条证据做 `EvidenceTrust`，再用证据重建快照，要求 canonical、哈希和四节一致，然后拒绝任何 `FeatureValue.Text` 和不在白名单里的受控引用。服务保存的是通过边界的服务端快照。`IllegalArgumentException` 变成校验错误。

测试：

- `ProfileAuditTest.forgedHashCanonicalAndForeignEvidenceAreRejected`
- `RelationshipProfileIT.offeredSnapshotMustMatchTheServerProjection`
- `RelationshipProfileIT.persistsAndReadsTheSnapshot`

CI：领域测试已在本机通过。写入边界的 IT 见本轮 Actions。

剩余限制：内存中的虚构案例仍可直接构造，包括自由文本。它们不是持久化路径。案例 JSON 没有改。

## P01-R05 隐私

优先级：P1。

源码证据：`SensitiveText` 只有邮箱和中国大陆手机号两类正则。旧投影把自定义爱好和开场白原文放进快照。`摄影`、证件号、学号、街道和私人 URL 都不是这两类正则能决定的。

最小复现：问卷里同时放手机号、邮箱、身份证号、学号、街道、私人 URL、自定义爱好「摄影」和开场白。旧 canonical 含「摄影」和开场白原文。

修复：普通快照的受控引用白名单是 `profile.expected_genders_enc`、`onboarding_answer.occupation`、`onboarding_answer.first_message`。自定义爱好不进入快照。职业和开场白只留固定引用。`sourceRef`、`sourceVersion`、`consentScope` 必须是短记号，连续 6 位以上数字会被拒绝。投影不解密 `expectedGender`，密封值变成引用，不把 `__sealed__` 写进 canonical。`TasteFacts` 没有改。文档不声称正则能消除私人信息。

测试：

- `RelationshipProfileTest.projectorKeepsSensitiveValuesOutAndDoesNotInfer`
- `ProfileAuditTest.simulatedFeedbackNeedsNoSpecialPrefixAndScopesStaySeparate` 里的不安全 `sourceRef`
- `RelationshipProfileIT.persistsAndReadsTheSnapshot` 仍检查库里的 JSON 不含姓名、邮箱、地点和手机号

CI：领域测试已在本机通过。使用的是合成字符串，不是真实个人信息。

剩余限制：正则仍留在 `FeatureRegistry` 的普通文本兜底上，只覆盖它能认出的邮箱和手机号。自由文本的安全做法是不复制。问卷原表里的原文还在 `onboarding_answer`，快照只留引用。密封的性别期望不会在投影时被解密来识别「不限」。

## 生产开关

`RelationshipProfileService` 的默认值是 false。`application-prod.yml` 写死 false。IT 用 `@DynamicPropertySource` 打开。`erase` 在开关关闭时仍可删除，供以后的清除任务调用。现在没有调用方，所以这不构成生产处理。
