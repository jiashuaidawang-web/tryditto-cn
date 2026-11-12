# 技术方案：校园 AI 红娘 MVP

- 状态：待用户评审。评审通过前不写代码。
- 日期：2026-11-12
- 对应文档：`docs/PRD.md`。调研引用写成「04 §3.3」，指 `docs/research/04-技术选型.md` 第 3.3 节。
- 证据标签沿用调研：【已证实】【推断】【猜测】。没有标签的句子是本方案的设计决定。

## 1. 范围与对 04 调研的改动

技术栈延续 04 调研的推荐（04 §8）。为了让 MVP 只服务核心闭环，本方案做了五处删减：

| 04 的推荐 | 本方案 | 理由 |
|---|---|---|
| 照片先交给 `deepseek-flash` 打标签（04 §2.2） | 照片不进入任何大模型，只走阿里云内容审核 | 匹配本来就不用外貌（04 §3.2）。模型看不到照片，就不可能给人打颜值分，也少一条敏感数据流 |
| 海报用 HTML 模板加 Playwright 截图成图（04 §4.2） | 匹配卡片就是 H5 页面，按查看者渲染，不生成图片 | 没有 iMessage 或微信这类需要发图片的渠道。少一个 Chromium 批处理环节 |
| 每周三通知短信，加一封备份邮件（04 §1.3） | 只发短信，H5 是结果的唯一来源 | 邮件只用于验证码，发信域名的信誉留给验证码用 |
| Web Push 作为增强（04 §1.3） | MVP 不做 | iOS 要求先添加到主屏幕，国内厂商浏览器没有文档证明支持【已证实】（04 §1.1） |
| 表结构预留 pgvector（04 §8） | MVP 不装 | 单校规模用全量打分即可【推断】（04 §3.3） |

## 2. 核心数据模型

PostgreSQL。所有时间用 `timestamptz` 存储，业务按 `Asia/Shanghai` 计算。手机号、邮箱、联系方式、「想认识的性别」都加密存储。

### 2.1 实体

