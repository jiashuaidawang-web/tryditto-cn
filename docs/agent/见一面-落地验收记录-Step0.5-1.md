# 落地验收记录：Step 0.5 + Step 1（部分）

- 分支：`master_agent_v3`（HEAD `11d3c17`）
- 日期：2026-11-15
- 执行人：CoS（总操刀手）
- 原则：不动 MySQL、不动 nginx、不换线上 jar、不打印密钥

---

## 一、Step 0.5 环境准备 —— 通过

### 镜像阻塞与解法（实测）

| 障碍 | 结果 |
|---|---|
| Docker Hub / 4 个国内加速站 / github.com | 全部不通 |
| `ghcr.io/cloudnative-pg/postgresql:16` | ghcr 可达但 layer 下载 ~17 KB/s，9 分钟仅 19 MB，判定不可用 |
| **Qdrant** | 本机经 `gh-proxy.com` 下载 `qdrant-x86_64-unknown-linux-musl.tar.gz`（26 MB，tar 校验通过）→ scp → 服务器 `--version` = 1.12.5 |
| **PG16** | 服务器 Alpine CDN 可达（`dl-cdn.alpinelinux.org` 200）→ 用**本地已有** `nginx:1.27-alpine` 基础镜像 + `apk add postgresql16 postgresql16-client` 构建，完全绕开 Docker Hub |

两个镜像都在 `/opt/aiditto-v3/` 隔离目录构建，不触碰 `/opt/aiditto`。

### 验收结果

| 检查项 | 命令 / 结果 |
|---|---|
| Qdrant 容器 | `aiditto-qdrant` Up，`/healthz` = `healthz check passed`，版本 1.12.5，`127.0.0.1:6333/6334`，`--memory=512m` |
| PG16 容器 | `aiditto-pg16` Up，日志 `database system is ready to accept connections`，`127.0.0.1:5432`，`--memory=384m`，`shared_buffers=128MB` |
| PG 版本 | `PostgreSQL 16.16 on x86_64-alpine-linux-musl` |
| 建库 | `aiditto_pg` + `aiditto_test` 均 `CREATE DATABASE` 成功 |
| 端口隔离 | `ss -lntp`：5432 / 6333 均只绑 `127.0.0.1` |
| 现有服务零中断 | `aiditto-api` Up 46h (healthy)、`aiditto-web` Up 2d、`ditto-mysql` Up 8d；站点 `HTTP 200` |
| 内存 | 可用 2042 MB → 1953 MB（新增两个容器仅占约 89 MB，远低于预算） |
| 可复现配置 | `deploy/docker-compose.v3.yml`（写明 Step 10 前不执行 up，两个镜像的 build context 与限额） |

**Step 0.5 闸门：通过。**

---

## 二、Step 1 PG 方言迁移 —— 已实跑，未接应用

### 已完成

| 项 | 内容 |
|---|---|
| MySQL 原版保全 | 6 个迁移文件原样备份到 `db/migration-mysql/`（回滚对照，Flyway 默认不扫描） |
| PG 版生成 | `db/migration/` 下 V1–V5 + `R__seed_places.sql` 全面转 PG：JSONB / BYTEA / TIMESTAMP(3) / `"` 标识符 / CONSTRAINT UNIQUE / 独立 CREATE INDEX / `ON CONFLICT (id) DO UPDATE SET … = EXCLUDED.…` / BOOLEAN 的 0·1 转 FALSE·TRUE |
| 经典坑已处理 | MySQL 索引名是表级、PG 是 schema 级：`idx_user` 在 V1(`auth_session`) 与 V4(`block`) 重名 → 改为 `idx_auth_session_user` / `idx_block_user`；全局复核无重名 |
| Java SQL 方言 | 49 处反引号标识符（98 个字符）按状态机改写：普通字符串内 `\"`、text block 内 `"`、注释内 `"`；涉及 10 个文件，主要在 `MatchDb`(58)、`People`(10)、`DemoService`(8)、`SyntheticLoader`(8) |
| 依赖与配置 | `flyway-mysql` → `flyway-database-postgresql`，`mysql-connector-j` → `org.postgresql:postgresql`；`application.yml` 默认 JDBC 改 PG 并加 **`stringtype=unspecified`**（否则 `Json.write()` 的 String 写 JSONB 会类型不匹配）；`application-prod.yml` 改 `DB_USERNAME` / `DB_PASSWORD` |
| 编译 | `mvn -o -q -pl aiditto-app -am -DskipTests compile` → `COMPILE_RC=0`；在线 `mvn -pl aiditto-app -am -DskipTests compile` → `BUILD SUCCESS`（Flyway PG 模块与 PG 驱动解析成功） |
| domain 基线 | `mvn -o -q -pl aiditto-domain clean verify` → `DOMAIN_RC=0`（379+ 性质测试未被破坏） |

### 真实 PG 实跑（不是静态检查）

在 `aiditto_test` 上按 V1→V5→R 顺序执行 `psql -v ON_ERROR_STOP=1`：

```
V1__create_user_auth.sql        -> rc=0
V2__create_onboarding_profile.sql -> rc=0
V3__create_photo_place.sql      -> rc=0
V4__create_block.sql            -> rc=0
V5__create_week_match.sql       -> rc=0
R__seed_places.sql              -> rc=0
information_schema.tables = 21 ; place 行数 = 24
```

> `aiditto_test` 是手动 DDL 验证库，未写 `flyway_schema_history`；集成测试按 M2-app 笔记会 Flyway clean 该库，届时自动接管，无需人工删除。

### 本步尚未完成（下一步）

1. 应用侧真正连 PG 启动一次，让 Flyway 自己 migrate（验证 `stringtype=unspecified`、TIMESTAMP(3)/Instant、BYTEA/byte[]、JSONB 读写全链路）
2. 新增 AI 表迁移 `V6__ai_taste_recall.sql` + `V7__sim_eval.sql`（`user_taste` / `taste_log` / `ai_pair_memory` / `recall_candidate` / `llm_call_log` / `vector_sync` / `sim.*`）
3. app 集成测试（22 个）迁到 PG：需要本机 SSH 隧道 `127.0.0.1:15432 → 服务器 127.0.0.1:5432`，并用 `TEST_DB_URL=jdbc:postgresql://…` 覆盖，不改用户 `~/.aiditto-test.env`
4. Step 2 规范同步：修订 `docs/技术方案.md` §7.3 / §8.1 / §8.2 / §4（Q1「完全按 v3」）

---

## 三、本轮变更文件清单（分支内）

```
修改  api/aiditto-app/pom.xml
修改  api/aiditto-app/src/main/resources/application.yml
修改  api/aiditto-app/src/main/resources/application-prod.yml
修改  api/aiditto-app/src/main/resources/db/migration/{V1..V5,R__seed_places}.sql  (改为 PG 方言)
新增  api/aiditto-app/src/main/resources/db/migration-mysql/{V1..V5,R__seed_places}.sql (MySQL 原版备份)
修改  api/aiditto-app/src/main/java/cn/aiditto/{account,block,match}/*.java (10 个文件，SQL 引号)
新增  deploy/docker-compose.v3.yml
新增  docs/agent/见一面-落地验收记录-Step0.5-1.md (本文)
```

服务器侧新增（隔离目录，未触碰现有服务）：`/opt/aiditto-v3/{pg,qdrant,pgdata,migrations,logs}`、容器 `aiditto-pg16` / `aiditto-qdrant`。

---

# 四、Step 1 收尾 + Step 2（2026-11-15 17:xx，同一分支）

## Step 1 闸门：通过

| 验收项 | 结果 |
|---|---|
| `V6__ai_taste_recall.sql` 真实 PG 实跑 | `rc=0`，新增 7 张 AI 表 |
| `V7__sim_eval.sql` 真实 PG 实跑 | `rc=0`，新增 `sim` schema 6 张表；测试库合计 34 张表 |
| **应用侧 Flyway 自启** | 连 `aiditto_pg` 空库启动：日志 `Successfully validated 8 migrations` → V1…V7 + R 全部自动执行 → `now at version v7` → `Started AidittoApplication` → actuator `{"status":"UP"}` |
| **全量 `mvn verify`（PG）** | `VERIFY_RC=0`，**domain 382 + IT 22 = 404 测试全部通过，0 失败，BUILD SUCCESS**（与 M2 基线数字一致，行为零回归） |
| 隧道 | `127.0.0.1:15432 → 服务器 127.0.0.1:5432`，带 `ServerAliveInterval=30` 保活；未改用户 `~/.aiditto-test.env`（只在跑测试时覆盖 `TEST_DB_*`） |

### 迁移过程中修掉的 PG 方言坑（11 项，全部有实测依据）

| # | 现象 | 修法 |
|---|---|---|
| 1 | Flyway clean 只清 `public`，`sim` schema 残留 → `relation "sim_user" already exists` | `spring.flyway.schemas: public,sim`（application.yml + prod） |
| 2 | `AS new ON DUPLICATE KEY UPDATE`（AnswerMapper / AnswerStore / MatchDb） | `ON CONFLICT (...) DO UPDATE SET ... = EXCLUDED....` |
| 3 | `INSERT IGNORE`（BlockMapper） | `INSERT ... ON CONFLICT DO NOTHING` |
| 4 | `CAST(#{valueJson} AS JSON)` | `AS JSONB` |
| 5 | `success = 1`（flyway_schema_history 是 BOOLEAN） | `success` |
| 6 | `UNHEX(SHA2(?,256))` | `sha256(convert_to(CAST(? AS TEXT),'UTF8'))` |
| 7 | `phone_enc LIKE CONCAT('%', ?, '%')`（BYTEA + untyped 参数） | `encode(phone_enc,'escape') LIKE '%' || CAST(? AS TEXT) || '%'` |
| 8 | `VALUES (...,0,...)` 写 BOOLEAN `reselected` | `FALSE` |
| 9 | `Instant` 直接作 JDBC 参数（PG JDBC 不支持 `setObject(Instant)`） | `Timestamp.from(...)`（SyntheticLoader 2 处、DemoService 1 处） |
| 10 | 测试期望版本只到 5、`VERSION()` 期望 MySQL 8.4 | 版本期望改为 1–7、`VERSION()` 改 `PostgreSQL 16` |
| 11 | 测试代码反引号（AuthIT / MatchIT 共 48 字符） | 按状态机转双引号；`ApiIT` 默认 TEST_DB_URL 改 PG |

