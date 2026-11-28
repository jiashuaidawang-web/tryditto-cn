# V4-L01 沙箱边界

允许的模式只有 `SYNTHETIC_SANDBOX` 和 `FROZEN_EXPERIMENT`。

禁止：

- 真实生产用户。
- 客户端传入 SQL、文件路径或外部 URL。
- 读取生产用户表做实验。
- 真实模型调用。
- 生产发布、通知、画像写入和算法权重写入。
- 真实配对生效。
- 超过 24 份沙箱画像。
- 10,000 人规模图。
- 召回切分除 `dev` 和 `holdout` 以外的任何数据集。

前端不显示入口不能代替这些检查。开关和 `ADMIN` 角色都在服务端。

沙箱候选人使用 `Cohort.DEMO`。两份 DEMO 画像可以互相评分。它们不会被写成 `relationship_profile_version`、`matching_observation` 或 `relationship_feedback_event`。