| 实体 | 关键字段 | 约束 |
|---|---|---|
| `school` | `id`、`name`、`student_email_domains[]`、`status`（`waitlist`、`active`、`paused`）、`open_threshold` | 学生邮箱域名白名单在这里维护。教职工域名不在白名单里 |
| `user` | `id`、`school_id`、`phone_enc`、`phone_hash`、`birth_date`、`gender`（`M`、`F`）、`grade`、`degree_level`、`status`、`personalization_enabled` | `phone_hash` 唯一。`status` 见第 3.4 节 |
| `student_verification` | `user_id`、`email_enc`、`email_hash`、`term`、`verified_at` | 有效记录里 `email_hash` 唯一。每学期一条，没有当期记录就不能参与 |
| `seeking` | `user_id`、`genders_enc` | 单独一张表，只有匹配快照导出能读，后台默认不可见 |
| `consent` | `user_id`、`kind`（`tos`、`privacy`、`algorithm`、`seeking_sensitive`）、`version`、`granted_at`、`revoked_at` | 每一类单独记录 |
| `profile_version` | `id`、`user_id`、`version`、`answers`、`open_answers`、`submitted_at` | 只追加，不修改。批次快照引用具体版本 |
| `profile_tag` | `profile_version_id`、`label`、`evidence_quote`、`source`（`ai`、`user`）、`status`（`proposed`、`confirmed`、`deleted`）、`ai_generation_id` | 只有 `confirmed` 的标签进入匹配和理由 |
| `photo` | `user_id`、`oss_key`、`moderation`（`pending`、`pass`、`reject`）、`position` | OSS 私有读，短时签名 URL |
| `contact_reserve` | `user_id`、`kind`（`wechat`、`phone`）、`value_enc` | 只在双方都想再见时解密给对方 |
| `place` | `school_id`、`name`、`guide`、`activity_types[]`、`slot_rules`、`active` | 运营实地录入 |
| `week` | `id`、`school_id`、`week_start`（周三日期）、`state`、`snapshot_hash`、`solver_version`、`weights_version` | `(school_id, week_start)` 唯一 |
| `participation` | `week_id`、`user_id`、`profile_version_id`、`status`（`in`、`paused`、`excluded`）、`exclude_reason` | 主键 `(week_id, user_id)`。锁定时写入 |
| `match` | `id`、`week_id`、`user_low`、`user_high`、`score`、`place_id`、`state`、`slot_start`、各截止时间 | `(week_id, user_low, user_high)` 唯一，且 `user_low < user_high` |
| `match_side` | `match_id`、`week_id`、`user_id`、`response`、`decline_reasons`、`slots[]`、`supplemented`、`confirmed_at`、`cancel`、`arrival` | `(week_id, user_id)` 唯一，数据库层面保证一人一周最多一个匹配 |
| `match_text` | `match_id`、`viewer_id`、`reasons`（每条带 `cites[]`）、`tips`、`source`（`ai`、`fallback`）、`ai_generation_id` | 每个匹配两行，一个查看者一行 |
| `feedback` | `match_id`、`user_id`、`attended`、`want_again`、`chat_score`、`flagged_reason`、`not_again_reasons`、`felt_safe` | 主键 `(match_id, user_id)` |
| `report` | `id`、`reporter_id`、`reported_id`、`match_id`、`category`、`text`、`state`、`sla_due_at`、`handler_id`、`decision` | 安全类举报创建时就写入 `block` 并暂停被举报人 |
| `block` | `blocker_id`、`blocked_id`、`source` | 两人有过任何匹配，也算互斥，由 `match` 表推出，不重复存 |
| `strike` | `user_id`、`kind`（`no_show`、`late_cancel`、`no_response`）、`match_id`、`term` | 处罚由 strike 计数推出 |
| `penalty` | `user_id`、`level`、`starts_at`、`ends_at`、`reason`、`appeal_id` | |
| `appeal` | `id`、`user_id`、`target`、`state`、`original_handler_id`、`reviewer_id`、`ack_due`、`decide_due` | `reviewer_id` 不等于 `original_handler_id`，由检查约束保证 |
| `ai_generation` | `id`、`kind`、`subject_id`、`model`、`prompt_version`、`input_hash`、`output`、`validation`、`moderation` | `(kind, subject_id, prompt_version)` 唯一，重跑不会重复调用 |
| `notification` | `idempotency_key`、`user_id`、`channel`、`template`、`state`、`attempts`、`provider_msg_id` | `idempotency_key` 唯一 |

### 2.2 两条建模原则

**每一方的动作只写自己那一行。** 双方会同时点「去」、同时确认、同时反馈。如果都改 `match` 一行，就要处理写冲突。所以每一方只写自己的 `match_side` 或 `feedback`。匹配级状态只由一个函数推进，它在事务里锁住 `match` 行，读双方的行，再做一次比较并设置（`UPDATE ... WHERE state = 旧状态`）。

**不可变快照。** 批次锁定时，记录每个参与者用的是哪个 `profile_version`，并把求解器的输入存成 JSON，连同哈希值一起保存。之后用户改问卷，不会影响本周结果。出了问题，可以用同一输入重放。

## 3. 状态机

状态机写成一张迁移表（`domain/match-machine.ts`），不在各处写条件判断。表里每一行是「当前状态、事件、守卫条件、下一状态、副作用」。所有状态推进都经过这张表。

### 3.1 匹配 `match.state`

