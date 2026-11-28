# V4-L03 回放契约

每次 `LabRun` 保留 `runId`、`parentRunId`、`workspaceId`、`experimentType`、`datasetVersion`、`policyVersion`、`commitSha`、`inputHash`、`resultHash`、执行模式、状态、起止时间和输入输出正文。

`inputHash` 覆盖 `schema=sandbox-input-1`。`resultHash` 与 `semanticResultHash` 覆盖不含运行编号和求解毫秒的 `schema=sandbox-result-1`。分数变化会改变摘要。

重新执行的模式是 `CURRENT_ENGINE_REPLAY`。它表示当前代码执行历史输入。查看冻结目录仍是 `FROZEN_RESULT`。本阶段不下载或编译任意历史提交，因此不能称为历史版本完全复现。

父运行已经随工作区重置或上限回收时，回放返回 `HISTORY_UNAVAILABLE`，不生成替代结果。

导出正文带 `schemaVersion`。摘要可以用 `inputHash` 核对。接口不接受客户端路径。