> 期间还遇到一次 SSH 隧道断开导致 22 个 IT 全 error（"尝试连线已失败"），属环境问题，重建带保活隧道后恢复，非代码缺陷。

## Step 2 规范同步：完成

`docs/技术方案.md` 顶部新增「v3 分支修订声明」，明确 6 条覆盖正文的约定：§8.1 大模型边界修订、§7.3 打分扩展、§8.2 流水线扩展、§4 表设计扩展、数据库改 PG16+Qdrant、404 测试基线。正文原样保留作历史追溯，不删除任何既有规范。

## 本轮新增/修改文件

```
新增  api/.../db/migration/V6__ai_taste_recall.sql
新增  api/.../db/migration/V7__sim_eval.sql
修改  api/.../resources/application.yml (spring.flyway.schemas)
修改  api/.../resources/application-prod.yml (spring.flyway.schemas)
修改  api/.../main/java/cn/aiditto/{onboarding,block,match}/ 共 6 个文件（SQL 方言 / Timestamp）
修改  api/.../test/java/cn/aiditto/it/{ApiIT,AuthIT,MatchIT}.java（方言与期望）
修改  docs/技术方案.md（v3 修订声明）
修改  docs/agent/见一面-落地验收记录-Step0.5-1.md（本文）
```

## 下一步：Step 3 LlmGateway

真实 LongCat-2.5 主 / qwen-plus 备（读服务器 `.env`，密钥不落盘）+ `MockLlm` 确定性实现 + WireMock 8 类故障注入 + `llm_call_log` 落库；闸门：重试后通过率 ≥95%、模板兜底率 ≤5%、主密钥改错整批仍能完成。之后再进 Step 4 信息收集衔接、Step 5 五路召回、Step 6 Agent 精排主导。

---

# 五、Step 3 LlmGateway（2026-11-15 17:xx）

## 交付

新增 `cn.aiditto.ai` 包（10 个文件）：`LlmRequest` / `LlmResult` / `LlmException` / `LlmModel` / `LlmProperties` / `LlmCallLogger` / `JdbcLlmCallLogger` / `MockLlmModel` / `OpenAiCompatModel` / `LlmGateway` / `LlmConfig`；`application.yml` / `application-prod.yml` 加 `aiditto.llm.*`（密钥只从环境变量读，默认 `mode=mock`，prod 默认 `live`）。

两个刻意的技术选择（都在技术方案 §8.4 / 测试分层里有对应退路）：

1. **用 JDK HttpClient 自己实现 OpenAI 兼容端点**，不引 LangChain4j starter：换模型只改配置、无新依赖、离线可跑；`thinking:{"type":"disabled"}` / `response_format` 直接进请求体。
2. **用 JDK ServerSocket 本地 stub 做 8 类 HTTP 故障注入**，不引 WireMock：等价覆盖 429 / 5xx / 400 / 401 / 空 content+length / 非法 JSON / 超时 / 带围栏 JSON / 正常响应，零新增依赖、可离线。

## 验收

| 项 | 结果 |
|---|---|
| 定向单测 | `LlmGatewayTest` 12 + `OpenAiCompatModelTest` 9 = **21 全过** |
| **全量 `mvn verify`（PG）** | `VERIFY_RC=0`，**domain 382 + AI 单测 21 + IT 22 = 425 测试全绿，BUILD SUCCESS**（Spring 装配 LlmConfig 在 IT context 中验证通过） |
| 真实主模型冒烟（服务器侧，密钥不打印/不落盘） | **`PRIMARY HTTP 200`**，`content_present=True`，`finish_reason=stop`，`model=LongCat-2.5` |
| 网关编排 | 主备切换、3 次重试、400/401 立即切备、429 不计入熔断、empty-content 翻倍 max_tokens、校验失败追加提示、连续失败熔断跳过主模型、全部用尽 `degraded=true` 全部有单测 |
| 闸门指标（网关级） | 100 次「前两次故障第三次成功」→ **重试后通过率 100%**；20 次主密钥错误（AUTH）→ **20/20 由备用完成**；`fallbackAvailable=false` 时整批 degraded 不抛异常 |
| 日志 | `llm_call_log` 写入实现 + 内存实现断言状态（ok/retry/failed/degraded）；日志写失败不影响主流程 |

## 两个如实标注的发现 / 未完成项

1. **备用模型密钥为空**：服务器 `/opt/aiditto/.env` 中 `LLM_FALLBACK_BASE_URL` / `LLM_FALLBACK_MODEL` 已配置，但 **`LLM_FALLBACK_API_KEY=EMPTY`**。按技术方案 §8.3「密钥为空时备用整条关闭」，真实环境下主模型失败将直接走模板/粗排兜底，而不是切 qwen-plus。Mock 环境的主备切换测试全部通过，真实备用需要补 key 后才能端到端验收。**建议补 qwen-plus 的 DashScope key，或明确接受单模型 + 兜底策略。**
2. **「模板兜底率 ≤5%」本步 N/A**：网关已提供 `degraded` 标志，但真实文案子图（Step 7）尚未接入，无法按批次统计兜底率；接入后按 Go/No-Go 闸门补测。

## 下一步

Step 4 信息收集衔接（`TasteExtractor`：answers + profile → `user_taste`，挂 `ProfileProjection`，不阻塞入驻）→ Step 5 五路召回（Qdrant 真实实例 + Exact 影子对照 + RRF）→ Step 6 Agent 精排主导 → Step 7 AI 文案 → Step 8 反馈学习 → Step 9 仿真与全量验收。

---

# 六、备用模型切换 DeepSeek-V4.1-Flash-1017（2026-11-15 18:xx）

## 起因

Step 3 验收如实记录了 `/opt/aiditto/.env` 中 `LLM_FALLBACK_API_KEY` 为空 → 按技术方案 §8.3 备用整条关闭。用户指定改用 **DeepSeek-V4.1-Flash-1017**（OpenAI 兼容端点 `https://api.deepseek.com`，模型名固定 `deepseek-flash`）并提供密钥。

## 探测先行：一个必须处理的模型差异

在服务器与本机双端实测三种请求体（真实 HTTP 200，均不打印密钥）：

| 请求体 | content | reasoning_content | 结论 |
|---|---|---|---|
| 不带 thinking 参数 | `收到` | **有** | 默认开思考，白耗 token |
| `thinking:{"type":"disabled"}` | `收到` | 无 | **可用，且是必需的** |
| `response_format:{"type":"json_object"}` | `{"ok":true,"score":0}` | 有 | JSON 干净无围栏 |

**关键风险**：DeepSeek 默认开思考，`max_tokens` 偏小时思考会挤空 `content`，网关会误判 `EMPTY_CONTENT` 并翻倍重试，最坏走到 degraded。原先 `disableThinking` 是**请求级**开关，未来任一调用点忘设就会踩坑 → 因此改为**模型级可配置参数**，由模型实例兜底。

## 改动清单

```
修改  ai/OpenAiCompatModel.java      增加 extraBody（保留旧构造，既有 9 个 HTTP 故障测试零改动）
修改  ai/LlmProperties.java          增加 primary/fallback-extra-body；默认模型改 deepseek-flash；
                                     对 deepseek-* 且未显式配置时自动注入 thinking:disabled
修改  ai/LlmConfig.java              两模型实例注入各自 extraBody
修改  resources/application.yml     默认 fallback-model=deepseek-flash；新增两个 extra-body 开关
修改  test/ai/LlmGatewayTest.java    适配新构造参数
修改  test/ai/OpenAiCompatModelTest.java  新增 1 个「请求未关思考、模型级必须兜底」用例
新增  test/ai/DeepSeekFallbackE2ETest.java  2 个真实端点用例（默认跳过）
新增  deploy/.env.v3.example        占位符版配置样例，不含任何真实密钥
修改  docs/agent/见一面-落地实施与验收计划.md  同步备用模型描述
```

`extra-body` 是通用扩展位（JSON 对象字符串）：换厂商只改配置，不用改代码。显式配置优先；`deepseek-*` 且留空时自动关思考；换成非 DeepSeek 模型不会误注入。畸形 JSON 在启动时 fail-fast，不悄悄发给模型。

## 配置落地（服务器）

`/opt/aiditto/.env` 幂等 upsert 三项并 `chmod 600`：`LLM_FALLBACK_BASE_URL=https://api.deepseek.com`、`LLM_FALLBACK_MODEL=deepseek-flash`、`LLM_FALLBACK_API_KEY=SET(len=35)`；主模型三项、其余变量、注释全部原样保留；追加两行变更说明注释。回滚只需把这三项改回原值（dashscope / qwen-plus / 空），**没有备份出含密钥的副本文件**。

## 验收（全部实测）

| 项 | 结果 |
|---|---|
| 定向单测 | `OpenAiCompatModelTest` 10 + `LlmGatewayTest` 12 = **22 全过** |
| **真实 E2E**（`AIDITTO_LLM_E2E=1`） | **2/2 通过**：① 主模型本地 401 → 网关立即切备用 → 真实 `api.deepseek.com` 成功，`model=deepseek-flash`、`ok=true`、`degraded=false`、`attempts=2`、日志 1 failed + 1 ok、content 可 `Json.readMap` 解析；② jsonMode 输出无代码围栏、含 ok/score 字段 |
| E2E 默认隔离 | 不设开关时 **2 skipped、0 网络调用**，日常 verify 不依赖密钥与网络 |
| **全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，domain 382 + app 单测 24（22 过 / 2 按设计跳过）+ IT 22 = 428 项，0 失败，BUILD SUCCESS** |
| **`LLM_MODE=live` 应用启动** | 连 PG16.16 → Flyway 校验通过 → `Started AidittoApplication in 8.443s` → actuator `{"status":"UP"}`；证明新增配置项在 Spring 绑定路径下解析正常 |
| 零中断 | `aiditto-api` Up 47h (healthy) / `aiditto-web` Up 2d / `ditto-mysql` Up 8d，站点 HTTP 200；PG 隧道与 Qdrant healthz 均正常 |

