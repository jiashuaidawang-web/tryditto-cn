# V4-P02 画像评估报告

日期：2026-11-26。评估对象是 `ProfileEvalCases.all()` 经过 `ProfileExtractionValidator` 和 `ProfileMerger` 的服务端结果。标签由夹具作者写入。没有调用真实模型，也没有让模型生成标签。

样本 120。开发集 96，保留集 24。划分规则是下标模 5 为 0 的进入保留集。

## 实测

命令是在领域模块编译后执行 `ProfileEval.score(ProfileEvalCases.all(), FeatureRegistry.v1())`。

| 指标 | 开发集 | 保留集 |
| --- | --- | --- |
| 样本 | 96 | 24 |
| 真阳性 / 假阳性 / 假阴性 | 40 / 0 / 0 | 10 / 0 / 0 |
| Precision | 1.0 | 1.0 |
| Recall | 1.0 | 1.0 |
| 被接受的模型特征键 | 40 | 10 |
| 分节混淆 | 0 | 0 |
| 混淆率 | 0.0 | 0.0 |
| 原始候选 | 80 | 20 |
| 无证据原始候选 | 4 | 1 |
| 无证据生成率 | 0.05 | 0.05 |
| 无效引用原始候选 | 20 | 5 |
| 无效引用率 | 0.25 | 0.25 |
| 无证据被接受 | 0 | 0 |
| 无效引用被接受 | 0 | 0 |
| 冷启动样本 / 正确保持空 | 8 / 8 | 2 / 2 |
| UNKNOWN 保留 | 1.0 | 1.0 |
| 硬约束请求 | 8 | 2 |
| 硬约束被接受 | 0 | 0 |
| 敏感样本 / 泄露 | 8 / 0 | 2 / 0 |
| 结构化尝试 / 成功 | 88 / 88 | 22 / 22 |
| 结构化成功率 | 1.0 | 1.0 |
| 坏格式样本 / 被当成成功 | 8 / 0 | 2 / 0 |
| 坏格式成功率 | 0.0 | 0.0 |

无证据率和无效引用率的分母是夹具故意放入的原始候选。它们描述「坏候选出现了多少」，被接受数才是安全结果。被接受数都是 0。

开发集打分耗时 73607753 纳秒，保留集 9428123 纳秒。这是内存中的指标循环，不是模型提取耗时。

`ProfileEvalThresholds` 的门槛没有下调。`ProfileEvalTest` 断言两个划分都达到门槛。

## 真实模型

真实模型理解质量：**NOT_RUN**。

120 份工程集的上表保持原样。它是手工标签经过服务端校验后的一致率，不是供应商模型的理解准确率。本轮没有降低这些测试。

独立集合是 `ProfileLiveEval`，`live-profile-v1`，66 条。数据集哈希 `4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c`。Prompt 版本 `profile-extract-v1`。Prompt 哈希 `bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195`。

阻塞：`aiditto.llm.mode` 默认是 mock，`LLM_PRIMARY_API_KEY` 为空。仓库里没有可安全使用的非生产真实模型配置。没有读取生产用户资料，也没有提交 API Key。因此没有第二家供应商的替换运行。

原始输出和校验后结果都没有运行，不能用上表填进下面任何一格。

| 项 | 模型原始输出 | 服务端校验后 |
| --- | --- | --- |
| provider | NOT_RUN | NOT_RUN |
| model | NOT_RUN | NOT_RUN |
| Precision | NOT_RUN | NOT_RUN |
| Recall | NOT_RUN | NOT_RUN |
| SELF/PREFERENCE 混淆率 | NOT_RUN | NOT_RUN |
| 无证据推断率 | NOT_RUN | NOT_RUN |
| 非法硬约束接受 | NOT_RUN | NOT_RUN |
| 未授权外发 | NOT_RUN | NOT_RUN |
| 敏感信息泄露 | NOT_RUN | NOT_RUN |
| 结构化输出成功率 | NOT_RUN | NOT_RUN |
| P50 / P95 延迟 | NOT_RUN | NOT_RUN |
| Token | NOT_RUN | NOT_RUN |
| 费用 | NOT_RUN | NOT_RUN |

实验门槛写在 `V4-P02-eval-design.md`。它们不是这次的结果。模型理解仍是 NOT_RUN，不写成 GO，也不用工程夹具的 1.0 代替。
