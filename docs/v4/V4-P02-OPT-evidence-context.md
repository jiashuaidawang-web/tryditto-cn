# V4-P02-OPT 证据上下文

## E2

E0 和 E1 把同一句话同时放进 `SELF_STATEMENT` 和 `PREFERENCE_STATEMENT`。模型于是可以同时引用两类证据，校验再因为引用不单一而拒绝。E2 改成一个原始来源对应一个证据身份，类型是 `USER_STATEMENT`。模型用 `proposedSection` 表示它在理解自身还是伴侣偏好。

候选字段是 `evidenceRef`、`evidenceSpan`、`proposedSection`、`proposedFeatureCode`、`proposedValue`。`evidenceSpan` 必须是该 ref 原文里的连续子串。混合句要拆成多条候选，每条绑定自己的片段。例如「我比较慢热，但希望另一半主动分享生活」应分别形成自身候选和伴侣偏好候选。

统一陈述类型没有取消 SELF / PREFERENCE 语义校验。`ProfileExtractionValidator.checkCited` 对 `USER_STATEMENT` 仍要求分节是 SELF 或 PREFERENCE，`behaviorKind` 为空，片段真实存在。`StatementSemantics` 同时看片段和整句。整句里的条件、转述或否定线索仍会拒绝，即使片段碰巧碰上封闭短语。权限、来源、硬约束和隐私边界保持原样。封闭短语表没有扩充。

E2 仍把确定性爱好 `reading` 放进上下文，用来和 E3 分开。

## E3

上下文分成三区：

- `knownFacts`：确定性事实，只作背景。本实验不发送不需要的爱好，因此这区为空。
- `extractionTargets`：本次契约里的特征码。
- `sourceStatements`：真正要分析的原句。

Prompt 禁止把 `knownFacts` 重新输出。生产 `ProfileContextBuilder.build` 仍然不接收调用方自由陈述，这条路径只存在于合成实验。

## 观察到的上下文效果

LongCat 全量 60 次调用里，E1 就没有再输出 `lifestyle.hobbies`。已知事实重复率从 E0 的大量重复降到 0/20，并在 E2、E3、E4 保持为 0。E3 的分区没有再把这个比率往下压，因为契约提示已经挡住了这一种重复。分区仍然单独保留，避免把「不发送爱好」和「改写证据身份」混成一次改动。

E2 开始接受一部分封闭短语能够确定的候选。LongCat 确定性接受从 E1 的 0 个变成 E2 的 3 个、E3 的 4 个。证据片段正确率在 E2、E3、E4 和保留集上都是 1。引用合法率也是 1。