## 边界（本轮严格遵守）

- 密钥仅出现在服务器 `/opt/aiditto/.env` 与本次验收进程的环境变量中；**未写入仓库任何文件、未打印、未进日志、未进文档**。`deploy/.env.v3.example` 为占位符版
- **线上容器未重启**，新 env 要到 Step 10 切流时才被读取；本轮验收一律用独立进程 / 独立环境变量，线上演示站仍是原 MySQL 版
- E2E 里主模型用本地 401 stub（确定性失败），**没有拿错误密钥去打 LongCat 真实端点**

## 一个安全提醒

该 API key 已在对话中明文出现。建议后续在 DeepSeek 控制台轮换一次，轮换后只需更新服务器 `.env` 的 `LLM_FALLBACK_API_KEY` 并重跑 `AIDITTO_LLM_E2E=1` 的两个用例即可，无需改代码。

## 下一步

Step 4 信息收集衔接（`TasteExtractor`：answers + profile → 版本化 `user_taste`，挂 `ProfileProjection` 且不阻塞入驻）→ Step 5 五路召回 → Step 6 Agent 精排主导 → Step 7 AI 文案（届时补测「模板兜底率 ≤5%」闸门）→ Step 8 反馈学习 → Step 9 仿真与全量验收。

---

# 七、Step 4 信息收集衔接（2026-11-15 19:xx）

## 目标

把现有 `questions.json → AnswerValidator → AnswerStore → ProfileProjector → ProfileProjection.write → profile` 链路，接到 v3 的 `user_taste`（版本化口味画像）。闸门：200 合成用户全量生成且确定性、OnboardingIT 不破、不阻塞入驻。

## 设计（五条 C 端底线）

1. **脱敏白名单**：`TasteFacts`（domain 层纯函数）用**显式白名单**而不是黑名单，再叠加 `catalog.isSensitive()` 与题型黑名单双保险；照片、手机号、邮箱、验证码、微信号、真实姓名、性别 / expectedGender、精确生日、身高、经纬度、dealbreakers 原文、所有敏感题一律不进提示词；自由文本做邮箱 / 手机号 / URL / 长数字打码并截断 60 字；`location` 只保留 `mode` + `minutes`。技术方案 §8.1 的合规红线原样保留。
2. **不阻塞入驻**：`OnboardingService.complete()` / `answer_edit` 发 `ProfileWrittenEvent`，`TasteListener` 用 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` + 专用线程池执行。事务回滚不触发、读到的永远是已提交数据、队列满或异常只记日志，由 `TasteBatchService.refreshMissing()` 补齐。执行器包成自定义 `TasteExecutor` 类型，避免 `@ConditionalOnMissingBean(Executor.class)` 顶掉 Spring Boot 全局 executor。
3. **确定性 + 幂等**：`inputHash = SHA-256(promptVersion + canonical(脱敏 facts))`（与 JSON 库无关的确定性序列化）。锁内复查 hash，答案没变直接复用 active 版本，不重复调模型、不产生新版本。V8 增量迁移加 `input_hash CHAR(64)`（V6 已执行过，validate-on-migrate=true 下不允许改写）。
4. **并发安全**：写事务内 `SELECT pg_advisory_xact_lock(userId)` 串行化同一用户，配合 `uq_user_taste_active` 唯一索引，杜绝版本撞车。模型调用放在写事务之外，不持锁等网络。
5. **绝不空画像**：模型超时 / 主备全挂 / 输出非法 JSON / 输出泄漏联系方式，一律走 `TasteWriter.template()` 确定性兜底；`ensure()` 吞掉所有异常，不回抛给入驻链路。版本只追加、旧版 `is_active=false`、`taste_log` 留痕可回滚可举证。

## 改动清单

```
新增  domain/taste/TasteFacts.java                 脱敏白名单 + 确定性指纹（纯函数）
新增  domain/test/taste/TasteFactsTest.java        6 个红线用例
新增  app/taste/TasteWriter.java                  taste-v1 提示词 / 严格输出校验 / 模板兜底
新增  app/taste/TasteExtractor.java               ensure()：幂等 + advisory lock + 版本化 + 降级
新增  app/taste/ProfileWrittenEvent.java          AFTER_COMMIT 事件
新增  app/taste/TasteListener.java                异步消费 + 失败日志
新增  app/taste/TasteExecutor.java                专用线程池（可退化为同步）
新增  app/taste/TasteAsyncConfig.java             装配
新增  app/taste/TasteBatchService.java            refreshMissing() 补偿 + stats() 可观测
新增  app/test/taste/TasteWriterTest.java         6 个校验/降级用例
新增  app/test/taste/TasteExtractE2ETest.java     真实模型提示词验收（默认跳过）
新增  app/test/it/TasteIT.java                   200 合成用户全量验收（真 PG）
新增  db/migration/V8__taste_input_hash.sql      input_hash 列 + 索引
修改  onboarding/OnboardingService.java          构造注入 publisher，complete/afterWrite 发事件
修改  resources/application.yml                  aiditto.taste.enabled / async-enabled
修改  test/it/ApiIT.java                         IT 内 async-enabled=false 消除竞态
修改  test/it/AuthIT.java                        迁移版本期望 1–7 → 1–8（附注释）
```

## 验收（全部实测）

| 项 | 结果 |
|---|---|
| 编译 / 定向单测 | `TEST_COMPILE_RC=0`；`TasteFactsTest` 6 + `TasteWriterTest` 6 = **12 全过** |
| **200 合成用户全量生成（真 PG）** | `TasteIT`：**200 个合成/演示用户零遗漏**全部有 active 画像；`stats.active == 有答案的 profile 数`；`pendingSync == active` |
| **确定性 / 幂等** | 再跑一轮 `refreshMissing(1000)`，**版本总数完全不变**；`input_hash` 全为 64 位小写 hex |
| **版本化** | 改 `firstMessage` 后 `ensure()` → `CREATED v2`、active 恒为 1 行、v1 置为失效、`taste_log` 2 条 |
| **零 PII 泄漏** | SQL 正则扫 `persona_text/taste_text`：邮箱、11 位手机号、`sk-` 命中数 **0** |
| **异步事件链路** | 删掉画像后发布 `ProfileWrittenEvent` → 自动补回 active 画像（AFTER_COMMIT / fallbackExecution 生效） |
| **真实模型路径** | `TasteExtractE2ETest` 1/1：DeepSeek 按 `taste-v1` 输出可被严格解析的 `{persona,taste}`，`fromModel=true`、无联系方式。证明不是所有批次都悄悄走模板 |
| **V8 生产库迁移** | 应用连 `aiditto_pg` 启动：Flyway `Migrating schema "public" to version "8 - taste input hash"` → `now at version v8` → `Started in 10.242s` → health `UP`；`flyway_schema_history max=8`、`user_taste.input_hash` 列存在 |
| **最终全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，domain 388 + app 单测 31（28 过 / 3 按设计跳过）+ IT 23 = 442 项，0 失败，BUILD SUCCESS**；既有 22 个 IT 全绿，OnboardingIT / MatchIT 零回归 |

## 如实标注的边界与遗留

- **mock 模式下 200 用户走模板兜底**（`MockLlmModel` 输出不含 persona/taste），这正是确定性验收所依赖的路径；真实模型路径由 `TasteExtractE2ETest` 单独验证。**真实批次的「模板兜底率 ≤5%」仍需在 Step 7 文案子图接入后按 Go/No-Go 闸门统计**，不拿 Mock 数据冒充。
- `embedding_version = "v0-placeholder"`、`vector_sync_status='pending'`：`user_taste` 已把待同步账记好，**真正的 embedding 与 Qdrant 写入是 Step 5**，届时替换版本号并回填 `vector_sync`。
- `source_feedback_cnt` 初始 0、跨版本继承；Step 8 反馈重写时递增。
- 每次新增 Flyway 迁移需同步更新 `AuthIT` 的版本期望（代码里已加注释），这是有意的闸门，防止漏执行。
- 本轮**未重启线上容器、未动 MySQL / nginx / 线上 jar**；`aiditto_pg` 侧只做了一次应用启动让 Flyway 自动迁移 V8。密钥全程未打印、未进仓库。

## 下一步

Step 5 五路召回：`QdrantRecallService`（真实 Qdrant 实例）+ `ExactRecallService`（pgvector FLAT 影子精确对照）+ R1–R5 五路 + RRF + 保底 + 分桶 Recall@K 实验矩阵；同时消费本步产出的 `vector_sync_status='pending'` 画像并回填 `embedding_id`。之后 Step 6 Agent 精排主导 → Step 7 AI 文案 → Step 8 反馈学习 → Step 9 仿真与全量验收。

---

# 八、Step 5 五路召回（2026-11-16 09:xx）

## 目标与拆分

按 `research/见一面-召回策略规格.md` 落地召回命门，分五个可独立验收的子步骤：5.1 Embedding 可插拔 + Qdrant REST 客户端 + collection；5.2 VectorSyncService 消费 Step 4 的 pending 画像；5.3 五路召回 + RRF + 保底 + 粗排；5.4 FLAT 影子精确对照 + 分桶 Recall@K；5.5 全量回归。

## 关键技术决策（含两处对规格的显式偏离，需知悉）

1. **Qdrant 客户端自实现 REST**：用 JDK HttpClient 而非 `qdrant-client 1.18`（其 gRPC 与 server 1.12.5 有兼容风险、且引入大量依赖），与 LlmGateway 风格一致、可离线 stub 测试。named vectors `taste`/`persona`、payload index、filter 下沉、`has_id` 排除、scroll/count/delete 全部覆盖。
2. **Embedding 双实现齐备**：`HashingEmbeddingService`（确定性特征哈希，默认）+ `OpenAiCompatEmbeddingService`（任意 OpenAI 兼容 `/embeddings`）。**不是临时桩，是正式可切换实现**；生产缺的只是 embedding key，配置到位即切，业务代码零改动。`version()` 写进 payload 并作为硬过滤项，禁止跨版本混算。
3. **R4 偏离（合规优先）**：规格里 R4 是「审美多模态 / crush 图像 embedding」，与技术方案 §8.1「照片不发给任何模型」直接冲突。本轮 R4 改为**结构化偏好路**（matchingStyle / 学历看重 / 预算 / 活动地点契合），crush named vector 位保留但不启用。若将来要做图像审美，必须先解决合规边界与单独同意。
4. **iso_cells 简化**：真实 isochrone 服务尚未接入，本轮用 `place_id` 近似等时圈可达性；规格要求的网格 id 数组与公交站点判定留待后续。
5. **R1/R2 走 Qdrant，R3/R4/R5 走 PG**：结构化路不依赖 Qdrant，因此 Qdrant 全挂时仍可交付（闸门要求）；同一 `Filter` 在两处语义一致。

## 实测踩坑与修复（3 个，都有证据）

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 200 个画像 Qdrant 写入成功但 PG 回填全 failed | V6 的 `embedding_version VARCHAR(16)` 装不下 `hash-1024-v1-d1024`(19)，真实模型名更长 | **V9** 扩容到 `VARCHAR(64)`；`AuthIT` 版本期望同步 1–9 |
| 2 | 五路召回 fused=0、日志 `recall_shortfall` | 合成 fixture 的 `expectedGender` 用 `men/women/everyone`，工程枚举是 `man/woman`；双向性别过滤在 Qdrant 与结构化路同时 0 命中 | 新增 `GenderNorms` 归一化兼容层（空=不限、everyone 展开全枚举），**不改仿真真值数据**，线上脏取值也自动对齐 |
| 3 | 评测建库 HTTP 422 | Qdrant 1.12 要求 `full_scan_threshold >= 10` | 评测取 10（尽量走 HNSW 索引，避免小数据集自动精确扫描掩盖问题） |

## 验收（全部实测，真 PG16 + 真 Qdrant 1.12.5）

| 项 | 结果 |
|---|---|
| 5.1 Qdrant REST 冒烟 | 3.15s：建库→15 个 payload index→upsert 6 点→**过滤在服务端生效**（6 点只返回 bucketA 的 3 个）→scroll 向量维度正确→delete→删库 |
| 5.2 200 画像全量同步 | `VectorSyncIT`：200 pending → **199 synced + 1 注销 deleted**，`failed=0`；PG 回填 `embedding_id` / 真实 `embedding_version`（不再是 v0-placeholder）/ `synced`；`vector_sync` 审计 200 upsert + 1 delete；Qdrant 点数与 payload 字段核对、零 PII |
| 5.2 可重入 / 注销 | 第二次 `syncAll` `scanned=0`、Qdrant 点数与 `user_taste` 行数不变；把用户置 deactivated → 向量库真删 1 点、PG `vector_sync_status='deleted'` |
| 5.3 五路 + RRF + 粗排 | `recall_candidate` 每周期 10 条留痕，`sources` 五路齐全（`{R1_TASTE,R2_PERSONA,R3_TAGS,R4_PREFERENCE,R5_EXPLORE}`），`cardinality(sources)=0` 为 0；同一 week/user/cand 重复调用幂等 |
| 5.3 Qdrant 不可用演练 | 注入全异常 `RecallService` → `engine=structured-only`、`degraded=true`、**仍有 10 个候选可交付** |
| 5.4 分桶 Recall@K（FLAT 精确对照） | N=5000 / 64 维 / 强制 HNSW / 每桶 40 query / `hnsw_ef=128`：**b50 0.9875、b10 0.9950、b5 0.9925、b1 0.9975、b01 1.0000(K=4)**；p50 34–37ms、p99 51–89ms（含 SSH 隧道） |
| 核心论断 | pgvector post-filter 在 **1% 选择性桶 Recall@10 = 0.039**；Qdrant filterable HNSW 实测 **0.9975** → 上独立向量库的决策成立 |
| **最终全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，domain 392 + app 单测 34（31 过 / 3 按设计跳过）+ IT 26（23 过 / 3 按设计跳过）= 452 项，0 失败，BUILD SUCCESS** |
| V9 生产库迁移 | 应用连 `aiditto_pg` 启动：Flyway `now at version v9` → `Started in 10.228s` → health `UP`；`embedding_version` 宽度 64 |
| 零中断 | 线上 `aiditto-api` / `aiditto-web` / `ditto-mysql` / nginx 全程未动；密钥未打印、未进仓库；全仓扫描通过 |

> 真实实例类 IT（`QdrantClientIT` / `RecallEvalIT` / `VectorSyncIT`）用 `AIDITTO_QDRANT_IT=1` 条件启用，日常 `mvn verify` 默认跳过，不依赖外部服务；本轮已逐个显式打开跑通。

## 如实标注的遗留与上线前必办

- **真实语义 embedding 未接**（缺生产 key）：默认确定性 hash 向量。功能链路齐备，但上线前必须配 `EMBEDDING_MODE=openai` 等并用真实向量**重跑 E1–E14 + 12 周期仿真**，否则语义质量未经验证。
- **R4 审美路**改为结构化偏好（合规）；**iso_cells** 用 place_id 近似；**starve_weeks 恒 0**（R5 用新用户 + 确定性取模），Step 8 反馈学习接入后按真实饥饿周数计算；粗排的「反馈历史 0.20」本轮为 0。
- **E1–E14 完整实验矩阵**（万级 / 1024 维 / 每桶 ≥200 query / 逐路消融 / 权重网格）由 Step 9 仿真阶段用同一套 `RecallEvaluator` 跑；本轮 5000 点 / 64 维 / 40 query 是**机制验证**，不冒充生产规模基准。
- **「单路占比 ≤60%」「候选覆盖率 ≥98%」两个闸门本轮未宣称达标**：200 用户池叠加严格硬约束（同校 + 双向性别 + 年龄 + 身高 + 地点）后候选偏少，日志已出现 `recall_shortfall`；需要 Step 9 扩池 + E8 权重网格调优后再按 Go/No-Go 判定。
- Step 10 切流前须把 api 容器加入 `aiditto-db` 网络并设 `QDRANT_URL=http://aiditto-qdrant:6333`（已写入 `deploy/.env.v3.example`）。

