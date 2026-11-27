# V4-P02 真实模型实验报告

日期：2026-11-27。LongCat 与 DeepSeek 各完成一次独立全量。两次都使用服务器上已经配置的共享额度；本轮任务明确授权用这套配置跑合成样本。仓库里没有单独的非生产密钥。Prompt、冻结标签、`StatementSemantics`、匹配算法和实验门槛都没有改。

实验进程只拿到 `LLM_MODE=live` 和对应模型的地址、密钥、模型名。没有加载数据库密码、`DATA_KEY` 或 `HASH_PEPPER`。Fallback 在进入 Java 进程前被清掉。生产容器没有重启：`aiditto-api`、`aiditto-pg16`、`aiditto-qdrant`、`aiditto-web` 保持原状态。没有连接生产数据库，也没有部署业务服务。

计分只使用全量文件。冒烟的 5 条只确认端点返回了可解析、非 mock、非降级的响应；同一条在温度 0 下两次调用仍可能不同，所以冒烟数字不并入全量。

| 项目 | LongCat | DeepSeek |
| --- | --- | --- |
| 模型名 | `LongCat-2.5` | `deepseek-flash` |
| 地址主机 | `api.longcat.chat` | `api.deepseek.com` |
| 数据集 / Prompt | `4d4c9b07…dc1c` / `bfbbcf69…0195` | 相同 |
| 样本 | 66 | 66 |
| 实际请求 | 60 | 60 |
| 未发送 | 6，`private-boundary` | 6，同一原因 |
| 结构化成功 | 60/60 | 60/60 |
| 降级或 mock | 0 | 0 |

未发送的 6 句是 L61 到 L66。生产导出让它们不可引用，评估路径却能看见原句，所以执行器不调用模型。这 6 句不算模型答对。

## A. 模型原始输出

原始键是解析后的 `section|featureCode|proposedValue`。Recall 分母是 6 条预期正例。

| 指标 | LongCat | DeepSeek |
| --- | --- | --- |
| Precision | 3/31 = 0.097 | 1/86 = 0.012 |
| Recall | 3/6 = 0.500 | 1/6 = 0.167 |
| F1 | 0.162 | 0.022 |
| SELF/PREFERENCE 混淆率 | 0/9 = 0 | 0 |
| 无依据推断率 | 0/31 = 0 | 0/86 = 0 |
| 无效证据引用率 | 0/31 = 0 | 0/86 = 0 |
| 结构化输出成功率 | 60/60 = 1 | 60/60 = 1 |

两个模型的原始 Recall 都低于 0.75。LongCat 命中的 3 条是 L03、L04、L09，键都是 `SELF|self.pace|slow`。L06、L12、L18 没有给出预期键；L06 和 L18 的原始特征列表是空的。DeepSeek 只命中 L03 的同一条键，其余 5 条用了注册表里不存在的特征码。

混淆率只统计「特征码相同、分节不同」。这次没有这种键。预期为空的误报算假阳性：LongCat 有 12 条，DeepSeek 有 49 条。

合成上下文里固定带了爱好 `reading`。两个模型都多次把它抽成 `lifestyle.hobbies|reading`。这会计入原始分母。注册表不允许模型来源写入该特征，所以它停在原始组。

## B. 服务端校验之后

`accepted()` 里没有任何特征。校验后的 Precision 分母为 0，按冻结规则记为空，不记成 1。

| 指标 | LongCat | DeepSeek |
| --- | --- | --- |
| Precision | 空（0 个接受键） | 空（0 个接受键） |
| Recall | 0/6 = 0 | 0/6 = 0 |
| F1 | 空 | 空 |
| 支持特征 | 0 | 0 |
| 挂起特征 | 0 | 0 |
| 拒绝特征 | 31 | 86 |
| 非法硬约束接受数 | 0；原始硬约束请求也是 0 | 0；原始硬约束请求也是 0 |
| 无依据推断接受数 | 0 | 0 |

校验没有给出一个更高的 Precision。它把 LongCat 的 3 个命中和 28 个误报一起拒绝，把 DeepSeek 的 1 个命中和 85 个误报一起拒绝。Recall 从 0.500 降到 0，以及从 0.167 降到 0。

这 4 个原始命中的拒绝原因都是 `unsupported`，发生在 `StatementSemantics` 之前。模型把同一句的自身陈述引用和偏好陈述引用一起放进 `evidenceRefs`。自身特征遇到偏好陈述，资格检查直接失败。DeepSeek 还在自身特征上填写了 `behaviorKind=SELF_REPORTED`，陈述类证据要求这个字段为空。全量结果里没有 `SEMANTIC_SUPPORT_PENDING`。

封闭短语的本地上界仍是 4/6。那是「只引用匹配的那一种陈述」时的规则上界。这次模型没有交出这样的引用，所以上界没有变成实际接受数，也没有把门槛从 0.75 下调。

## C. 生产输入下的覆盖

这一组不调用模型。全量过程中对 66 句都做了生产 `build`。

- 生产导出包含原句：0/66。
- 6 条预期正例的生产可写入数：0/6。
- 未授权外发：0。可识别敏感句进入生产导出：0。

C 组的 0 覆盖是生产自由文本策略，不并入 A 或 B。

## D. 调用消耗

适配器只返回 prompt 与 completion token，没有金额。费用写「适配器未提供」，不估算。

| 指标 | LongCat | DeepSeek |
| --- | --- | --- |
| 请求 / 成功 / 失败 | 60 / 60 / 0 | 60 / 60 / 0 |
| P50 / P95 | 2320 ms / 12788 ms | 1002 ms / 1691 ms |
| Input / Output token | 28836 / 2280 | 30221 / 6144 |
| 费用 | 适配器未提供 | 适配器未提供 |

## 判断

原始理解没有达到 Recall 0.75，校验后 Recall 为 0。真实模型理解维持 **NO-GO**。生产启用和算法质量维持 **NO-GO**。门槛没有改。

逐条结果在 `docs/v4/live-eval-results/longcat/samples-full.jsonl` 和 `docs/v4/live-eval-results/deepseek/samples-full.jsonl`。两边分开统计。
