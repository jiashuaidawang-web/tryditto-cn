# V4-L01 审计

Match Lab 放在现有 `web/` React 工程和 `aiditto-app` 里。没有新建前端项目，没有改成 Vue。路由命名空间是 `/match-lab`，API 是 `/api/match-lab/**`。交友页面仍走原来的登录和入驻守卫。Match Lab 不经过那条入驻守卫。

后端默认 `aiditto.match-lab.enabled=false`。`MatchLabFilter` 在开关关闭时直接拒绝 `/api/match-lab`，不进入后面的链路。开关打开后，`SecurityConfig` 要求 `ADMIN`。普通登录用户和未登录请求都进不了控制器。

控制器只接收画像名称、特征码和注册表里的值。没有 SQL、文件路径或外部 URL 参数。特征码必须存在于 `FeatureRegistry`，并且落在声明的分区。非法码返回 `FEATURE_CODE`。

沙箱画像只活在 `LabWorkbench` 的内存里。候选人群是 `Cohort.DEMO`。评分调用 `BidirectionalCompatibilityEngine`。召回对照调用 `RecallRankStudy.run`，只用冻结开发集和 `NARROW` 预算，人数超过 80 就拒绝。推理调用 `ReasoningExperiment` 和 `RuleEchoModel`。C1、C2 不加分。没有模型客户端。

本轮没有改 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom`，没有读生产用户表，没有发布匹配。