## 下一步

Step 6 Agent 精排主导（分身互约 + 裁判 rubric，AI 分直接决定边权与排序，HardFilter 硬约束保留）→ Step 7 AI 文案（补测模板兜底率 ≤5%）→ Step 8 反馈学习 → Step 9 12 周期合成仿真与全量 Go/No-Go → Step 10 切流与回滚。

---

# 九、Step 5 偏离补全（2026-11-16 10:xx，master_agent_v3）

## 起因与首要结论：用户给的 embedding key 已过期

用户指定备用网关 `https://api.vveai.com` 并提供 key，要求「把所有偏离都补全」，同时给出 `/v1/models` 模型清单接口。

**实测三态对比（本机 + 服务器双端，均不打印密钥）：**

| 请求 | HTTP | 服务端返回 |
|---|---|---|
| 不带 key | 401 | `API key is missing` |
| 乱写的 key | 401 | `Invalid API key` |
| **用户给的 key** | 401 | **`This token has expired`** |

另测：`Bearer` / `x-api-key` / 裸 token 三种认证头、`api.vveai.com` 与 `api.gpt.ge` 两个域名、`/v1/models` 与 `/v1/embeddings` 两个端点，全部同一结论；`/models`（不带 v1）返回 200 网站首页，证明服务在线、API 挂在 `/v1`。

**结论：token 格式合法、服务端认得，但已过期。不是调用姿势、不是域名、不是路径问题。** 需到控制台续期/重置后重新提供。本轮**未伪造任何真实 embedding 验收结果**。

## 偏离清单逐条处置（三类）

| # | Step 5 偏离 | 类别 | 处置 |
|---|---|---|---|
| 1 | 真实语义 embedding 未接 | **B 等 key** | 客户端健壮化 + 模型探测工具 + 配置/一键脚本全部就绪，key 到位即跑 |
| 2 | `iso_cells` 用 place_id 单点近似 | **A 已补** | 本地几何等时圈网格 |
| 3 | `starve_weeks` 恒 0 | **A 已补** | 从 week/match 历史算真实空窗周数 |
| 4 | 粗排「反馈历史 0.20」恒 0 | **A 已补** | FeedbackPrior 真实反馈先验 |
| 5 | E1–E14 只跑机制验证 | **A 已补** | 权重网格/逐路消融 + 生产规模真跑 |
| 6 | 单路占比/覆盖率未达标 | **A 已补口径** | 修指标定义缺陷，给出实测值，如实未宣称达标 |
| 7 | Step 10 api 未入网络 | **A 已补** | compose 加 cutover profile 服务 |
| 8 | R4 审美图像 embedding | **C 合规保留** | 不把照片发给模型（§8.1 红线），该路补到合规上限 |

## A 类：已补全并验收

**1. 本地几何等时圈**（`domain/geo/IsoCellGrid`，合规设计）
- 用 place 表已有的 lat/lng + location 题的 `{placeId, mode, minutes}`，按交通方式速度 × 保守折减估算可达半径，展开成 0.02° 格网编号集合。
- **不接任何外部地图服务、不内置任何 key、不做地图渲染、不展示他人坐标**；输出到向量库的只有格网编号字符串（如 `c3992_11627`），满足位置信息最小化与地图合规红线。
- 全局固定网格（双方编号可比）+ 8km 保守封顶（校园场景不跨城）+ 对称截断保险丝。实测单用户 75–85 个 cell，不触发截断。
- **两侧语义一致性**：引入通配 cell `cANY`。写入侧无地理信息写通配；查询侧带通配放行这类候选；自己无地理信息则完全不施加约束。Qdrant `match any` 与 PG `matches()` 严格等价（方向不可颠倒，已单测覆盖「跨城候选不得放行」）。
- 200 用户全有 location 数据，等时圈真实生效，不是纸面补全。