| 当前 | 事件 | 守卫 | 下一个 | 副作用 |
|---|---|---|---|---|
| `draft` | 周三 19:00 发布 | 批次为 `reasoned` | `published` | 发通知短信 |
| `published` | 一方选「不去」 | | `declined` | 通知另一方「这次没有约成」 |
| `published` | 双方都选「去」 | 时段有交集 | `scheduled` | 选时段，发时间方案 |
| `published` | 双方都选「去」 | 时段无交集 | `no_overlap` | 请双方补充时段 |
| `published` | 周四 22:00 到期 | 至少一方未回应 | `expired` | 未回应方记 `no_response` |
| `no_overlap` | 一方补充时段 | 有交集 | `scheduled` | 同上 |
| `no_overlap` | 周五 20:00 到期 | | `unscheduled` | 不记责任 |
| `scheduled` | 双方都已确认 | | `confirmed` | |
| `scheduled` | 确认截止 | 至少一方未确认 | `unconfirmed` | 未确认方记 `no_response` |
| `confirmed` | 一方取消 | 距开始超过 6 小时 | `cancelled` | 记为提前取消，不记责任 |
| `confirmed` | 一方取消 | 距开始不足 6 小时 | `cancelled` | 取消方记 `late_cancel` |
| `confirmed` | 时段结束后 1 小时 | | `awaiting_feedback` | 发反馈提醒 |
| `awaiting_feedback` | 双方都已反馈，或 72 小时到期 | | `closed` | 按 PRD 第 4.7 节的表判定结果，双方都想再见就解锁联系方式 |
| 除终态外任一状态 | 安全类举报 | | `halted` | 写 `block`，暂停被举报人 |

终态：`declined`、`expired`、`unscheduled`、`unconfirmed`、`cancelled`、`closed`、`halted`。`closed` 另存 `outcome`，取值为 `met`、`no_show`、`disputed`、`no_feedback`。

时间方案从双方时段的交集中取最早一个，且距当前至少 24 小时。确认截止时间是「方案发出后 12 小时」和「开始前 12 小时」中较早的那个。这两个规则写成纯函数，各自有单元测试。

### 3.2 每一方 `match_side`

`response` 取值为 `pending`、`accept`、`decline`。只能从 `pending` 改一次。`accept` 必须同时带至少一个时段，这在 Zod 层和数据库检查约束里都要校验。`supplemented` 只能从假改成真一次。

### 3.3 周批次 `week.state`

`open → locked → solved → reasoned → published → closed`

| 步骤 | 何时运行 | 完成判据 |
|---|---|---|
| `lock` | 周二 23:59 起 | 写完 `participation` 和输入快照 |
| `solve` | 周三 00:10 起 | `match` 和 `match_side` 草稿已写入 |
| `reason` | 周三 00:30 起 | 每个草稿匹配都有两行 `match_text` |
| `publish` | 周三 19:00 | 所有草稿在同一个事务里变成 `published` |
| `close` | 下周三锁定前 | 本周所有匹配都在终态 |

### 3.4 用户 `user.status`

`registered → phone_verified → email_verified → onboarding → pending_review → eligible`。审核不通过进入 `needs_revision`，用户修改后回到 `pending_review`。`suspended` 是暂停，处罚到期或申诉通过后回到 `eligible`。`banned` 和 `deleted` 是终态。只有 `eligible`、有当期 `student_verification`、`personalization_enabled` 为真、且没有生效中的 `penalty` 的用户，才能进入批次。

## 4. 系统模块与边界

```
            ┌─────────────── Next.js 应用（TypeScript）────────────────┐
H5 页面 ──▶ │ api/*  ──▶  domain/*（纯函数：状态机、时段、处罚、资格）  │
            │   │                                                     │
            │   ├─▶ adapters/：sms、mail、moderation、storage、llm    │
            │   └─▶ db/（迁移、查询）                                  │
            │ jobs/：batch（lock、solve、reason、publish）、tick、notify │
            └──────────────┬───────────────────────────────────────────┘
                           │ stdin 输入快照 JSON，stdout 输出配对 JSON
                    ┌──────▼──────┐
                    │ matcher/    │ Python + OR-Tools CP-SAT，纯函数，不连数据库
                    └─────────────┘
```

