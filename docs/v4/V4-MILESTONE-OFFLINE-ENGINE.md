# 离线引擎里程碑

Match Lab 第一版和它前面的离线引擎在 L03-FINAL 冻结。这一页记录当时的集成基准。发布 GitHub Release 不在本里程碑里。

## 集成基准

- `master_agent_v4` 在合并 L03-FINAL 之前是 `3f822f86820f7e90710ba710a899938a1e4b94ea`。
- L03 工作台实现 `8173410f6efa18b3ea8fef8b87658058feffc3d1`。
- L03 浏览器通过的尖端记录是 `bcdd3113af6689aca5444cc169532e35da8f2c4f`，CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46135107419 ，Artifact `15259471489`。
- L03-FINAL 收口分数单位、统计口径、数据集身份和能力边界。快进后的 `master_agent_v4` 是 `767d65fbb3d67d17f6c3c7608e01b00fc918b115`。五作业 CI https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46137692591 。

## 当时已经核实的工程数字

612 项领域测试，101 项 PostgreSQL 集成测试，18 项应用层单元测试，1 项真实 Qdrant 集成测试。失败、异常、跳过都是 0。四个 Match Lab Playwright 用例通过。五个 CI 作业 success。

原 200 人生产算法回归是 83 对、34 人未匹配、822 条合法候选边、0 条硬约束违规。那不是 V4 质量证明。

## 仍然不是通过

真实关系质量 NOT_RUN。真实策略提升 NOT_IDENTIFIED。历史真实模型质量 NO-GO。生产启用 NO-GO。最终匹配算法质量 NO-GO。

冻结之后的产品集成从 `feature/v4-product-profile-integration` 开始，不在这个里程碑里自动合并。