**2. `starve_weeks`**：SQL 从 `week`(closed) 与 `match`(confirmed/feedback_open/closed) 计算「最近成约后空过的周期数」，写入 payload；R5 探索公平改用真实饥饿值（封顶 8 周防霸榜），保持确定性。

**3. 粗排反馈项 0.20**：新增 `FeedbackPrior`，从 `feedback`(rating/again/met/felt_safe) 算候选质量先验，贝叶斯式平滑（3 条反馈后才接近真实值），无反馈返回**中性 0.5** 不压死新人；查询失败返回空表不中断召回。四权重改为可配置且按和归一化，包装类型缺省取安全默认（避免旧 yml 缺项导致权重静默归零）。

**4. E1–E14 实验矩阵**
- `domain/recall/RecallMatrix`：等权基线 + E1–E5 逐路消融 + E6–E14 权重网格，纯函数毫秒级，输出单路占比/覆盖率/闸门结论。
- `RecallEvaluator.sweepEf()`：hnsw_ef 网格扫描，参数化 n/维度/query 数。
- **真 Qdrant 实测（生产规模档）**：10000 点 / 256 维 / 每桶 60 query / ef=128 → `minRecall=0.9833`，1% 选择性桶 **0.9883**，0.1% 桶 1.0000；p50 30–32ms、p99 58–122ms。对比 pgvector post-filter 在 1% 桶的 0.039。
- **ef 网格（3000 点/128 维）**：ef=32/64/128/256 的 minRecall 分别 0.9875/0.9825/0.9850/0.9875，全档达标 → 该规模下 ef=128 即够，再提只增延迟。
- 修正 `full_scan_threshold` 注释（Qdrant 1.12 要求 ≥10，实际取 10）。

**5. 单路占比闸门：发现并修复指标定义缺陷（重要）**
- 实测 `fused=11`、`shares` 五路全 1.0。原 `RrfFusion.share` 度量「该路贡献过的候选占比」，**无法区分「五路共识召回复合同一批好候选」（健康）与「单路垄断」（风险）**，在 200 人小池必然误报 100%。
- 新增 `RecallMatrix.maxExclusiveRouteShare`（仅被唯一一路召回的候选占比）作为闸门真实信号；`RecallPipeline` 的 dominance 告警同步改用独占口径；旧的 narrow-pool 单独告警，明确标注是**池规模问题而非引擎故障**。
- **如实说明：200 人池叠加等时圈+双向性别+年龄+身高后合格候选仅 11 个，五路高度重合。「单路占比 ≤60%/候选覆盖率 ≥98%」仍不宣称达标**，需 Step 9 扩池 + 权重网格调优后判定。不拿改口径冒充达标。

**6. Step 10 网络**：`docker-compose.v3.yml` 增加 `api` 服务（`profiles: [cutover]` 默认不启动，防误切流），接入 `aiditto-db` 网络、PG/Qdrant 健康依赖、容器名 URL、内存限额与健康检查。

## B 类：真实 embedding 做成「一键就绪」

- `OpenAiCompatEmbeddingService` 健壮化：URL 归一化（`https://api.vveai.com` → `/v1/embeddings`，也支持 `/v1` 后缀与自定义前缀）、`dimensions` 参数可开关（不支持该参数的网关设 false）、维度不符给出实际值与建议配置、单条与批量都做 L2 归一化、批量按 index 归位、错误带 HTTP 状态与响应体片段（token 过期一眼可辨）。6 个本地 stub 单测全覆盖。
- `RecallConfig`：live 模式版本号自动带维度（`model-d<dim>`），换模型/维度后旧向量自动被 filter 排除，禁止跨版本混算。
- `deploy/probe-embedding-models.py`：用用户给的 `/v1/models` 列全部模型、筛 embedding 候选、逐个探测真实维度与延迟、自动回退不支持 dimensions 的情况、输出可直接粘贴的配置建议。密钥只从环境变量读，不落盘不打印；三种 401 错误有诊断提示。**已用过期 key 验证工具本身工作正常**。
- `deploy/verify-live-embedding.sh`：有效 key 一到位，`export EMBEDDING_API_KEY=... && bash deploy/verify-live-embedding.sh` 即完成 模型探测→真实向量全量同步 200 用户→分桶 Recall→E1–E14 矩阵→全量回归，使用独立 collection 避免与 hash 向量混算。
- `VectorSyncIT` 支持 `EMBEDDING_MODE/QDRANT_COLLECTION/EMBEDDING_DIM` 等环境变量注入（默认仍 hash，既有 26 个 IT 行为不变）。
- `.env.v3.example` 与 `application.yml` 补齐全部新配置项（无密钥）。

## C 类：合规红线保留（明确不"补全"）

R4 规格里的「审美多模态图像 embedding」与技术方案 §8.1「照片不发给任何模型」、Step 4 的 `TasteFacts` 脱敏白名单直接冲突。**不把照片送进任何模型**，这是面对 C 端学生用户与 PIPL 的硬红线，不因"补全所有偏离"而突破。该路补到合规上限：结构化偏好从 4 项相等打分扩展为 4 项 + 价值观标签 Jaccard（0.7/0.3 加权），全部只用问卷结构化字段。

## 验收（全部实测）

| 项 | 结果 |
|---|---|
| 编译 | `TEST_COMPILE_RC=0`、`PACKAGE_RC=0` |
| 新增单测 | 等时圈 10 + URL/响应兼容 6 + 反馈先验 4 + 独占指标 5 + 矩阵 4 = 29 全过 |
| **最终全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，domain 396 + app 单测 44（41 过/3 按设计跳过）+ IT 28（23 过/5 条件跳过）= 468 项，0 失败，BUILD SUCCESS** |
| 真实 PG+Qdrant 端到端 | `VectorSyncIT` 通过：200 画像同步、五路 sources 齐全、降级演练、等时圈 payload 75–85 cell、无通配误放行 |
| 生产规模实验矩阵 | `RecallExperimentIT` 2/2 通过（ef 网格 + 万级点档） |
| 应用侧启动 | `Started in 8.559s`、actuator `UP`；新配置绑定与 `RecallPipeline`/`StructuredRecallService` 双构造注入均正常 |
| 线上零中断 | 5 容器 Up、站点 HTTP 200；MySQL/nginx/线上 jar 全程未动 |
| 密钥安全 | 全仓扫描用户提供的 key（含前缀片段）命中 **0**；密钥未进仓库/文档/日志；过期 key 未写入服务器 .env |

## 上线前仍须完成（不隐瞒）

1. **换一个有效 embedding key**：当前 token 过期，真实语义向量链路尚未端到端跑过。key 到位后跑 `verify-live-embedding.sh`。
2. 用真实向量重跑 12 周期合成仿真与 Go/No-Go 报告（Step 9）。
3. 扩池后判定「独占单路占比 ≤60% / 候选覆盖率 ≥98%」两个闸门；当前 200 人池候选仅 11 个，指标无区分度。
4. R4 审美图像路若要启用，需先解决 §8.1 合规边界与单独同意，本轮不做。

---

# 十、真实语义 embedding 全量跑通（2026-11-16 11:xx，master_agent_v3）

## 有效 key 与模型选型

用户提供的新 key 有效：`GET /v1/models` → **HTTP 200，275 个可用模型**（api.vveai.com 与 api.gpt.ge 同网关、结果一致），其中 15 个 embedding 候选。

**中文语义质量实测**（4 句基准：2 句冒险型相关 / 2 句无关；gap = 相关句相似度 − 最大无关句相似度；同时验证批量能力）：

| 模型 | 维度 | 延迟 | 相关 | 无关 | gap |
|---|---|---|---|---|---|
| Qwen3-Embedding-8B | 1024 | 2236ms | 0.828 | 0.632 | +0.196 |
| **text-embedding-v3** | **1024** | **1862ms** | **0.813** | **0.629** | **+0.184** ← 选定 |
| bge-m3 | 1024 | 2774ms | 0.795 | 0.632 | +0.162 |
| text-embedding-3-small | 1024 | 1862ms | 0.645 | 0.499 | +0.146 |
| gemini-embedding-001 | 1024 | 1695ms | 0.860 | 0.722 | +0.138（无关句基线偏高） |
| text-embedding-v4 | 1024 | 1419ms | 0.723 | 0.618 | +0.105 |

选定 `text-embedding-v3 / 1024 维`：语义 gap 接近最优、快、支持 `dimensions`、**国产合规模型**（符合项目铁律）。另发现 `bce-embedding-base_v1` 已停用、`ada-002/v1/v2` 原生 1536 维且不接受 dimensions。

**探测工具缺陷修正**：原按字母序推荐首个可用模型，会选中最慢的 Qwen3-Embedding-8B。改为按「实测语义质量优先 + 延迟」的固定推荐序；一键脚本不再每次自动选型（模型漂移会破坏仿真可复现性），改为固定选型 + 探测仅校验可用性，允许环境变量覆盖。

## 真实向量 vs hash 向量：本次最关键的结论

同一批中文句子对比（第 1 句与第 2 句语义相关但用词几乎不重叠）：

| 向量 | 相关句相似度 | 无关句相似度 | gap |
|---|---|---|---|
| **真实 embedding** | 0.813 | 0.629 | **+0.184** |
| 确定性 hash（此前默认） | 0.115 | 0.227 | **−0.112（完全反向）** |

**hash 向量把语义相关的两个人判成比无关的人更不相似。** 这量化证明了此前 452→468 项测试全绿只说明「工程链路通」，不说明「召回语义正确」；也印证了用户坚持「召回是命门、必须上真实向量库+真实 embedding」的判断。上线用真实向量是硬要求，不是优化项。

