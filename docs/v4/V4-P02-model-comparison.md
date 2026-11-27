# V4-P02 LongCat 与 DeepSeek 对照

日期：2026-11-27。两次运行使用同一数据集、同一 Prompt、同一校验器和同一门槛。DeepSeek 在独立进程里被映射成主模型，然后清空 fallback。它不是 LongCat 失败后的备用结果。数字来自两份 `summary-full.json`，没有合并。

## 原始能力

| 指标 | LongCat-2.5 | deepseek-flash |
| --- | --- | --- |
| 原始 Precision | 3/31 = 0.097 | 1/86 = 0.012 |
| 原始 Recall | 3/6 = 0.500 | 1/6 = 0.167 |
| 原始 F1 | 0.162 | 0.022 |
| 能否达到 Recall 0.75 | 否 | 否 |
| 结构化成功 | 60/60 | 60/60 |
| 空结果且预期为空 | 42/54 | 5/54 |
| P50 / P95 | 2320 / 12788 ms | 1002 / 1691 ms |
| Input / Output token | 28836 / 2280 | 30221 / 6144 |

LongCat 更常保持沉默，原始命中更多，延迟更高，输出 token 更少。DeepSeek 几乎每条都给特征，原始命中更少，延迟更低，输出 token 更多。

## 校验之后

| 指标 | LongCat-2.5 | deepseek-flash |
| --- | --- | --- |
| 接受数 | 0 | 0 |
| Precision | 空 | 空 |
| Recall | 0/6 | 0/6 |
| 相对原始 Recall 的损失 | 0.500 | 0.167 |
| 挂起 | 0 | 0 |
| 拒绝 | 31 | 86 |

校验没有把 Precision 提升成一个新的分数。分母变成 0 之后，冻结规则要求这个比例留空。被一起去掉的包括全部误报，也包括 LongCat 已经写对的 3 个键和 DeepSeek 写对的 1 个键。

## 差异从哪里来

LongCat 能写出注册表里的 `self.pace=slow`，但引用同时指向两种陈述，资格检查失败。它没有写出 L06 和 L18 的封闭短语。

DeepSeek 更常发明特征码，例如 `social.warmup_pace`、`partner.initiative`、`social.talkativeness`，并给陈述特征填 `SELF_REPORTED`。即使 L03 写对了 `self.pace=slow`，引用和 `behaviorKind` 仍使它在语义判断前被拒绝。

两边都把评测上下文自带的 `reading` 抽成爱好特征。这是原始 Precision 的共同减项，也是 `source` 拒绝的主要来源。

封闭短语上界 4/6 仍然低于 0.75。本次实际接受数是 0/6，因为正确候选没有走到短语判断。门槛保持 Precision ≥ 0.90、Recall ≥ 0.75。两个模型都没有通过。
