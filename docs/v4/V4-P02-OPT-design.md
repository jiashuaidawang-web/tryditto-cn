# V4-P02-OPT 设计

真实模型基线是 `a1d9a0813e34f5c5a82d0ef41dce25887127d449`。本轮只做可分开复现的消融，用来看特征契约、证据上下文、事实分区和独立语义验证各自改变了什么。生产匹配、S01 发布闸门、自由文本外发、画像处理和召回都没有打开。冻结的 live-profile-v1 标签、`profile-extract-v1` 正文和 `StatementSemantics` 短语表没有改。

## 阶段

| 阶段 | 相对上一阶段只增加的东西 | Prompt | 证据上下文 | 校验 |
| --- | --- | --- | --- | --- |
| E0 | 已冻结基线，本轮不重跑 | `profile-extract-v1` | 同一句话复制成 SELF 与 PREFERENCE | 原校验 |
| E1 | 注入模型可提取特征契约 | `profile-extract-v2` | 与 E0 相同，并带上确定性爱好 `reading` | 与 E0 相同 |
| E2 | 一个来源一个证据身份 | `profile-extract-v3` | 一条 `USER_STATEMENT`，要求 `evidenceSpan` | 保留权限、来源、硬约束和隐私边界 |
| E3 | 把已知事实从提取任务里拿出去 | `profile-extract-v4` | 与 E2 相同，但不发送爱好 | 与 E2 相同 |
| E4 | 对挂起候选调用独立验证器 | 提取仍用 v4，验证用 `profile-verify-v1` | 与 E3 相同 | 结构非法直接拒绝；封闭规则能确定的走原路径；其余才验证 |
| E5 | 用 E1–E4 里安全侧最好的 E3，在保留集上测一次 | 与 E3 相同 | 与 E3 相同 | 与 E3 相同 |

E1–E4 的主实验是 LongCat-2.5。选出 E3 之后，DeepSeek-flash 在同一 66 条上跑 E3。保留集只跑了一次 LongCat E3，没有按保留集结果改 Prompt、规则或标签。

开发集 D001–D120 没有参与调参。

## 运行边界

实验程序是 `ProfileAblationRunner`。E0 仍由 `ProfileLiveEvalRunner` 负责。服务器目录是 `/opt/aiditto-v4-eval/`，Java 跑在一次性的 `eclipse-temurin:17-jre` 容器里。脚本只读取所选模型的地址、密钥和模型名，子进程不接收 fallback、数据库或应用密钥。日志和报告在写出前做密钥扫描。生产容器没有重启。

契约渲染按 `featureCode` 排序。`Map.copyOf` 的遍历顺序带进程内随机盐，不排序的话同一份源码每次启动都会得到不同的 Prompt 摘要。排序只固定展示顺序，不增删特征。

## 指标口径

以下口径在保留集运行前已经写进 `ProfileAblationRunner`，保留集之后没有改。

分母为 0 时比值为空，不记成 1。原始精确键和实验接受集分开报。

| 指标 | 分子 | 分母 |
| --- | --- | --- |
| Schema Compliance Rate | 解析成功的调用 | 实际模型调用 |
| Registered Feature Code Rate | 注册表里存在的特征码出现次数 | 原始候选数 |
| Valid Enum Rate | 取值属于该特征合法枚举的次数 | 已注册特征码出现次数 |
| Evidence Reference Validity | 引用的 ref 都在本次上下文里 | 原始候选数 |
| Evidence Span Accuracy | 片段是所引原文的连续子串 | E2 及之后的原始候选数。E1 没有片段，值为空 |
| 原始 Precision / Recall | 与冻结预期键完全相同的候选；被召回的预期键 | 原始候选数；EXTRACT 预期键数 |
| Semantic Support Precision / Recall | 实验接受键里命中预期的个数；被实验接受集召回的预期键 | 实验接受键数；EXTRACT 预期键数 |
| Known Fact Duplication Rate | 落在非契约特征或问卷已知特征上的候选 | 原始候选数 |
| SELF/PREFERENCE Semantic Confusion Rate | 特征码与某条预期相同、分节不同的原始候选 | 带有预期键的样本上的原始候选 |
| Unsupported Claim Rate | 原始候选里对不上任何预期键的个数 | 原始候选数 |
| Pending Feature Rate | 仍为 `SEMANTIC_SUPPORT_PENDING` 且没有被验证器接受的候选 | 原始候选数 |
| Contradiction Preservation Rate | 实验接受集为空的矛盾样本 | 矛盾样本数 |
| Registry Coverage Rate | 预期键的特征码属于本次契约 | EXTRACT 预期键数 |

实验接受集在 E1–E3 等于确定性校验接受集。E4 在此之上加入验证器返回 `SUPPORTED` 的挂起候选。验证器不能改值、不能给用户确认、不能生成硬约束。验证器同意也不算客观正确，仍按冻结标签计分。

「无依据推断进入可用画像」看实验接受集里对不上预期键的比例。`Unsupported Claim Rate` 看的是原始候选，两者不是同一个数。

## 原门槛

Precision 至少 0.90，Recall 至少 0.75，SELF/PREFERENCE 混淆率至多 0.03，进入可用画像的无依据推断至多 0.02，非法硬约束、未授权外发和可识别敏感信息泄露都是 0。门槛没有降低。结果见消融报告，结论是 NO-GO。