## 发现并修复的生产级缺陷：批量上限

首次真实全量同步 `VectorSyncIT` 耗时 **499s**。排查发现不是网络慢，而是**批量实际假生效**：

- `text-embedding-v3` 网关限制**单请求文本数 ≤ 10**（实测 n=10 通过、n=16/20/32 全部 400 `batch size is invalid, it should not be larger than 10`）。
- 原实现按「用户数」切批，每用户 2 条文本（taste + persona），16 用户 = 32 文本 → 每批 400 → **静默回退逐条 400 次 × 1.2s ≈ 480s**。
- 之前日志里没有任何告警，"批量化"看起来生效实则退化。

修法：
- 新增 `EMBEDDING_MAX_BATCH_ITEMS`（默认 10）与 `RecallProperties.usersPerBatch()` 纯函数，按**文本条数**切分，保证 `users × 2 ≤ maxItems`；`EMBEDDING_BATCH_SIZE` 默认 5 用户/批。
- `VectorSyncService` 批量失败**显式 WARN**（不再静默吞掉），并输出 `embedding sync done: users / batchCalls / usersPerGroup / fallbackTexts / costMs` 可观测日志。
- 新增 `FeedbackPriorTest.batchConfigNeverExceedsGatewayTextLimit` 回归防护。
- 修后实测：**40 次批量调用、`fallbackTexts=0`、同步耗时 30.5s**（约快 16 倍），整体 IT 从 499s 降到 222s。

## 全量验收（真实语义向量，全部实测）

| 项 | 结果 |
|---|---|
| 小样本 E2E | `LiveEmbeddingE2ETest` 2/2：1024 维、L2 归一化、批量 index 归位、单条/批量余弦 >0.999、中文语义 gap +0.185 |
| **200 用户真实向量全量同步** | `VectorSyncIT` 通过：Qdrant 199 点（含 1 注销真删）、双 named vector 均 **1024 维**、PG 回填 `text-embedding-v3-d1024` 200 行、`vector_sync` 审计 200 upsert + 1 delete、五路 sources 齐全、Qdrant 挂掉降级仍可交付 |
| 闸门实测 | `fused=11 consensusShare=1.0000 exclusiveShare=0.0000`（印证第九节的独占口径修正：五路共识，非单路垄断） |
| 分桶 Recall@K | `RecallEvalIT` 通过，各桶 ≥0.90 |
| E9/E14 ef 网格 | 3000 点/128 维：ef=32/64/128/256 → minRecall 0.9850/0.9825/0.9875/0.9850，全档 PASS |
| **生产规模档** | 10000 点/256 维/每桶 60 query → `minRecall=0.9833`、1% 桶 **0.9900**、0.1% 桶 1.0000；串行复测 p50 31–32ms、p99 ≤175ms |
| 延迟抖动归因 | 并发跑两个后台任务时 b50 桶 p99 达 1866ms；**串行复测回落到 157ms**，确认是资源争抢而非引擎退化 |
| **最终全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，471 项 0 失败 BUILD SUCCESS**（domain 398 + app 单测 47 + IT 28） |
| `EMBEDDING_MODE=openai` 应用启动 | `Started in 10.431s`、actuator `UP`，真实配置绑定正常 |
| 线上零中断 | 5 容器 Up、站点 HTTP 200；MySQL/nginx/线上 jar 全程未动 |

## 配置落地（服务器，密钥不进仓库）

- 新建 **`/opt/aiditto-v3/.env`**（v3 切流专用，`chmod 600`）：26 项，含 `EMBEDDING_MODE=openai` / `text-embedding-v3` / 1024 维 / batch 5 / maxItems 10 / `QDRANT_COLLECTION=match_user_live_v3`、PG 与 Qdrant 容器名 URL，以及有效 key（仅显示 SET(len=51)，未打印值）。
- **线上 `/opt/aiditto/.env` 完全未改动**（mtime 仍为 2026-11-15 18:17），MySQL 版演示站零风险。
- `docker-compose.v3.yml` 的 `api`（cutover profile）改引用 v3 专用 env，并补齐全部 embedding/recall 变量。
- `deploy/.env.v3.example` 写入实测选型与批量上限注释（无密钥）；`deploy/verify-live-embedding.sh` 固定选型 + 批量参数。
- 全仓扫描用户提供的 key（含前缀片段）命中 **0**；第九节记录里曾写入的脱敏前缀也已清除。

## 仍须完成（不隐瞒）

1. **Step 9**：用真实语义向量重跑 12 周期合成仿真与 Go/No-Go 报告，证明「越匹配越准」——本轮验证的是召回引擎与语义质量，不是完整学习闭环。
2. **扩池**后判定「独占单路占比 ≤60% / 候选覆盖率 ≥98%」：当前 200 人池经等时圈+硬约束后仅 11 个候选，五路高度重合，指标无区分度。
3. **Step 10 切流**：需先 stop/rm 旧 `aiditto-api`，再用 `docker compose --profile cutover up`，灰度 1 个完整周期零 P0 才可推广；回滚路径保留（旧容器 + 线上 .env 原样）。
4. R4 审美图像 embedding 仍不启用（§8.1 照片红线）。
5. 该 key 已在对话中明文出现，建议轮换；轮换后只需更新服务器 `/opt/aiditto-v3/.env`，无需改代码。

---

# 十一、Step 6 Agent 精排主导（2026-11-16 12:xx，master_agent_v3）

## 设计口径（Q1 落实 + C 端底线）

| 决策 | 落地 |
|---|---|
| AI 决定候选与配对排序 | `AgentRerankService` 产出 `Map<PairKey,Integer>` 经 `MatchingInput.aiScores` **主导 Blossom 边权**（`ai*1_000_000 + 规则 objective*1000 + tiebreak`），AI 分差 1 分压过规则分差 1000 分 |
| 硬约束不可绕过 | `HardFilter.violations` 与 `Weights.pairThreshold` **在 AI 之前**执行；AI 分只作用于已通过门槛的边 |
| 展示分不被幻觉抬高 | `ScoreBreakdown` 保持纯规则分，前端看到的仍是 `match.score`，可解释 |
| 零回归/可回滚 | `aiScores` 为空时 `weight()` 逐位走 M2 原公式；开关 `MATCH_AGENT_ENABLED` 默认 false |
| 四级降级 | L1 网关主备/重试/熔断；L2 分身模拟失败则跳过模拟；L3 裁判输出非法则该对回落规则边权；L4 整批降级返回空 Map，周批照常 PUBLISH |

## 组件

- `domain/matching/MatchingInput`：新增可选 `aiScores`（构造时校验 0-100），保留 7 参数兼容构造与 `withAiScores/withSeed/withPastPairs`。
- `domain/matching/MatchingSolver`：新增 `weight(..., Integer ai)` 与 `aiScore()`；`buildEdges` 在硬约束/门槛之后取 AI 分。
- `match/AiJudge`：模板 1/2 的 system/user prompt、服务端严格校验（复算加权分、dims 四项、topics==3、reason≤40、confidence 范围、模拟对话 3 轮/每句≤45 字/无元信息词）、`sim_report` 结构化留痕、提示词末端脱敏（邮箱/手机号/密钥/链接）。
- `match/AgentRerankService`：只精排规则分 Top-N 对（默认 15）控成本；画像只读 `user_taste` 脱敏产物并二次 PII 扫描；每对留痕到 `recall_candidate.sim_report`；输出 `agentMode/judged/simulated/degraded` 进 `week.stats` 可观测。
- `match/MatchAgentProperties` + `WeekRunner.solve()` 注入点：`MatchingSolver.buildEdges(input)` 用**同一 input** 先建边（保证硬约束口径一致），精排后 `withAiScores` 重新求解。
- `docs/技术方案.md` v3 声明新增第 7 条并把测试基线从 404 更新到 485。

## 过程中修掉的 4 个真问题（都有证据）

1. **硬冲突信号被误丢**：模型自报 99 分但列出硬冲突时，原「自报 vs 复算差 >25 判不可信」会把本该强制 0 的结果判成 null 降级。改为先应用硬冲突再比对，且硬冲突以服务端为准。
2. **reason_text 超长未拦截**：单测数据只有 34 字，最初误判为代码 bug；补齐真实 50 字用例后确认实现正确，同时把断言改成确实越界的数据。
3. **模型把「爱好不同」当 dealbreaker（实测发现）**：真实模型对「攀岩男 × 街潮女」返回 `conflicts:["活动场景差异较大"]`，若照单全收会把 Ditto 最核心的跨表面共鸣直接毙成 0 分。修法：prompt 明确「爱好/场景/专业/作息不同都不是冲突」；服务端只接受**声明过的结构化红线标签**（`parse(content, declared)`），模型自报的泛泛差异一律忽略。同时守住 §8.1（dealbreaker 原文不进提示词）。
4. **降级不留痕**：无画像/裁判非法时直接 `continue`，`sim_report` 为 0，事后无法举证。改为所有降级路径都写 `no-persona` / `invalid-verdict` 留痕。

**另发现并修复的链路缺口**：合成用户由 `SyntheticLoader` 直接插库、不走入驻事件，因此没有 `user_taste`，精排会「能跑但空转」。生产用户由 Step 4 的 AFTER_COMMIT 事件自动补齐，无此问题；仿真/演示数据必须显式补画像。IT 中已区分处理（mock 走 `refreshMissing`，live 用确定性最小画像避免 200 次真实模型调用）。

## 验收