| 模块 | 职责 | 边界规则 |
|---|---|---|
| `domain/` | 状态迁移表、时段交集、截止时间、处罚档位、爽约判定、资格判定 | 不做 I/O。时间通过参数传入 `now`，测试可以快进时钟 |
| `api/` | 认证、入驻、问卷、本周、匹配回应、确认、见面状态、反馈、举报、设置、后台 | 所有外部输入在这一层用 Zod 解析，之后的内部代码信任类型 |
| `adapters/` | 短信、邮件、内容审核、OSS、大模型 | 每个都有 `fake` 实现。M1 全部用 fake |
| `jobs/batch` | 周批次的四个步骤 | 只有它写 `match` 草稿 |
| `jobs/tick` | 每分钟扫描到期的截止时间，推进状态 | 只调用状态机，不自己写状态 |
| `jobs/notify` | 发送 `notification` 队列 | 按 `idempotency_key` 去重 |
| `matcher/` | 计算边权、求最大权匹配 | 输入输出是 JSON 文件格式，由共享 schema 定义。数据库只由 TypeScript 一方写 |
| `admin/` | 审核队列、举报处理、批次监控、申诉 | 后台看不到 `seeking` 和解密后的联系方式 |

匹配器只做一个纯函数，因为它是最难测、最容易出错的部分。TypeScript 导出快照，Python 返回配对，TypeScript 写库。这样只有一个写数据库的进程，匹配器可以单独拿夹具测试【推断】（04 §7）。

## 5. 技术栈

| 层 | 选择 | 依据 |
|---|---|---|
| H5 与 API | Next.js、React、TypeScript `strict`，运行在国内云主机的 Node 进程里 | 04 §6.1、§8 |
| 校验 | Zod 作为唯一 schema 来源，覆盖问卷、API、模型输出 | 04 §7 |
| 数据库 | PostgreSQL（阿里云 RDS 或自建） | 04 §6.1 |
| 匹配 | Python 加 OR-Tools CP-SAT | 04 §3.3 |
| 对象存储 | 阿里云 OSS，私有读写 | 04 §6.1 |
| 定时 | 系统 cron，`Asia/Shanghai`，用 Postgres advisory lock 防止重复运行 | 04 §6.1 |
| 短信 | 阿里云短信通知模板，每条不超过 70 字 | 04 §1.3 |
| 邮件 | 阿里云邮件推送，独立发信域名，配置 SPF 和 DKIM，只发验证码 | 04 §6.2 |
| 大模型 | 理由和提示用 `deepseek-flash` 非思考模式加 JSON 输出；画像抽取用百炼 `qwen3.7-plus` 的 JSON Schema；两家互为备用 | 04 §2.2、§8 |
| 内容审核 | 阿里云内容安全：照片用 `profilePhotoCheck`，文本用标准版，模型输出用 `llm_response_moderation` | 04 §5.1 |
| 测试 | Vitest、pytest（含 hypothesis）、Playwright | 04 §7 |
| 本地环境 | 一个仓库，一个 `docker compose`（Postgres） | 04 §7 |

周三 19:00 和凌晨都落在 DeepSeek 的闲时价格时段。高峰是工作日 9:00 至 12:00 和 14:00 至 18:00【已证实】（04 §2.1，协调人已核对定价页）。

上线时要在页面公示所调用模型的名称和备案号。百炼公示页列出的备案号包括 `Beijing-DeepseekChat-202404280016` 和 `ZheJiang-TongYiQianWen-20230901`【已证实】（04 §2）。这些备案号是否覆盖当前模型版本，要向厂商确认【推断】（04 §2）。

## 6. 周三批处理

### 6.1 流程

1. **锁定（周二 23:59）。** 选出可参与用户，写 `participation`，把输入快照写成 JSON 并存哈希。快照包含：用户 id、性别、想认识的性别、年级、关系意图、匹配松紧、问卷向量、已确认标签、拒绝项、互斥对（拉黑和历史匹配）、反馈学到的权重版本。快照不含手机号、邮箱、照片、开放题原文。
2. **求解（周三 00:10）。** 调用 `matcher`。它先按硬约束删边（互相不在对方想认识的性别范围内、拉黑、历史匹配、年级窗口、拒绝项），再给剩下的边打分，删掉低于双方门槛的边，最后用 CP-SAT 求每人至多一条边的最大权匹配。求解器固定随机种子，并设置 `num_workers=1`，同一输入总是得到同一输出。结果在一个事务里写入 `match` 草稿和 `match_side`。地点按规则从地点库中选，规则是双方偏好的活动类型的交集，再取使用次数最少的点。
3. **生成理由（周三 00:30 起）。** 给每个草稿匹配调用大模型，校验、审核后写入 `match_text`。见第 7 节。
4. **发布前检查（周三 18:00）。** 如果还有匹配缺理由，就用模板句补上，并通知值班人。
5. **发布（周三 19:00）。** 在同一个事务里，把所有草稿改为 `published`，同时把批次改为 `published`。然后给每个参与者入队一条通知：有匹配的发「本周匹配已生成」，空周的发「本周没有合适的人，已自动进入下周」。

