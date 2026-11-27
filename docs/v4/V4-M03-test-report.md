# V4-M03 测试报告

本地 `mvn -B -pl aiditto-domain test` 为 539 项通过，0 失败。其中包括原有领域测试、M02 收口测试和 6 项关系推理测试。`SingleModelReasoningClientTest` 为 1 项通过：模型超时后内容为空，没有换成另一个模型的文本。

GitHub Actions 地址在这个分支推送之后补上。推送前不填写。

集成基线是 `04e702743c93631c30fd0175d2e516bb3e302ed7`，已经快进到 `master_agent_v4`。M02 代码的 CI 是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45990401708 。记录这次 CI 的文档提交又通过了 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45991079422 。远程没有并行的四模型 Benchmark 分支。合并前可见的远程分支是 `master`、`master_agent_v3`、`master_agent_v4`、`feature/v4-recall`、`feature/v4-bidirectional`。

生产 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom 和 S01 没有改。
