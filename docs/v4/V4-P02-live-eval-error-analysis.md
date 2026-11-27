# V4-P02 真实模型失败分析

日期：2026-11-27。分类依据是全量 jsonl 里的原始键和拒绝原因。冒烟不重复计数。标签没有改。

| 类别 | LongCat | DeepSeek |
| --- | --- | --- |
| 1. 模型自身理解错误 | 14 | 49 |
| 2. 特征注册表缺少对应特征码 | 2 条样本带有未知码；另有 12 次 `source` 拒绝 | 6 条样本的主错误是未知码；另有 55 次 `source` 拒绝 |
| 3. Prompt 表达不清楚 | 本轮不改 Prompt，不能从一次调用单独证明 Prompt 是原因 | 同左 |
| 4. 证据引用错误 | 0 条空引用或引用不在上下文中 | 0 |
| 5. 服务端语义规则 | 2 条正例键被 `unsupported` 拒绝，尚未进入封闭短语判断 | 正例键同样被 `unsupported` 拒绝；样本主类记在未知码上 |
| 6. 用户表达本身不确定 | 仍按冻结注释保留，不新判 | 同左 |
| 7. 数据集标注歧义 | 见 label-review，没有改标签 | 同左 |
| 8. 隐私策略导致输入不可用 | 6 | 6 |
| 9. 模型格式错误 | 0 | 0 |
| 10. 模型服务失败 | 0 | 0 |

LongCat 另有 42 条调用保持空特征，且预期本来就是空。DeepSeek 只有 5 条是这种空结果：L45、L47、L49、L57、L58。

## 六条正例

| 样本 | 预期 | LongCat 原始键 | LongCat 结果 | DeepSeek 原始键 | DeepSeek 结果 |
| --- | --- | --- | --- | --- | --- |
| L03 | `SELF\|self.pace\|slow` | 含该键，另有 `partner.pace` 和爱好 | `unsupported` | 含该键，另有未知码和爱好 | `unsupported`，样本还带未知码 |
| L04 | `SELF\|self.pace\|slow` | 含该键；偏好用了 `pref.initiative` | 正例键 `unsupported`，偏好码未知 | `social.warmup_pace`、`interaction.initiative` | 未知码 |
| L06 | `PREFERENCE\|partner.social.initiative\|high` | 空 | 模型未输出 | `partner.initiative=more_proactive` | 未知码，取值也不是 `high` |
| L09 | `SELF\|self.pace\|slow` | 只有该键 | `unsupported` | `social.warmup_pace=slow` | 未知码 |
| L12 | `SELF\|self.communication\|quiet` | `self.communication.style=quiet` | 未知码 | `social.talkativeness=low` | 未知码，取值也不是 `quiet` |
| L18 | `SELF\|self.communication\|frequent` | 空 | 模型未输出 | `social.talkativeness=high` | 未知码，取值也不是 `frequent` |

LongCat 原始 Recall 是 3/6。DeepSeek 是 1/6。校验之后两边都是 0/6。

## 规则损失和模型损失

正确键被拒绝的是 LongCat 的 L03、L04、L09，以及 DeepSeek 的 L03。拒绝原因是 `unsupported`。校验器在进入 `StatementSemantics` 之前，要求一条陈述引用的类型和特征分节一致，并且 `behaviorKind` 为空。这四个候选都同时引用了自身陈述和偏好陈述。DeepSeek 的 L03 还写了 `SELF_REPORTED`。因此封闭短语没有机会接受或挂起它们。全量文件里的挂起数是 0。

其余损失来自模型输出：

- LongCat 对封闭短语 L06、L18 返回空列表。
- LongCat 把 L12 写成不存在的 `self.communication.style`。
- DeepSeek 把 6 条正例里的 5 条写成注册表之外的特征码，取值也经常对不上 `high`、`quiet`、`frequent`。
- 两个模型都把合成上下文里的爱好 `reading` 抽成 `lifestyle.hobbies`。该特征码存在，但注册表不允许模型来源，拒绝原因是 `source`。LongCat 12 次，DeepSeek 55 次。

`unsupported` 和 `source` 是注册表与引用资格。未知特征码是注册表没有这个码。空输出、错码和错取值是模型输出。本轮没有为了提高 Recall 去改短语表、注册表或门槛。

## 其余失败样本

LongCat 的模型错误样本是 L02、L05、L06、L07、L10、L13、L14、L18、L23、L25、L48、L51、L59、L60。其中 L06 和 L18 是正例漏报，其余是预期为空时仍给出特征。L59 的原始输出里没有被接受的硬约束。

DeepSeek 除 6 条正例和 5 条空结果外，其余 49 条调用都给出了非预期特征。完整的 `rawOutput`、拒绝原因、延迟和 token 在各自的 `samples-full.jsonl`，一行一条。文件已在服务器上对过密钥，没有把密钥写进来。