### 6.2 幂等与重试

每个步骤都是 `step(weekId)` 形式的函数，先读批次状态：已经完成就直接返回，没完成就把剩余部分做完。cron 在每一步的时间窗内每 5 分钟运行一次，直到批次进入下一个状态。所以一次崩溃只会让这一步晚 5 分钟完成，不需要有人手动补跑。

| 风险 | 防护 |
|---|---|
| 两个进程同时跑同一步 | `pg_advisory_xact_lock(hash(step, week_id))` |
| 求解结果写了一半 | 草稿写入在一个事务里，失败就整体回滚 |
| 重复写入匹配 | `match_side (week_id, user_id)` 唯一约束 |
| 理由重复调用模型 | `ai_generation (kind, subject_id, prompt_version)` 唯一，已有结果直接复用 |
| 重复发短信 | `notification.idempotency_key` 唯一，例如 `week:{id}:published:{user}` |
| 短信发送失败 | 1 小时内指数退避重试 3 次，仍失败就记录。用户打开 H5 仍能看到结果 |
| 模型超时或输出不合规 | 每个匹配最多重试 2 次，并切换到备用厂商。仍失败就用模板句，模板句由结构化重合项拼成，例如「你们都选了『周末去看展』」 |
| 求解器到 18:00 仍未完成 | 告警给值班人，本周发布推迟，页面显示「本周结果稍晚发布」。不发布部分结果 |
| 有人在锁定后修改问卷 | 快照引用 `profile_version`，修改只影响下周 |

### 6.3 定时推进

`jobs/tick` 每分钟运行。它对每一种截止事件执行一条带条件的更新，例如 `WHERE state = 'published' AND respond_due < now()`，再调用状态机。重复运行不会产生重复迁移，因为第二次运行时状态已经变了。

## 7. 大模型调用点与提示词约束

### 7.1 只有两个调用点

| 调用点 | 输入 | 输出 schema | 模型 |
|---|---|---|---|
| 画像标签抽取 | 用户的 3 道开放题原文和所选兴趣 | `tags[]`，最多 8 个，每个含 `label`（不超过 8 个字）和 `evidence_quote` | `qwen3.7-plus` JSON Schema，备用 `deepseek-flash` |
| 匹配理由和开场提示 | 双方的白名单字段：兴趣、已确认标签及其原文、价值观题中双方答案相同的题、作息、活动偏好、地点名 | 每个查看者 `reasons[]` 2 到 3 条、`tips[]` 2 条，每条含 `text`（不超过 60 字）和 `cites[]`（字段 id） | `deepseek-flash`，备用 `qwen-plus` |

下面这些不调用大模型：决定谁和谁配对、选地点、审核、处罚、读照片、处理反馈。拒绝原因和反馈都是选择题，自由文本只存给人工看。

传给模型的数据永远不包括：照片、性别、想认识的性别、手机号、邮箱、联系方式、真实姓名、生日。

### 7.2 提示词约束

系统提示词写入仓库，带版本号。改动提示词必须重跑第 8.4 节的评测，评测通过才能合并。约束如下：

