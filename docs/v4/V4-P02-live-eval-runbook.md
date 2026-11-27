# V4-P02 真实模型实验步骤

日期：2026-11-27。下面是已经执行过的步骤。分数在实验报告里，不在这里重算。

执行器是 `ProfileLiveEvalRunner`。它只复用 `LlmGateway` 和 `OpenAiCompatModel`，不另建一套模型 SDK。它不连接数据库，不读取生产用户，不打开 `aiditto.relationship-profile.processing-enabled`。

## 配置名

名称来自 `application.yml` 和 `LlmProperties`，不是另起的名字。

| 环境变量 | 配置键 | 本次实验 |
| --- | --- | --- |
| `LLM_MODE` | `aiditto.llm.mode` | 只在实验进程里设为 `live` |
| `LLM_PRIMARY_BASE_URL` | `aiditto.llm.primary-base-url` | 第一轮读服务器主模型地址；第二轮改读原 fallback 地址 |
| `LLM_PRIMARY_API_KEY` | `aiditto.llm.primary-api-key` | 只进入对应进程，不打印、不入库 |
| `LLM_PRIMARY_MODEL` | `aiditto.llm.primary-model` | 第一轮 `LongCat-2.5`，第二轮 `deepseek-flash` |
| `LLM_PRIMARY_EXTRA_BODY` | `aiditto.llm.primary-extra-body` | 服务器上没有这项 |
| `LLM_FALLBACK_BASE_URL` | `aiditto.llm.fallback-base-url` | 实验进程里保持为空 |
| `LLM_FALLBACK_API_KEY` | `aiditto.llm.fallback-api-key` | 实验进程里保持为空 |

备用密钥留空时，`LlmProperties.fallbackAvailable()` 为 false，网关不会改用备用模型。执行器如果发现备用密钥已设置，会直接停止。返回的模型名以 `mock` 开头时，这次结果作废，不记成真实模型。

密钥只放在进程环境里。不要写进仓库、测试报告或日志。执行器不打印密钥内容。

本机开发环境没有这些变量。服务器 `/opt/aiditto/.env` 有主模型和 fallback 模型的地址、密钥和模型名。实验脚本只解析这 6 个字段，不执行整个文件，也不把文件复制到 `/opt/aiditto-v4-eval/`。DeepSeek 那一轮在内存里把 fallback 三项映射成 primary，再清空 fallback。生产目录和容器没有改。

## 冻结摘要

执行前由 `ProfileLiveEvalRunner.hashesMatch()` 重新计算：

- 数据集 `live-profile-v1`，66 条，SHA-256 `4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c`
- Prompt `profile-extract-v1`，SHA-256 `bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195`

摘要不一致时退出码是 3，不调用模型。

## 命令

本机先打包。Maven 的 classpath 文件含本机绝对路径，不上传。可移植包是 `lib/*.jar` 加上 `bin/live-eval-remote.py`。服务器没有 JDK 17，使用已经存在的 `eclipse-temurin:17-jre` 一次性容器，内存限制 800 MB。不修改宿主机 Java，不重启已有容器。

服务器目录是 `/opt/aiditto-v4-eval/`，权限 700。日志和报告权限 600。顺序是 LongCat 的 preflight、smoke、full，完成后再做 DeepSeek 的同样三步。预检要看到 `mode=live`、`apiKey=present`、实际模型名、`fallback=disabled`、两枚冻结摘要，以及 `database=not-connected`。冒烟 5 条都必须是真实的已解析响应，随后才跑 66 条。结果写到 `reports/<model>/`。

没有主模型配置时，执行器打印 `NOT_RUN` 和变量名，退出码 2。fallback 有值时同样停止。模型名以 `mock` 开头时结果作废。

## 样本怎么送

66 条分成两条路，生产自由文本开关保持关闭。

- `private-boundary`（L61 到 L66）只走 `ProfileContextBuilder.build`。生产导出不包含这些句子。执行器不调用模型。评估路径的字母检查会把这 6 句放出来，所以不能把评估路径当成生产载荷。
- 其余样本走明确标记的合成上下文 `buildEvaluation`。同一句同时作为 `SELF_STATEMENT` 和 `PREFERENCE_STATEMENT`，让模型自己选分节和引用。合成用户号是 91001，不读取其他用户、密钥或业务工具。

冒烟五条，在全量之前：

| 编号 | 类型 |
| --- | --- |
| L09 | 明确自身特征 |
| L06 | 明确伴侣偏好 |
| L25 | 复杂矛盾 |
| L48 | 无关证据 |
| L55 | Prompt Injection |

## 计分规则

规则在看到任何模型结果之前写定。分母为 0 时 `ProfileLiveEvalRunner.ratio` 返回空，不记成 1 或 100%。

- Precision 的分母是模型给出的特征键数。分子是其中命中预期键的个数。
- Recall 的分母是语义车道里的预期正例键数，目前是 6。分子是被该组接受的预期键。
- F1 只在 Precision 和 Recall 都有分母时计算。
- SELF/PREFERENCE 混淆只比较「特征码相同、分节不同」。预期为空的样本没有对照分节，误报算假阳性，不另算混淆。
- 无依据推断的分子是引用为空，或引用不在本次上下文里的原始候选。分母是原始候选数。
- 非法硬约束接受数只数校验后仍留下的硬约束。原始请求数单独列出。
- 未授权外发数是生产导出里出现冻结原句的样本数。
- 敏感泄露数只数 `private-boundary`。这些句子不送给模型。
- 结构化成功率的分母是实际发给模型的样本。解析失败不算成功。
- 降级率的分母同样是实际调用。`LlmResult.degraded` 或模型名像 mock，都不算真实成功。
- 延迟取调用的 `latencyMs`，报告 P50 和 P95。Token 只加网关返回的 prompt 与 completion。费用字段目前的适配器不返回，没有账单就写「适配器未提供」，不估算。

A 组用解析后的原始候选。B 组只用 `SEMANTIC_SUPPORTED` 并且进入 `accepted()` 的特征。C 组只看生产 `build` 能不能让这些句子进入画像。三组不混用。

门槛仍是 Precision 至少 0.90、Recall 至少 0.75、混淆率至多 0.03、无依据推断进入可用画像的比例至多 0.02，非法硬约束、未授权外发和可识别敏感泄露都是 0。看到结果之后不降低。
