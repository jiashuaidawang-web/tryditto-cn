# V4-S01 实现说明

分支 `master_agent_v4`。审计提交是 `77d2a2b9b4ab3a1495e549ddd3cdaf78785b54ff`。本文件随发布闸门修复一起提交。

提交说明：

```
fix: enforce matching consent and recheck eligibility before publishing
```

## 行为

- 新增 `matching_consent`。没有行 = UNKNOWN，不回填 GRANTED。历史用户不会被自动授权。
- V4 合规模式默认 fail-closed。`ConsentPolicy.allows` 只有 GRANTED 返回 true。
- `legacyAllowUnknown` 只放行 UNKNOWN。REVOKED 在参数为 true 时仍然 false。这个开关不属于合规验收。
- `EligibilityGate` 复用 `PoolGate` 和 `HardFilter`。
- `WeekRunner.publish` 逐场加锁重读。不合格的 draft 用 `MatchEvent.Hold` 进入 `CANCELLED`，不发通知。
- `MatchDb.publishDrafts` 改为抛 `IllegalStateException`。
- 同意、拉黑、停用、注销、入驻后的画像修改，在同一事务里锁用户并 Hold 不再合格的未结束场次。
- 管理匹配、手动匹配和演示发布都走 `PublishGuard`。
- 卡片不展示未发布的场次，也不展示系统 Hold 的对方。错误体只有 `MATCH_STATE`。

`Scorer`、`BlossomMatcher`、`AiJudge`、召回、反馈学习策略没有改。`Hold` 从 draft / published / no_overlap / confirmed / feedback_open 到 cancelled，无 effect。终态上的第二次 Hold 是重放。

## 文件

领域：

- `api/aiditto-domain/src/main/java/cn/aiditto/domain/consent/ConsentStatus.java`
- `api/aiditto-domain/src/main/java/cn/aiditto/domain/consent/MatchingConsent.java`
- `api/aiditto-domain/src/main/java/cn/aiditto/domain/consent/ConsentPolicy.java`
- `api/aiditto-domain/src/main/java/cn/aiditto/domain/matching/EligibilityGate.java`
- `MatchEvent.Hold`、`MatchMachine` 增加 hold 行

应用：

- `api/aiditto-app/src/main/resources/db/migration/V13__matching_consent.sql`
- `cn.aiditto.consent`：`MatchConsentProperties`、`MatchingConsentStore`、`MatchingConsentService`、`MatchingConsentController`
- `cn.aiditto.match.PublishGuard`、`EligibilityWithheld`
- `WeekRunner`、`MatchFlow`、`MatchDb`、`People`、`MatchCard`
- `AccountService`、`BlockService`、`OnboardingService`
- `application.yml` 的 `aiditto.match.consent.legacy-allow-unknown`，默认 false

测试：

- `ConsentPolicyTest`、`EligibilityGateTest`
- `MatchMachineTest`、`MatchMachinePropertiesTest` 覆盖 Hold
- `PublishSafetyIT`：合规模式。需要独立 PostgreSQL，本环境未跑，见测试报告
- `ConsentLegacyIT`：逃生口打开时 REVOKED 仍不能发布。同样未跑
- `TestConsents.grantAll`：`MatchIT`、`AiCopyIT`、`FeedbackLearningIT`、`AgentRerankIT` 在发布前显式授予合成用户。加载器不会自动授权

## 迁移与启用

Flyway `V13` 只建表、给 `match` 加可空的 `hold_reason`。没有 INSERT。

重新确认：列出没有 `matching_consent` 行的用户，让本人 `POST /api/me/matching-consent`。不要 UPDATE 成 granted。

本轮没有改 `application-prod.yml`，没有改 `deploy/`，没有部署。生产打开 `MATCH_CONSENT_LEGACY_ALLOW_UNKNOWN` 之前要人工审批，打开之后的运行不算 V4 合规。

## 回滚

1. 保持逃生口关闭。不要用旧的 `MATCH_CONSENT_ENFORCE=false`，那个键已经不再绑定。
2. 保留 `PublishGuard` 和「`publishDrafts` 不可用」。
3. 表可以留着。不要把代码退回 `c74921b` 来关闸门。

## 锁

发布一批：周行锁 → 本批用户 id 升序 → 每场 match id 升序 → 重读 → 写。

撤回 / 拉黑 / 注销 / 改画像：该用户行锁 → 写事实 → 该用户未结束 match 按 id 升序 → 不合格则 Hold。不锁对方。

同一周两个发布任务被周行锁串行，后一个 `ALREADY_DONE`。