1. 只能使用输入中给出的字段，每条理由和提示都必须在 `cites` 里列出依据字段。
2. 不编造共同经历、不编造对方说过的话、不预测关系结果、不写匹配度百分比。
3. 不评价外貌、身材、颜值、穿着，不打任何分。
4. 不推断性取向、宗教、政治、健康状况、家庭经济状况。
5. 标签不使用诊断性词语，例如「焦虑型」「抑郁倾向」。
6. 只输出 JSON。温度设低。

### 7.3 程序校验，不靠模型自觉

模型输出先过 Zod，再过下面的校验器。任一项失败就重试，重试仍失败就使用模板：

- 标签的 `evidence_quote` 必须是用户原文的子串。
- 理由的每个 `cite` 必须指向双方都有值的字段，并且双方的值有重合。
- 文本不能命中禁用词表。词表覆盖外貌、身材、性、宗教、政治、健康、「命中注定」一类的承诺词。
- 文本过阿里云 `llm_response_moderation` 审核。
- 写入 `match_text` 时 `source = 'ai'`，前端据此显示「AI 生成」标识。标识办法要求在界面上给出显著提示【已证实】（03 §2.4）。

## 8. 测试策略

### 8.1 单元测试（Vitest）

- 状态迁移表：每个允许的迁移各一个测试，并对每个状态枚举所有不允许的事件，断言会被拒绝。
- 时段交集、方案时段选择、确认截止时间。
- 爽约判定表（PRD 第 4.7 节）和处罚档位（PRD 第 4.8 节）。
- 资格判定：未满 18 周岁、邮箱域名不在白名单、本学期未验证、关闭个性化、处罚中，都不能进入批次。
- 第 7.3 节的校验器：每一条规则都有通过和失败的夹具。

### 8.2 匹配器测试（pytest 加 hypothesis）

用 hypothesis 随机生成人群，对每一次输出断言：

- 没有人出现在两个配对里。
- 每一对都互相在对方想认识的性别范围内。
- 没有拉黑对、没有历史匹配对、没有超出年级窗口的对。
- 每条边都不低于双方各自的门槛。
- 同一输入运行两次，输出完全相同。
- 不超过 10 人时，结果和暴力枚举的最优解相同。

### 8.3 合成用户匹配评测

`eval/synth.py` 生成合成学校，供评测和端到端测试共用。参数包括：人数 200、1000、2000；性别比 1:1、1:1.5、1:3；10% 的用户选「都可以」，5% 的用户想认识同性；一定比例的拉黑；预设 4 周的历史匹配。每次运行输出一份报告：

| 指标 | 用途 |
|---|---|
| 硬约束违反数 | 必须为 0 |
| 匹配覆盖率 | 看性别比失衡时有多少人空周 |
| 平均边权 | 和贪心基线对比 |
| 连续 4 周空周的用户占比 | 看是否有人长期被排除 |
| 2000 人的求解耗时 | 04 没有做基准测试【已证实为缺口】（04 §10），这里补上 |

### 8.4 大模型评测

固定一个评测集：至少 50 对合成画像，外加一组对抗样本，例如开放题里大量描述外貌、夹带手机号、谈论宗教的画像。每次改提示词或换模型都要跑：

- schema 一次通过率、重试后通过率、回退到模板的比例。
- 校验器拦截后，进入 `match_text` 的文本中禁用词命中数必须为 0，引用无效数必须为 0。
- 人工抽查 20 条，判断理由是否空泛。Ditto 的理由被学生批评空泛【已证实】（01 §9.1）。

### 8.5 端到端测试（Playwright）

测试环境提供 `POST /test/clock`，用于快进时钟，只在 `NODE_ENV=test` 时挂载。所有外部服务用 fake。场景：

1. 正常闭环：两名合成用户注册、填问卷、到周三 19:00 看到卡片、都选「去」、确认时间、见面状态、都反馈「想再见」、看到对方联系方式。
2. 一方选「不去」，对方看到中性文案，下周这两人不再配对。
3. 时段无交集，补充一次后仍无交集。
4. 一方未确认。
5. 开始前不足 6 小时取消，记一次迟取消。
6. 爽约判定，以及第一次爽约后下周被暂停。
7. 举报后匹配进入 `halted`。
8. 关闭个性化匹配后，下周不进入批次。
9. 未满 18 周岁、邮箱域名不在白名单，都在注册时被拒绝。
10. 空周页面。