| 项 | 结果 |
|---|---|
| domain 性质测试 | `AiScoreMatchingTest` 6/6：空 AI 分逐位等同 M2、AI 分主导最终配对、满分绕不过未成年硬约束、一人一位、确定性、输入校验、边权公式 |
| 裁判校验单测 | `AiJudgeTest` 10/10：加权复算、硬冲突强制 0、自相矛盾拒绝、畸形/越界拒绝、代码围栏、低置信度降权、对话 3 轮校验、留痕无 PII、提示词脱敏 |
| **真实模型 E2E** | `AgentRerankLiveE2ETest` 1/1（7.95s）：`deepseek-v4-flash` 分身模拟 + 裁判输出通过服务端严格校验；「攀岩男 × 街潮女」得 **74 分**，模型自报 82 被服务端 rubric 复算校正为 74，理由「同是尝鲜型人格」——正是 hash/标签重合做不到的跨表面共鸣 |
| 周批端到端（mock 降级） | `AgentRerankIT`：`state=published, pairs=3, participants=130`，`fully-degraded` 下**不空发**，`sim_report` 5 行留痕，一人一位，`week.stats` 含 agentMode |
| **周批端到端（真实模型）** | 同一 IT 开 `AIDITTO_STEP6_LIVE=1` + `deepseek-v4-flash`：`judged=4 simulated=5 degraded=1 mode=partial`、**`agentAiPairs=4`、AI 分注入 4 行 `final_score`**、`sim_report` 5 行，周批仍 `published`。证明 AI 分真的参与配对、且部分失败时自动降级不空发（此前 mock 下 `fully-degraded` 只验证了兜底，不够） |
| **全量 `mvn verify`（PG）** | **`VERIFY_RC=0`，domain 398 + app 单测 58 + IT 29 = 485 项 0 失败 BUILD SUCCESS**，471 项既有基线零回归 |
| 文档/部署一致性 | 技术方案 v3 声明、`.env.v3.example`、`docker-compose.v3.yml`（cutover 开启精排）同步 |
| 安全 | 全仓扫描 key 命中 0；线上 5 容器 Up、站点 HTTP 200；MySQL/nginx/线上 jar 未动 |

## Step 6 结束时的未完成项（已在第十二～十四节完成，保留作追溯）

- **Step 7 AI 文案**：`MatchCopyWriter` 仍走 `TemplateCopy`，`match_copy.source` 尚未出现 `'ai'` → 已完成，见第十二节。
- **Step 8 反馈学习**：`TasteRewriter` / `ai_pair_memory` / 裁判校准未实现 → 已完成，见第十三节。
- **Step 9 12 周期合成仿真**：隐藏真值、Oracle、指标、对抗用例、Go/No-Go 报告 → 已跑通，结论 NO-GO，见第十四节。
- **结构化红线标签**：`declaredDealbreakers` 传空集 → 已接入 `profile.ai_constraints` 标签白名单。
- **候选池规模**：200 人池仅 64 条边、3 对 → 已在 Go/No-Go 报告列为扩池遗留项，未宣称达标。

---

# 十二、Step 7 AI 文案（2026-11-16 13:xx，master_agent_v3）

## 交付

1. `MatchCopyProperties`（`aiditto.match.copy.*`，默认关闭，逐位等同 M2 模板）。
2. `AiCopyGenerator`：提示词只取 `ProfileFacts` 显式白名单，再叠加一层 PII 打码；**地点由程序决定**，模型不输出 venueId；输出经 `CopyValidator` 全量校验（禁用词/联系方式/真实姓名/未知占位符/编造引用/无效 cites/长度/结构），返回结构化 issues 供重试。
3. `AiCopyService`：子图级重试（默认 2 次），把**规则名 + 具体路径**拼进重试提示；网关 degraded 立即停止重试、不空耗；统计 `copyAttempts / copyAccepted / copyInvalidOutputs / copyDegradedCalls / copyExhausted`。
4. `MatchCopyWriter`：AI 成功用 `'ai'` + `prompt_version`，失败回落 `TemplateCopy`；二次校验兜底；按周批统计 `copyTemplateRate`。
5. `MatchDb.insertCopy` 增加带 source 的重载（旧签名保留）、`mergeWeekStats` 用 JSONB 合并，不覆盖 SOLVE 阶段的 agent 统计；`WeekRunner.copy()` 写统计；`MockLlmModel` 增加按 callPoint 注入响应的能力供 IT 使用。

## 验收（全部实测）

| 项 | 结果 |
|---|---|
| 定向单测 | `AiCopyGeneratorTest` 14 + `AiCopyServiceTest` 9 = **23 全过** |
| 网关/内容闸门 | 非法 JSON、代码围栏、缺字段、超长、禁用词、联系方式、真实姓名、未知占位符、编造引用、无效 cites、数量错误、空输出全部拦截；100 次「首次非法二次合法」→ **重试通过率 100%**；100 次稳定输出 → **模板兜底率 0** |
| 真实模型 E2E | `AIDITTO_STEP7_LIVE=1` 1/1：`deepseek-v4-flash` **1 次调用**即产出通过严格校验的中文文案（headline「周三，见一面聊聊音乐和书」），`degraded=false` |
| **真 PG 周批 IT** | `AIDITTO_STEP7_IT=1` **2/2 通过**：① 全 AI `copies=6 ai=6`、`copyTemplateRate=0.0`、`prompt_version=match-copy-v1`、禁用词与无效引用扫描 = 0；② 模型输出全不可用时 `copies=6 template=6`、`copyTemplateRate=1.0`、`copyInvalidOutputs=6`，周批仍 `published` |
| 可观测 | 两批 `week.stats` 同时含 `pairs / agentMode`（SOLVE）与 `copyAi / copyTemplateRate`（COPY），证明 merge 不覆盖 |

## 过程中修掉的真问题

1. **重试提示缺规则名**：`CopyValidator.Issue.toString()` 只有 `path: detail`，模型第二次仍会犯同类错。改为 `path [RULE]: detail`，测试钉住。
2. **第二次周批配不出对**：`match_side` 的 open 状态与 `pastPairs` 让同周重跑 `pairs=0`。IT 里显式清理上一周批派生态（无外键、不动用户与画像）后再验降级路径。

---

# 十三、Step 8 反馈学习闭环（2026-11-16 14:xx，master_agent_v3）

## 交付

1. **V10 迁移**：`user_taste.weights`（10 维结构化口味权重，与自然语言 `taste_text` 双轨）+ `learned_feedback_cnt`；`taste_log` 加可重入唯一索引与每周计数索引；新增 `judge_calibration`。`AuthIT` 版本期望同步 1–10。
2. **domain 层**：`TasteWeights`（单次只改 1 维、幅度硬夹 ±0.15、Spearman ρ、口是心非收敛、maxDeltaFrom）、`FeedbackSignal`（标签/文本 → 维度与幅度，矛盾信号「待观察」、恶意差评降权）、`HiddenTruth` / `TrueScore` / `Feedback` / `FeedbackOracle` / `SimMetrics`。
3. **app 层**：`TasteRewriter`（Prompt 模板 3 + 服务端强制：每周≤1、版本只追加、旧结论只追加不覆盖、PII/敏感属性扫描、模型全挂走确定性规则、`vector_sync_status='pending'` 触发重算向量）、`FeedbackLearningService`（每周期批量消费，不侵入反馈提交链路）、`PairMemoryService`（双向拒绝永久 forbidden）、`JudgeCalibrationService`（≥30 条才启用，偏置夹 ±10，重启恢复）、`ConstraintTags`（结构化红线标签白名单）。
4. **接线**：`TasteExtractor` 新版本继承已学权重（防止编辑问卷清零学习成果）；`WeekRunner.solve()` 把 `forbiddenPairs` 并入硬过滤；`AgentRerankService` 读 `profile.ai_constraints` 标签进 `AiJudge` 的 declared 集合，并叠加校准偏置；裁判提示词只给标签 id，不送红线原文。

## 验收（全部实测）

| 项 | 结果 |
|---|---|
| 领域单测 | `TasteWeightsTest` 6 + `FeedbackSignalTest` 7 + `SimDomainTest` 5 = **18 全过**（含 A01/A02/A03、Spearman、Oracle 确定性、脏数据标记） |
| **真 PG IT** | `AIDITTO_STEP8_IT=1` **1/1 通过**：`scanned=2 rewritten=2 forbiddenPairs=2 failed=0` |
| 防过拟合 | 新版本 active 恒 1 行、版本 +1、`source_feedback_cnt` / `learned_feedback_cnt` 各 +1、`vector_sync_status='pending'`；**恰好只变 1 个维度且 maxDelta ≤0.15** |
| 可重入 / 限速 | 同一反馈第二次扫描 `scanned=0`；同一用户再造一条反馈被**每周≤1**闸门拦下，版本不变 |
| 配对记忆 | 双方 `again=no` → `ai_pair_memory.forbidden=true`，`db.forbiddenPairs()` 能查到，硬过滤第一道排除（A04） |
| 裁判校准 | 样本 0 条时 `active=false bias=0`，`apply(74)=74`；重启可从 `judge_calibration` 恢复 |
| 模型全挂 | Mock 输出非法时 `TasteRewriter` 走规则改写仍产出新版本，闭环不空转 |
| 迁移 | 应用连 `aiditto_test` 启动，Flyway `Migrating ... version "10 - feedback learning"` 成功 |

## 过程中修掉的 4 个真问题（都有证据）

1. **`FeedbackSignal` 的 terse 判定过宽**：`length<=4` 把「聊得挺好」判成无信息量，矛盾反馈强度反而显得更高。收紧到真正无信息量的回复。
2. **`taste_log.to_version` 是 NOT NULL**：失败留痕写了 NULL → 整批 `BadSqlGrammarException`。改用哨兵 0 表示「未产生新版本」。
3. **`date_trunc('week', ?)` 参数类型推断失败**：PG JDBC 把 `?` 声明成未知类型。改由 Java 按 UTC 算本周一 00:00，SQL 直接比较 `created_at >= ?`。
4. **雪花 ID 经 double 丢失精度（最严重）**：`num(r.get("user_id"))` 把 19 位 ID 转成 double（53 位尾数），导致 `userId:matchId` 匹配不上 reasonTag、`pair_memory` 记错人。全部 ID 改走 `((Number) ...).longValue()`。修后 `rewritten` 从 0 变 2。

---

# 十四、Step 9 12 周期合成仿真与 Go/No-Go（2026-11-16 14:xx）

## 交付

