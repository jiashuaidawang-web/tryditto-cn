# V4-P02-OPT 语义验证器

`StatementSemantics` 的封闭短语上界仍是 4/6，本轮没有加短语。E4 在 E3 的提取结果上增加一个独立验证器，复用 `LlmGateway`，Prompt 是 `profile-verify-v1`。

摘要：`9f801f0c9317ec8593e13a8dd2b8bc24818ed8c4c4893111f4a7167601dd83b4`

完整正文在 `docs/v4/live-eval-results/opt/prompts/profile-verify-v1.txt`。

## 输入和输出

验证器看到原始片段、候选分节、特征码、取值、该特征的语义定义、合法枚举，以及适用场景说明。它看不到提取器自报的置信度。输出只能是 `SUPPORTED`、`CONTRADICTED`、`INSUFFICIENT`、`AMBIGUOUS`，外加证据引用和一句短理由。

它不能改写用户特征值，不能授予确认状态，不能生成硬约束。调用失败、输出无法解析，或结论不是 `SUPPORTED` 时，候选保持 `PENDING`，不进入实验接受集。

执行顺序是：先做确定性校验。结构不合法、越权、敏感或硬约束违规的候选直接拒绝，不调用验证器。封闭规则已经明确支持的候选走原路径。只有结构合法且语义仍为 `SEMANTIC_SUPPORT_PENDING` 的候选才调用验证器。全部结果留在离线实验，不进匹配评分，也不进生产画像。

## LongCat 在 v1 上的验证调用

全量提取 60 次，验证调用 9 次。标签是 8 次 `SUPPORTED`、1 次 `INSUFFICIENT`。验证延迟合计 20985 ms。这 8 次同意带来的实验接受键是 12 个，其中 6 个对上冻结预期，6 个对不上。

对上的新增是 L03、L04 上冻结标签所要求的 `SELF|self.pace|slow`。同时被同意、但按冻结标签算错的包括：

- L03 额外的 `PREFERENCE|partner.pace|fast`
- L04 额外的 `PREFERENCE|partner.social.initiative|high`
- L16 条件句上的 `SELF|self.communication|quiet`
- L22 转述他人评价上的 `SELF|self.communication|quiet`
- L28 矛盾句上的 `PREFERENCE|partner.social.initiative|high`
- L51 诱导句上的 `SELF|self.social.initiative|high`

L28 是 6 条矛盾样本里唯一进入实验接受集的一条，矛盾保留率因此是 5/6。L51 没有用户陈述证据，验证器仍返回 `SUPPORTED`。

因此 E4 把实验 Recall 从 4/6 抬到 6/6，同时把实验 Precision 从 4/4 降到 6/12。验证器同意没有被当成客观正确。这一阶段不作为后续对照，也不在保留集上重跑。