### 8.6 幂等测试

在集成测试里，把批次每一步各运行两次，断言数据库状态和只运行一次时一样。在 `solve` 和 `reason` 中间注入崩溃，断言下一次 cron 运行能把批次推进完成。

## 9. AI 员工开发分工

每个单元可以独立开发和验证。完成判据都是可以运行的检查，不是「代码写完了」。

| 单元 | 内容 | 依赖 | 完成判据 |
|---|---|---|---|
| U0 脚手架 | 仓库、`docker compose`、TypeScript `strict`、lint、Vitest、pytest、CI、可注入的时钟 | 无 | `docker compose up` 后 CI 全绿，空测试能跑 |
| U1 数据模型 | 第 2 节所有表的迁移、唯一约束和检查约束 | U0 | 迁移可以从零执行；针对每个唯一约束和检查约束各有一个违反用例，断言会报错 |
| U2 状态机 | `domain/` 下的迁移表、时段、截止、处罚、爽约、资格 | U0 | 第 8.1 节的单元测试全绿，覆盖率 100% |
| U3 账号 | 手机验证码、学校邮箱验证码、年龄门、各项单独同意 | U1 | 端到端场景 9 通过；fake 短信和邮件收到验证码 |
| U4 问卷与画像 | 分段问卷、Zod schema、照片上传与审核、标签抽取、画像确认页 | U1、U6 | 合成用户能走完入驻；删除标签后，下周快照里没有这个标签 |
| U5 匹配器 | Python 纯函数：删边、打分、CP-SAT | U0 | 第 8.2 节的性质测试全绿；输入输出 schema 和 TypeScript 一侧一致 |
| U6 大模型网关 | 厂商适配、提示词版本、Zod 校验、第 7.3 节校验器、模板回退 | U0 | 第 8.4 节评测报告达标；fake 厂商能模拟超时和非法 JSON |
| U7 批处理 | `lock`、`solve`、`reason`、`publish`、`tick`、`notify` | U1、U2、U5、U6 | 第 8.6 节的幂等测试和崩溃恢复测试通过 |
| U8 匹配卡片与约会 | 卡片页（照片叠加查看者编号水印）、去或不去、时段、确认、见面页、取消 | U2、U7 | 端到端场景 1 至 5 通过 |
| U9 反馈与处罚 | 反馈表、爽约判定、strike 与 penalty、联系方式解锁 | U2、U8 | 端到端场景 1 和 6 通过 |
| U10 安全 | 举报、拉黑、暂停、申诉、后台审核队列 | U1、U2 | 端到端场景 7 通过；后台接口测试断言看不到 `seeking` |
| U11 设置与合规页面 | 暂停本周、关闭个性化、标签管理、注销、算法说明页、AI 标识 | U3、U4 | 端到端场景 8 通过；注销后数据库中画像、照片、联系方式都已删除 |
| U12 合成评测 | `eval/synth.py`、评测报告 | U5 | 第 8.3 节的报告能生成，硬约束违反数为 0 |
| U13 闭环验收 | 第 8.5 节全部场景串起来跑 | 全部 | Playwright 全绿，连续运行 3 次结果一致 |

并行方式：U2、U5、U6 只依赖 U0，可以同时开工。U1 完成后，U3、U4、U10 可以并行。U7 是汇合点。每个单元一个分支，合并前必须通过自己的完成判据。

## 10. 里程碑

