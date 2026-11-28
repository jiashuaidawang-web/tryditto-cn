# V4-L01 沙箱边界

允许的模式只有 `SYNTHETIC_SANDBOX` 和 `FROZEN_EXPERIMENT`。

禁止：

- 真实生产用户。
- 客户端传入 SQL、文件路径或外部 URL。
- 读取生产用户表做实验。
- 真实模型调用。
- 生产发布、通知、画像写入和算法权重写入。
- 真实配对生效。
- 超过 24 份沙箱画像，或超过 48 次运行，或超过 64KiB 的已知请求体。
- 10,000 人规模图。
- 召回切分除 `dev` 和 `holdout` 以外的任何数据集。沙箱候选只读当前工作区。

前端不显示入口不能代替这些检查。管理员角色仍是必要条件。只打开 `MATCH_LAB_ENABLED` 不够。`MatchLabProperties.admits()` 还要求环境名是 `local`、`ci` 或 `isolated`，并且 `production` 为 false。活跃 profile 为 `prod` 或 `production` 时，`MatchLabProductionLock` 把 `production` 设为 true，接口保持关闭。

每个管理员一份内存工作区，最多 32 份。空闲两小时后回收。`POST /workspace/reset` 只重置调用者自己的工作区。画像修改带期望版本，后写入的一方在版本不符时被拒绝，已写入的版本保留。服务重启后工作区、画像和运行记录全部丢失。这不是分布式沙箱。

演示验证码只存在于演示模式。它不是生产管理员认证。生产口令、生产画像、生产匹配、生产反馈和学习数据都不进入这个接口。这里不调用 `WeekRunner`、`PublishGuard` 或真实模型，也不接受 SQL、文件路径或外部 URL。

沙箱候选人使用 `Cohort.DEMO`。两份 DEMO 画像可以互相评分。它们不会被写成 `relationship_profile_version`、`matching_observation` 或 `relationship_feedback_event`。