1. `SimDriver`（**test 源，不进生产 jar**）用生产组件跑 12 周期：`RrfFusion` 五路融合 → `MatchingSolver.buildEdges` 硬约束/门槛 → `MatchingInput.withAiScores` → Blossom 全池配对 → `FeedbackOracle` → `FeedbackSignal` + `TasteWeights` 画像重写 → `SimMetrics` 指标。隐藏真值只在 `HiddenTruth`/Oracle 内存可见，绝不进 Candidate、aiScores、文案、日志。
2. `SimulationRunTest` 实跑生成 `docs/eval/见一面-Go-No-Go-报告.md`，闸门实测值与 CI 断言同源；附**不学习影子对照**与 A 类 P0 用例。
3. `docs/技术方案.md` v3 声明补第 8/9/10 条；测试基线更新到 545。

## 实测（200 人 / 12 周期 / seed 20261118 / 口是心非 20%）

| 指标 | 周期 1 | 周期 12 | 闸门 | 结论 |
|---|---|---|---|---|
| taste_recovery（Spearman ρ） | 0.850 | **0.908** | ≥0.70 | PASS |
| 口是心非组收敛 | 0.553 | **1.000** | ≥0.80 | PASS |
| 末周期 regret | 0.078 | **0.096** | ≤0.15 | PASS |
| 冷启动 top1_hit@decile | **0.635** | — | ≥0.35 | PASS |
| 周期12 top1_hit@decile | — | **0.490** | ≥0.60 | **FAIL** |
| regret 单调下降 | — | **5/11** | ≥8/12 | **FAIL** |
| learning_slope | — | **+0.0019** | <0 | **FAIL** |
| P0 对抗用例 | — | **8/8** | 100% | PASS |
| pair 重复 / 独占单路 / 长尾公平 | 0 / 0.442 / 1.000 | 0 / 0.437 / 1.000 | 达标 | PASS |

**总判定：NO-GO**。12/15 闸门达标，3 项与「越匹配越准」直接相关的未达标。

## 诊断（影子对照，最重要的产出）

1. 关掉画像重写的对照组 top1 同样从 0.635 降到 0.505，说明下降主要是**结构性**的（往期配对累积 + Blossom 全局最大权匹配压制「人人都选到自己 top10%」），不是学习把画像学坏。
2. 但学习组末周期相对不学习组的净收益未稳定为正，**「越匹配越准」尚未被证明**。按创始人铁律，这就是 NO-GO；不靠调仿真参数或放宽阈值「做」出达标。
3. 本轮修掉的 4 个真实建模/产品缺陷：冷启动画像改为由问卷显性答案生成（对齐 Step 4）；契合函数从「画像相似度」改为「偏好 × 属性」（对齐 §3.3）；学习结果改走召回漏斗而非直接给全池边打分（对齐「召回是命门」）；真值兼容度在 12 周期内固定、主观噪声只落在反馈观测里。
4. 期间还修掉一个仿真工程错误：Sort comparator 里调 `rnd.nextDouble()` 违反排序契约；以及把 `Cohort.SYNTHETIC` 当成可互相配对的池（生产规则是「合成只与演示账号配」），导致整池被 COHORT 排空。

## 阶段 B 剩余工作（NO-GO 出口条件）

1. 让反馈学习信号真正进入**召回权重与裁判校准**：当前 `user_taste.weights` 只影响语义召回排序，未直接优化「预测分 vs 观测反馈」的排序目标；`JudgeCalibrationService` 的偏置未回灌精排。
2. **扩池**到 500 / 2000 并补 3:7 性别比组，降低全局最大权匹配对单人 top-decile 的结构性压制。
3. 增加**固定候选池**的补充评估，剥离 pastPairs 累积效应后单独量化学习净收益。
4. 多种子重复回放 + 成对显著性检验，给置信区间而非单次点估计。
5. `regret 单调下降 ≥8/12`、`learning_slope < 0`、`周期12 top1_hit ≥0.60` 全部达标后重新签发报告。**在此之前不进灰度、不接真实用户试错。**

## 全量验收与边界

| 项 | 结果 |
|---|---|
| **全量 `mvn verify`（PG16）** | **domain 430 + app 单测 83 + IT 32 = 545 项，0 失败，BUILD SUCCESS**（16 项按设计跳过：真实 LLM / Qdrant / 显式开关） |
| 显式打开的专项验收 | Step 7 IT 2/2、Step 7 真实模型 E2E 1/1、Step 8 IT 1/1、Step 9 仿真 1/1（报告 NO-GO） |
| 密钥安全 | 全仓 `sk-` 长串扫描命中 **0**；密钥只在服务器 `/opt/aiditto-v3/.env`（chmod 600），未进仓库/文档/日志 |
| 线上零中断 | `aiditto-api` Up 2d (healthy) / `aiditto-web` Up 3d / `ditto-mysql` Up 9d / PG16 / Qdrant 全部 Up，站点 HTTP 200；MySQL 版 nginx 与线上 jar 全程未动 |

---

# 十五、阶段 B 复核与决策点 D6（2026-11-16 17:xx，master_agent_v3）

Step 6–9 完成后继续打磨阶段 B，目标是查清「越匹配越准」未被证明的根因。结论：**NO-GO 不变，但根因从「结构性噪声」收敛到「架构级链路问题」，需要创始人拍板 D6。**

## 一、修掉的真实缺口

1. **候选方读原始问卷，反馈学习丢了一半**：反馈链路双向更新 `learned[a]` / `learned[b]`，但仿真的召回与精排始终读 `stated[b]`（冷启动问卷、固定不变）。生产 Step 8 会写 `user_taste` 新版本并置 `vector_sync_status='pending'` 重算向量，下一周期召回与 `AgentRerankService.loadPersonas` 读的都是新版本。已改为双方都读 `learned`，对齐生产。
2. **仿真 Candidate 全是默认占位符**：所有人属性相同 → 规则分全过门槛 → 200 人每周全配 100 对；生产 Step 6 IT 里 130 人只配 3 对。新增 `projectCandidate()`，只从**问卷观测画像**（`observed`，绝不含 `HiddenTruth` 真值）投影爱好 / 节奏 / 期待 / 预算 / 年级 / 地点，让规则门槛真正生效。配对数 97–100/200，进一步确认生产里配对少主因是 cohort 隔离与池构成，不是分数门槛。
3. **新增可计算的诊断指标**：`SimMetrics.rankCorrelation` + `CycleMetric.predTrueCorr` —— 在当周被召回并精排的候选边上，算「系统预测分 vs 隐藏真值」的 Spearman ρ。它把「学习信号问题」与「匹配结构问题」分开：ρ 升而 top1 不升 = 结构问题；ρ 不升 = 学习信号问题。单测 6 项全过。

## 二、三组对照实测（200 人 / 12 周期 / seed 20261118）

| 组别 | 末周期 top1 | 末周期 regret | 末周期 pred-true ρ | taste_recovery |
|---|---|---|---|---|
| 主回放（学习 + pastPairs） | 0.479 | 0.098 | 0.814 | 0.907 |
| 影子对照（不学习 + pastPairs） | 0.423 | 0.107 | 0.828 | — |
| **固定池对照（学习，无 pastPairs）** | **0.495** | **0.086** | **0.785** | 0.907 |
| **固定池对照（不学习，无 pastPairs）** | **0.570** | **0.071** | **0.797** | — |

**判读**：剥掉往期配对累积效应后，学习组在 top1 / regret / ρ 三项上**全部落后于不学习组**（−0.075 / +0.015 / −0.012）。因此不是「好对被逐周耗尽」的结构性问题，而是**反馈学习信号没有对准排序目标**：画像的维度序确实更像真值了（taste_recovery 0.907），却没有转化成更好的候选排序。

## 三、一次失败实验（已回滚，留证据链）

按 v3 方案 §4.1 字面，把 `reason_tag` 从「viewer 自己的 top/bottom 权重」改成「这对真值贡献最大/最小维度」，并把 `bad:<隐性维度>` 从降权改成升权（贴合 Prompt 模板 3 的字面语义）。12 周期实测：

- taste_recovery **0.907 → 0.775**
- pred-true ρ **0.814 → 0.671**
- 固定池末周期 top1 净收益 **−0.135**

明显更差，已回滚到已验证基线。对照实现保留在 `TrueScore.topContributionDim/worstContributionDim` 作证据链，不参与默认回放。这次实验否定了「继续在 reason_tag 来源 / 调整方向 / 学习幅度上试错能解决」的假设。

## 四、决策点 D6（阻塞项，等创始人拍板）

当前闭环是「反馈 → 调用户画像 1 个权重 → 间接影响 compatibility 边权」。固定池对照与失败实验共同证明这条链路不保证排序变准，而画像还原度与全部防过拟合闸门都过了。**这不是参数问题，是链路设计问题。**

| 选项 | 内容 | 建议 |
|---|---|---|
| **A** | 引入 learning-to-rank：`user_taste.weights` 从「偏好画像」升级为「排序权重」，直接参与 `AiJudge` 边权计算；需重构 Step 6/8 边权契约 | **推荐**，但属架构级改动，等创始人确认后动工 |
| B | 保留现闭环，把「越匹配越准」证明推迟到真实反馈阶段 | 不建议，违背「真实用户只用于验证、不用于试错」铁律 |

D6 未决前只做不依赖它的验证（扩池 500/2000 + 3:7 性别比、多种子成对显著性检验），**不进灰度、不接真实用户试错**。

## 五、验收

| 项 | 结果 |
|---|---|
| 全量 `mvn verify`（PG16） | **domain 430 + app 单测 83 + IT 32 = 545 项，0 失败，BUILD SUCCESS**（复核后复跑仍全绿） |
| 报告 | `docs/eval/见一面-Go-No-Go-报告.md` 重签为 8 节，新增预测质量诊断、固定池对照、决策点 D6；总判定 **NO-GO** |
| 密钥 / 线上 | 全仓 `sk-` 长串扫描命中 0；线上 5 容器 Up、站点 HTTP 200，MySQL 版 nginx / 旧 jar 未动 |