| 里程碑 | 内容 | 退出条件 | 依赖的人工关卡 |
|---|---|---|---|
| M1 本地可跑闭环 | U0 至 U13，外部服务全部用 fake，大模型可以接真实 API 跑评测 | 端到端全绿；合成评测报告硬约束违反为 0；大模型评测达标 | 无 |
| M2 单校内测 | 接入真实短信、邮件、审核、OSS；邀请试点学校一小批学生（建议不超过 300 人），跑 4 个周三 | PRD 第 8 节指标每周可以算出；没有未处理超时的安全举报 | PRD 第 9 节第 1 至 14 项。真实学生使用就算对公众提供服务，所以内测前必须完成 ICP 备案【推断】（03 §1.1） |
| M3 公开试点 | 试点学校开放注册 | 达到 PRD 第 8 节的 M2 转 M3 门槛 | 算法备案在提供服务之日起 10 个工作日内填报，通过后公示编号【已证实】（03 §3） |

关键路径在人工关卡，不在开发。ICP 备案在材料齐全后法定 20 个工作日【已证实】（03 §1.3）。短信签名报备 5 到 10 个工作日，运营商不承诺时限【已证实，厂商观察】（03 §7.1）。网信办对大模型登记的答复没有法定期限【已证实】（03 §9 第 9 项）。这些都要求公司先注册。建议 M1 开发和公司注册同时启动。

## 11. 成本估算（1000 名活跃用户，每月）

以 04 §9 的算法为基础，按本方案的改动调整。这是乘法，不是账单。

假设（标【猜测】的是漏斗比例，没有数据支持）：

- 1000 人每周都参与，每月 4 个周三，每月新注册 200 人（沿用 04 §9）。
- 每周约 400 个匹配，每月约 1600 个【猜测】，即约 80% 的人拿到匹配。
- 一半的匹配双方都选「去」【猜测】。每个这样的匹配平均再发 8 条短信：时间方案 2 条、确认提醒 2 条、见面状态 2 条、反馈提醒 2 条。
- 短信 0.045 元/条，邮件 0.002 元/封，`deepseek-flash` 闲时输入 1 元、输出 4 元每百万 tokens，照片审核 30 元/万次，文本审核 7.5 元/万次，模型输出审核 15 元/万次【已证实单价】（04 §1.1、§2.1、§5.1）。

| 项 | 用量 | 元/月 |
|---|---|---|
| 周三通知短信（含空周通知） | 4000 条 | 180 |
| 注册验证码短信 | 400 条 | 18 |
| 约会流程短信 | 800 个匹配 × 8 条 = 6400 条 | 288 |
| 验证码邮件 | 400 封 | 0.8 |
| 画像抽取 | 200 次，每次输入约 2000、输出约 300 tokens | 不到 1 |
| 理由和提示 | 1600 次，每次输入约 1500、输出约 600 tokens，按 1.5 倍计入重试 | 约 10 |
| 照片审核 | 600 次 | 1.8 |
| 文本审核（简介、开放题、状态短文本） | 3 万次（沿用 04 §9） | 22.5 |
| 模型输出审核 | 1600 次 | 2.4 |
| **已计入合计** | | **约 525** |

比 04 估算的约 257 元多出来的部分，主要来自约会流程短信。04 只算了周三通知和验证码（04 §9）。如果状态和提醒改成只在 H5 内显示，可以省掉其中大部分，但到达率会下降。这是一个产品取舍，留给 M2 用数据决定。

未计入的部分：云主机、RDS、带宽的目录价，04 没有从购买页抄到【已证实为缺口】（04 §6.1）；公司注册、律师、等保测评；安全值班人的人力。按 1000 用户的规模，人力很可能比云费和模型费都高【推断】。收费功能不在 MVP，所以 1000 万元注册资本门槛不适用于这一阶段【已证实】（03 §1.3）。

## 12. 未决技术问题

1. 「想认识的性别」是否构成敏感个人信息。本方案已按敏感信息设计，律师结论不影响结构。
2. 属地网信办对「API 调用加输出校验」走登记还是备案。程序校验可能被看成二次处理，但不能为此删掉校验【推断】（03 §2.3）。
3. 试点学校邮箱对新发信域名的拦截程度，需要用真实学生邮箱实测【已证实为缺口】（04 §10）。
4. CP-SAT 在 2000 人规模的实际耗时，在 U12 里测。
5. 见面状态按钮是否会被认定为即时通讯，需律师确认【推断】（03 §5.2）。
