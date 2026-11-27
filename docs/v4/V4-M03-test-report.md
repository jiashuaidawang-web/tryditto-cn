# V4-M03 测试报告

本地 `mvn -B -pl aiditto-domain test` 为 539 项通过，0 失败。其中包括原有领域测试、M02 收口测试和 6 项关系推理测试。`SingleModelReasoningClientTest` 为 1 项通过：模型超时后内容为空，没有换成另一个模型的文本。

实现提交是 `c856ac05d11f1062b524c2bc517048fedc35d69e`。第一次 Actions https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45999915874 里领域测试、Qdrant 隔离和报告归档成功，集成作业在写出测试报告之前以退出码 1 结束，公开页面看不到 Maven 日志。随后一次脚本改动在 Maven 启动前就退出，没有覆盖这次失败的原因。修正脚本之后的提交 `00b44d104d458514dbbabfc2fec553f4572d39be` 运行 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46003372691 ，四项作业都是 success。集成作业执行了发布安全、同意、匹配、画像和提取场景，验收脚本通过。集成报告产物约 68KB，不是空报告。

集成基线是 `04e702743c93631c30fd0175d2e516bb3e302ed7`，已经快进到 `master_agent_v4`。M02 代码的 CI 是 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45990401708 。记录这次 CI 的文档提交又通过了 https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/45991079422 。远程没有并行的四模型 Benchmark 分支。合并前可见的远程分支是 `master`、`master_agent_v3`、`master_agent_v4`、`feature/v4-recall`、`feature/v4-bidirectional`。

生产 `WeekRunner`、`Scorer`、`MatchingSolver`、Blossom 和 S01 没有改。
