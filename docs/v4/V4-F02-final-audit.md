# V4-F02-FINAL 收口审计

F02 的确认事务原先自己插入 `relationship_profile_version`，并且只从 `profile_learning_confirmation` 重建证据。这一轮把它收口到 `RelationshipProfileService`。没有新建第二套画像生命周期，没有改 V14–V20，没有调用模型，也没有启用生产学习。

## R01 完整画像保留

`ProfileLearningStore.loadEvidence` 现在先投影当前问卷答案，再并上确认表里的证据。`RelationshipProfileService.save` 和 `commitProvisional` 在写入前调用同一段合并。问卷里的其他特征、已经确认且未被这次修改的偏好，以及仍有来源的证据留在新快照里。只撤回或替换当前这次确认对应的特征。

`source_profile_version` 写的是 `profile.version`。快照版本由 `MAX(version)+1` 分配，不再把快照版本当成问卷源版本。

## R02 统一写入

确认前先取咨询锁 `pg_advisory_xact_lock(11, user)`，再由 `lockLearning` 锁用户行。代次、处理授权和账号状态从数据库读。命令里的授权布尔值不能单独放行。命令里的代次只作为用户看到的期望值，当前代次以生命周期表为准。

同一问卷源版本上的学习修订使用 `WriteMode.LEARNING_REVISION`。它允许内容变化，水位停在问卷源版本，不把这次修订记成新的问卷版本。画像行和 `PROFILE_LEARNING` 消费凭证仍在同一个事务里提交。

账号删除仍不从 `AccountService.requestDelete` 调用。统一约定写在 `RelationshipFeedbackStore.FUTURE_ACCOUNT_DELETION` 和 `RelationshipProfileService.FUTURE_ACCOUNT_DELETION`：先锁用户，再 `eraseActor`，再 `RelationshipProfileService.erase`，提交时不跑学习器，也不恢复旧快照。

## R03 确认绑定

直接修改走用户自己提交的命令。确认已有提案时读取 `profile_learning_proposal` 的用户、特征、旧值、新值、来源、版本、代次和原来的 `expires_at`。不允许用更晚的确认时间重造提案来延长有效期。

同一个 `commandId` 如果载荷不同，返回 `IDEMPOTENCY_CONFLICT`，不当成幂等成功。`LearningActor` 必须等于命令里的用户。另一个合法参与者不能代替他确认。生产会话构造 `LearningActor` 会得到 `PRODUCTION_ACTOR_NOT_WIRED`。集成测试使用 `synthetic-test`。

## R04 派生证据撤回

`evidence_origin` 在 V21 区分 `DIRECT` 和 `DERIVED`。用户直接写下的值在来源撤回后保留。只依赖已撤回事件的派生确认会被标成 `RETRACTED`，当前快照按剩余证据重写，不把整份历史快照拷回来，也不覆盖后来的合法修改。

相关事件的 `PROFILE_LEARNING` 消费凭证改成 `REVOKED`。其他事件的凭证不动。待确认且引用来源已被撤回的提案改成 `RETRACTED`。之后的 `expireDue` 和再次 `confirmStored` 不能把这条派生证据恢复成当前事实。

## R05 重复反馈

`FeedbackSignalClassifier.observe` 在特征存在相反方向时把证据状态写成 `NOT_USABLE`。矛盾假设可以留下 `CONTRADICTED` 的待确认提案，但不能被当成可用事实，也不能进入匹配。

带账本的 `accepted` 只收下真实事件：事件存在、行动者是本人、方向与匹配中的位置一致、发生时间不晚于现在、落在 30 天窗口内、没有被取代、学习授权仍在。同一 `eventId` 只计一次。不存在的编号、已撤回事件、窗口外事件和未来时间都不计。重复次数 3 和窗口 30 天仍是未验证参数，没有写入评分权重。

72 条冻结合成案例没有改标签。`LearningEvalTest` 仍对照 `docs/v4/f02-learning-cases.json`。

## 测试

本地领域测试 587 项，失败 0，异常 0，跳过 0。相对 F02 的 585 项，新增 2 项在 `ProfileLearningEngineTest`。`ProfileLearningIT` 新增 6 个 PostgreSQL 方法，方法名已写入 `.github/workflows/v4-ci.yml`。本机没有 PostgreSQL，这 6 个方法在 CI 执行。

实现提交 `c74e9c1b5db1aa2c5c985da4a0984fd5521e0325`。CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46115262767 ，四个作业 success。

应用模块 `test-compile` 通过。这不是集成测试通过。

## 仍未接入

- 生产画像处理默认关闭。
- 生产反馈学习不自动运行。
- `AccountService.requestDelete` 仍不调用删除。
- `GROUP_EVALUATION` 仍不能构造。
- 真实用户确认质量和真实关系质量是 NOT_RUN。
