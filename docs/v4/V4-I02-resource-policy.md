# V4-I02 资源策略

默认隔离预算：

| 项目 | 值 |
|---|---|
| 最大入池用户 | 200 |
| 最大候选边 | 50,000 |
| 最大合格边 | 50,000，等于现有 `GlobalBudgets.MAX_SOLVER_EDGES` |
| 最大墙钟 | 20 秒，不超过现有 90 秒求解预算 |
| 最大并发 | 1 |
| 队列 | 0 |
| 结果文档 | 1 MiB |

5,000 人及以上直接拒绝，构造函数也不接受这个用户上限。500 人即使单独放宽用户上限，完整候选边是 124,750，仍因候选边上限拒绝。拒绝发生在建图之前，不把截断图叫成最优。

`V4GlobalMatchingEngine` 在截止时间之后丢弃 Blossom 返回的配对。它不能取消已经开始的 Blossom 线程。本轮没有增加子进程隔离。资源隔离因此继续 NO-GO，不能写成生产超时治理已经解决。

领域测试 `expiredDeadlineDiscardsTheResultAndAMathFixtureIsNotABusinessPlan` 覆盖了“结果丢弃”。数学夹具的配对不能当成业务计划。
