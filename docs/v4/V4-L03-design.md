# V4-L03 设计

Match Lab 在原有八个页面之后增加策略学习、实验回放和安全审计。三页复用现有 React 应用、工作区和 F03 领域类型。

策略页有两个来源。`FROZEN_EXPERIMENT` 读取 `StrategyDataset` 的 128 条合成观察，并用 `RelationshipLabels` 做 F03-FINAL 投影。`SYNTHETIC_SANDBOX` 只从当前工作区已经打开的反馈账本调用 `ObservationBuilder`。客户端不能提交双方接受或继续意愿。

校准调用 `ScoreCalibration`。沙箱样本不足时直接给出 `INSUFFICIENT`，不拟合一个有效模型。冻结集展示训练与验证拆分后的结果，并写明这是合成频率。

策略对照使用 `StrategyComparison`。真实提升保持 `NOT_IDENTIFIED`。`activate()` 继续拒绝。

回放列出 `LabRun`。重新执行把已保存的图交给当前 `V4GlobalMatchingEngine`，新运行的模式是 `CURRENT_ENGINE_REPLAY`。它不覆盖旧运行，也不检出历史 Git 提交。对照两份运行时，输入或策略不同会明确写出来。

安全页把本次工作区的求解违规、固定风险目录和生产闸门分开。生产闸门只有读接口，返回 `NO-GO`。
