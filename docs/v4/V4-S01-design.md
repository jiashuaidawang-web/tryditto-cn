# V4-S01 设计：发布安全闸门与匹配同意

本轮只修发布安全。不改 Scorer、Blossom、AI Judge、召回或反馈学习策略，不把 RecallPipeline 接到周匹配，不部署，不改生产配置。

算法质量仍是 NO-GO。闸门不提高配对率，也不放宽硬约束。

## 两道闸门

1. **硬安全，始终生效。** 年龄、账户状态、双向拉黑、禁止配对，以及当前 `HardFilter` / `PoolGate`。`WeekRunner.publish` 不再调用 `MatchDb.publishDrafts`。该方法现在直接抛错，避免有人把它接回去。
2. **匹配同意，V4 合规模式是 fail-closed。** 只有 `GRANTED` 可以通过。`UNKNOWN` 和 `REVOKED` 都不能正式发布。注册、服务协议、入驻完成、AI 画像提取都不会授予。没有记录就是 `UNKNOWN`，不是 `GRANTED`。迁移不回填，历史用户不会被自动授权。

## 授权状态

| 状态 | V4 合规模式（默认） | `legacy-allow-unknown=true` |
| --- | --- | --- |
| GRANTED | 可以进入本周，也可以正式发布 | 可以 |
| UNKNOWN | 不可以。缺记录不是授权 | 可以。这是迁移逃生口，**不属于 V4 合规验收** |
| REVOKED | 不可以 | 不可以。没有任何开关能放行 |

配置是 `aiditto.match.consent.legacy-allow-unknown`，环境变量 `MATCH_CONSENT_LEGACY_ALLOW_UNKNOWN`，默认 `false`。旧的 `MATCH_CONSENT_ENFORCE=false` 不再放行 UNKNOWN。

生产环境打开逃生口之前必须单独人工审批。本轮不修改 `application-prod.yml`，不部署。

同意文案版本是 `match-consent-v1`。资格结果版本是 `eligibility-v1`。

## 历史用户重新确认

Flyway `V13` 只建表，不 INSERT，不把旧用户改成 GRANTED。

上线后、把流量切到 V4 合规模式之前：

1. 查出没有 `matching_consent` 行的用户。这就是待重新确认的人。
2. 由本人调用 `POST /api/me/matching-consent`。同一文案重复授予不涨版本。
3. 不要用 SQL 把历史行写成 `granted`。那会把「没表态」记成「已授权」。
4. `DELETE /api/me/matching-consent` 是撤回。从没授予过也可以撤回，撤回后即使逃生口打开也不能发布。
5. 逃生口只在迁移窗口、并经过人工审批后短期打开。打开期间的运行不能当作 V4 合规验收。窗口结束必须回到默认 fail-closed。

## 资格判定

`EligibilityGate` 在领域层。它调用 `PoolGate.check` 和 `HardFilter.violations`，不复制规则。

内部原因码不带用户 id，也不写是哪一方：`POOL_*`、`HARD_*`、`CONSENT_UNKNOWN`、`CONSENT_REVOKED`、`FORBIDDEN_PAIR`、`ACCOUNT_MISSING`、`PROFILE_INCOMPLETE`。

用户能看到的只有 `MATCH_STATE` / 「现在不能做这个操作」。原因写在 `match.hold_reason`，不进卡片，不进错误体，日志只打 `matchId`。

正在发布的这场 draft 已经在 `match` 表里。复核时把这场的 id 从历史配对和未结束匹配里去掉。别的历史配对、别的未结束匹配仍然算。

## 发布

普通周任务、`POST /api/admin/match-runs`、手动 `WeekRunner.runManual`、演示 `POST /api/demo/match/stage` 都进 `PublishGuard`。没有第二条发布路径。

`WeekRunner.publish` 在原来的周事务里：

1. 先把本批所有用户按 id 升序 `FOR UPDATE`。
2. 再按 match id 锁双方用户和这场 match。
3. 锁之后重新读同意、账户、拉黑、画像、禁止配对、举报。事务外查一次不算数。
4. 通过则 `MatchEvent.Publish`。不通过则 `MatchEvent.Hold`：进入 `CANCELLED`，draft 的 `published_at` 仍为空，没有惩罚、没有举报、没有通知。
5. 周步骤照常进入 published。被停掉的场次是终态，不挡住以后的 close。

已经是 `PUBLISHED` 的再次发布直接返回，不改版本。

## 并发

锁顺序：用户 id 升序，然后 match id 升序。撤回、拉黑、注销、改硬约束的事务先锁操作者那一行，写完事实，再按 match id 锁这个人未结束的场次并 `Hold`。这条路径不锁对方，所以不会在已经持有较大 id 之后再去锁较小 id。

| 竞争 | 结果 |
| --- | --- |
| 撤回先提交，发布后执行 | 发布重读到 REVOKED，draft 被 Hold，`published_at` 为空 |
| 发布先提交，撤回后执行 | 同一撤回事务里 `releaseUser` 把已发布场次 Hold。卡片隐藏对方，联系方式接口不再返回微信 |
| 拉黑、注销、改硬约束与发布 | 谁后提交谁看到对方已提交的行。结束后该场不是 published |
| 两个发布打同一场 | match 行锁串行。后一个看到 `PUBLISHED`，版本只加一次 |
| 两个场次共用一个用户 | 两边都按用户 id 升序加锁，不应死锁 |
| 发布事务回滚 | 场次回到回滚前的 draft |

用户动作如果发现已经不合格，先 Hold 再抛 `EligibilityWithheld`。这些方法声明了 `noRollbackFor`，Hold 会提交。演示外层事务没有这个声明，失败的演示发布整笔回滚，不会留下 published。

## 回滚

1. 保持 `MATCH_CONSENT_LEGACY_ALLOW_UNKNOWN` 为 false。不要把它当成回滚「恢复旧发布」的开关。
2. 不要把 `publishDrafts` 接回 `WeekRunner.publish`，也不要去掉发布前的 `EligibilityGate`。
3. 可以留下 `matching_consent` 和 `hold_reason`。不要用退回 `c74921b` 的方式关闸门。

## 不在本轮

召回未进主链路、Top-15、评分尺度、`rankAgainst`、画像提取和反馈学习的锁，都还在。见 `docs/v4/risk-register.md`。不启动 V4-P01。
