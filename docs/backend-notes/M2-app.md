# M2 应用层：匹配接口、周批次、合成用户、演示面板

领域层（状态机、求解器、文案模板）已经在 `aiditto-domain`。这一层把它们接到 MySQL、HTTP 和前端。登录方式没变：手机号 + 固定验证码 `888888`。

## 1. 怎么跑

测试库和 M1 相同（`~/.aiditto-test.env`，隧道 `127.0.0.1:13306` / `aiditto_test`）。env 文件里的值可能带引号，读的时候要去掉引号，否则 JDBC URL 不是以 `jdbc` 开头。

```bash
cd api
python3 - << 'PY'
import os, subprocess
env = os.environ.copy()
with open(os.path.expanduser("~/.aiditto-test.env")) as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        k, v = line.split("=", 1)
        v = v.strip()
        if len(v) >= 2 and v[0] == v[-1] and v[0] in "\"'": v = v[1:-1]
        env[k] = v
subprocess.check_call(["mvn", "-o", "verify"], env=env)
PY
```

`mvn -o verify` 会 Flyway clean `aiditto_test`。只能指向这个库。

前端：

```bash
cd web
npm run build
VITE_API=mock npm run build
```

端到端（jar 和 `npm run dev` 都起着，截图在 `compare/ours/e2e-match/`）：

```bash
cd api
# 环境变量同上，另外：
#   ADMIN_PHONES=13800000001
#   SCHEDULER_ENABLED=false          # 避免定时批次和手动「立即匹配」抢同一周
#   SYNTHETIC_AUTOLOAD 默认 true，演示模式启动时会灌入 200 个虚构用户
DB_URL="$TEST_DB_URL" DB_USERNAME="$TEST_DB_USER" DB_PASSWORD="$TEST_DB_PASSWORD" \
  ADMIN_PHONES=13800000001 SCHEDULER_ENABLED=false \
  java -Xmx640m -jar aiditto-app/target/aiditto-api.jar

cd web && npm run dev                # :5173，/api 代理到 :8080
node scripts/e2e-match.mjs
```

这次实测把 `SYNTHETIC_AUTOLOAD=false`，由脚本自己 `POST /api/admin/synthetic/load`。脚本开头还会 `POST /api/admin/demo/reset`，只删双方都不是真实用户的匹配，避免测试库里残留的演示匹配挡住新的一周。隧道上单次接口大约要数秒，脚本里的等待按这个放宽了。

### 实测（2026-11-13）

- `mvn -o verify`：领域层 382，集成测试 22，合计 404，失败 0。原先约 397，多出来的是 cohort / 人生阶段硬约束、合成用户隔离、MatchIT。
- MatchIT 里两城并行的「立即匹配」：**8139 ms**，0 条硬约束违反，4 个演示号都有匹配。同一条断言在端到端里再跑了一次，**6999 ms**（北京 4 对 / 140 人，上海 1 对 / 70 人）。
- `node scripts/e2e-match.mjs` 退出码 0：真实用户只看到真实对象；演示号 01 选时段 → 对方确认 → 签到 → 双方都想再见 → 微信号；02 拒绝；03 时段不重叠；04 举报。生日行是 `DIV`。
- `npm run build` 和 `VITE_API=mock npm run build` 都通过。

## 2. 迁移 V5

不改 V1–V4。`V5__create_week_match.sql`：

- `user` 增加 `cohort`（`real` / `demo` / `synthetic`，默认 `real`）、`late_cancel_count`、`no_response_streak`、`dispute_count`。已有用户回填为 `real`。
- 新表：`week`、`week_participant`、`match`、`match_side`、`date_plan`、`match_copy`、`feedback`、`report`、`match_demo`、`job_lock`、`admin_audit`。无外键。
- 批次触发列叫 `trigger_kind`。`trigger` 是 MySQL 保留字。

## 3. 匹配规则（相对技术方案的决定）

- 学生只配学生，非学生只配非学生。这是硬约束 `LIFE_STAGE`，不是加分项。
- 人群：`REAL` 只配 `REAL`；`DEMO` 可以配 `DEMO` 或 `SYNTHETIC`；`SYNTHETIC` 不能配 `SYNTHETIC`，也不能配 `REAL`。写在 `HardFilter` 和 `MatchingInvariants` 里，各有测试。合成用户不会出现在真实用户的卡片、列表或配对结果里。
- 四个演示号 `13800000001`–`04` 是 `demo`，不是 synthetic。200 个虚构用户（含林夏 / 周屿 / 许沐）是 `synthetic`。虚构手机号是 `137` 开头，邮箱是 `@demo.*.edu.cn` 或 `@example.com`。
- 生日在入驻完成后锁定。服务端本来就会拒绝修改；资料编辑页不再提供生日入口。
- 文案用 `TemplateCopy`，`source=template`，理由的 `ai` 为 false。这一期没有大模型。
- 演示重置只删「双方都不是 real」的匹配，并锁定演示用的个人周。真实用户的答案、资料、匹配不动。

## 4. 接口

