# V4-I01 测试记录

本地没有 PostgreSQL，也没有 Docker。下面的集成测试在本机是 NOT_RUN，交给 CI 的 Postgres 16。没有为了通过而删掉断言。

## 已在本机执行

`mvn -B -pl aiditto-app -am test`：

- 领域测试 617，失败 0，异常 0，跳过 0。其中 L03-FINAL 相对此前核实的 612 增加了 1 个沙箱测试，I01 再增加 4 个。
- 应用模块 Surefire 134，失败 0，异常 0，跳过 8。跳过的是原先就存在的真实模型、真实向量和开放池用例，不是这轮新加的断言。
- 新增并已执行的是 `ProductIntegrationModeTest` 2 个、`OnboardingFieldMapTest` 2 个、`ProductIntegrationSettingsTest` 2 个。

## 交给 CI 执行

集成作业的 Failsafe 名单新增：

- `ProductProfileIntegrationIT`：入驻投影、改一题后的保留、撤回与重新授权、题库覆盖、反馈修正和画像保存并发。
- `AccountLifecycleIT`：注销与外部任务、重复删除、迟到任务、外部失败、五项授权撤回、其他用户、撤回和保存并发。
- `LegacyProductModeIT`：默认注销不写擦除任务，V3 学习开关仍开启。

相对此前核实的 101 个 PostgreSQL 集成测试，这 13 个方法是新增，并且写进了 CI 的必跑名单。提交 `9229f14ba58b4416438c11ec0898221018c673da` 的五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46141335428 全部 success。第一次集成作业 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46140874430 失败，原因是确认编号带上了过长的用户编号，快照拒绝这种来源标记。

CI 的 Surefire 切片原来是 18 个。`ProductIntegrationSettingsTest` 的 2 个方法已放进同一名单。

浏览器：没有新增用户端 Playwright。真实登录、入驻和个人资料修改的浏览器验收是 NOT_RUN。Match Lab 原有 4 个用例不代替这条产品路径。

## 合成用户能说明的覆盖

题库 50 项，映射 22 项，未映射 28 项。合成入驻会检查爱好、阅读、音乐、关系目标、节奏、在读、年级、学历拒答、职业引用、年龄硬约束、身高不设限、学历重要性不设限、性别引用和开场白。宗教答案留在问卷里，但不进入 V4 快照。

这些数字只说明隔离测试走通了现有投影规则。它们不是真实用户的语义理解质量。
