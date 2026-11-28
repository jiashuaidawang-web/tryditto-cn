# V4-I02 画像适配

`ProductCompatibilitySubjectAdapter` 只读取 `RelationshipProfileService` 已经保存的当前快照和 `People.Member.candidate()`。

它不复制自身特征到伴侣偏好，不使用旧 Taste 摘要，不按性别推断性格，不把拒绝或超时写成负向偏好，也不给缺失值填默认分。

已确认特征才标成 `SEMANTIC_SUPPORTED`。争议、临时和空值不进入官方评分支持。`WITHHELD`、`UNBOUNDED` 和已有硬规则保持原契约，由 M02 记为不可评分，而不是合成一个分数。

`Candidate.cohort` 保持产品值 `real`。评分对象的 `fixtureSource` 是 `synthetic-fixture:isolated-business`，只用来满足离线引擎“本轮只接受合成夹具”的原有检查。这个前缀不是业务 cohort，也不放宽 `HardFilter`。

## 覆盖

`FeatureAlignmentRegistry.v1` 只有四对：pace、communication、social initiative、schedule。

`OnboardingFieldMap` 的 22 个已映射特征与这四对的交集是 0。领域测试 `mappedOnboardingDoesNotOverlapTheFrozenAlignmentTargets` 实测了这个结果。已映射的年龄、性别、身高和学历偏好走硬约束或“不评分”，不能当成 B5 的对齐特征。

因此当前业务画像不能产生合法的 M02 推荐资格。这是数据契约缺口，不通过降低 B5 来补。