都要登录。匹配 id 是字符串（雪花 id）。

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/matches/current` | 当前匹配卡片。没有匹配时 `partner` 为 null，阶段是 `waiting` 或 `emptyWeek` |
| POST | `/api/matches/{id}/accept` | 点「我想见」之前的接受 |
| POST | `/api/matches/{id}/slots/confirm` | `{slotIds}` |
| POST | `/api/matches/{id}/decline` | `{reasons, note}` |
| POST | `/api/matches/{id}/reasons/{index}/flag` | 某条理由不准 |
| POST | `/api/matches/{id}/checkin` | 签到窗口内 |
| POST | `/api/matches/{id}/cancel` | 取消 |
| POST | `/api/matches/{id}/feedback` | `{met, rating, again, comment, feltSafe}` |
| POST | `/api/matches/{id}/report` | `{category, note}` |
| GET | `/api/matches/{id}/contact` | 双向想再见才有微信，否则 `{wechat:null}` |

演示面板（`DEMO_MODE=false` 时整组 404）。调用者必须是 demo 或 synthetic。对方如果是 real，返回 403，并且不改任何行。

| 方法 | 路径 | body |
|---|---|---|
| POST | `/api/demo/match/stage` | `{stage, partnerId?}` |
| POST | `/api/demo/match/partner` | `{partnerId}` 林夏 `linxia` / 周屿 `zhouyu` / 许沐 `xumu` |
| POST | `/api/demo/match/partner-action` | `{action}`：`confirm` / `disjoint` / `feedback` |
| POST | `/api/demo/match/partner-feedback-intent` | `{wantsAgain}` |
| POST | `/api/demo/match/time` | `{to}`：`dateDay` / `ended` |
| POST | `/api/demo/reset-me` | 只清这个演示号自己的匹配 |
| POST | `/api/demo/seed-profile` | 204，不做任何事（真实入驻不能靠这个按钮） |

管理（`ADMIN_PHONES`，角色在登录时写入）：

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | `/api/admin/match-runs` | `{pool?: bj\|sh, publish?: true, dryRun?: false}`。不传 pool 时北京、上海并行。返回 `{runs, millis}` |
| GET | `/api/admin/weeks` | 最近的批次 |
| GET | `/api/admin/weeks/{id}` | 批次 + 配对 |
| GET | `/api/admin/matches?weekId=` | 配对：名字、handle、cohort、分数、状态、文案来源。没有手机号、邮箱、微信明文 |
| POST | `/api/admin/synthetic/load` | 幂等灌入 200 人。已完成入驻的真实用户不会被覆盖 |
| POST | `/api/admin/demo/reset` | 只删除双方都不是 real 的匹配 |

时段没有交集时，状态机是 `no_overlap`，首页沿用旧文案，显示成「谢谢你认真赴约」。

## 5. 周批次

`WeekRunner` 按 `LOCK → SOLVE → COPY → PUBLISH → CLOSE` 走，每一步一个事务。已完成的步骤再跑是空操作。崩溃停在 `failed` + `failed_step`，下一次 `step` 从这一步接着做。关闭时如果还有未终态的匹配，不标失败，等它们结束。

定时任务 `MatchScheduler`：`SCHEDULER_ENABLED` 默认 true，每 `SCHEDULER_DELAY_MS`（默认 30000）跑一次两城的到期步骤，再推进截止时间（未回应、未选上时间、约会结束、反馈超时）。测试里把它关掉，不靠睡眠。

「立即匹配」只做到发布，不等到下周关闭。同一城市如果还有 `open/locked/solved/copied/failed` 的批次，返回 `409 RUN_IN_PROGRESS`。已发布的批次不挡下一次。种子是 `weekStart.toEpochDay()`。

## 6. 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `SCHEDULER_ENABLED` | `true` | 定时推进批次和截止时间 |
| `SCHEDULER_DELAY_MS` | `30000` | 两次 tick 的间隔 |
| `SYNTHETIC_AUTOLOAD` | `true` | 仅在 `DEMO_MODE=true` 时，启动灌入虚构用户。生产若不想写入这 200 人，设 `false`。就算写入了，cohort 也禁止他们配真实用户 |
| `ADMIN_PHONES` | 空 | 逗号分隔。这些人登录后是管理员。不是新的密钥 |

没有新增密钥。不要轮换已经在用的 `DATA_KEY` / `HASH_PEPPER`。

## 7. 上线时要做的

- 启动时 Flyway 会自己跑 V5。不用手工 DDL。
- 已有用户的 `cohort` 默认是 `real`，计数器默认 0。
- 生产把 `SYNTHETIC_AUTOLOAD=false`，除非明确要在这台机器上放演示数据。演示号和虚构用户可以进库，但短信、邮件网关不能往 `137` 开头的虚构号码或 `@example.com` / `@demo.*.edu.cn` 发任何东西。`OutboundGuard` 会拒掉 cohort 为 synthetic 的发送。
- `ADMIN_PHONES` 填实际管理员手机号。
- `SCHEDULER_ENABLED` 保持 true，周批次才会在周二锁定、周三发布。
- 演示接口在 `DEMO_MODE=false` 时不存在。

## 8. 已知缺口

- 匹配说明是模板，不是大模型。后台的模型日志、举报处理、封禁操作不在这一期。
- 地点种子只有商圈，没有具体咖啡馆。约会地点用「{商圈}附近的咖啡馆」合成，不挂 `place` 外键。
- 登录仍是固定验证码，没有真实短信。
- 管理端的「清空演示数据」比技术方案 §8.6 窄：不动真实用户。
- 领域层的 `DomainException` 和接口层的异常类型还没有合并。
