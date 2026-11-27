# V4-M01-RANK 测试报告

分支 `feature/v4-recall-rank`，起点 `544e3d8eac0f1f8c502ea1aedfe1762134c35007`。`origin/master_agent_v4` 与 `origin/feature/v4-ai-reasoning` 都在这个起点。M03 的离线研究已经在集成分支上，生产评分和 AI 修正没有启用。

本轮新增：

- `RecallRankFixture`
- `RecallRankStudy`
- `RecallRankStudyTest`
- `.github/workflows/v4-ci.yml` 的推送分支增加 `feature/v4-recall-rank`

没有修改 P02、M03 Prompt、M01 权重、`RrfFusion`、`WeekRunner`、`Scorer`、`MatchingSolver`、Blossom 或 S01。

## 本地测试

`RecallRankStudyTest` 五个方法在本地通过。其中规模方法约 90 秒，五个规模都在 90 秒墙钟内完成。其后只调整了未评分边的未入选原因，四个不含量规模的方法再次通过。

断言锁住的是实验前写下的门槛：历史分离实验仍为 0、4、8；安全违规为 0；宽预算 F 覆盖全部手写强匹配；窄预算 A 覆盖更少；窄预算 A 存在空路线信号；并列策略可复现；周期散列的曝光广于 userId；双向覆盖的强匹配缺席少于 userId；向量标记为 `NOT_RUN`；完成的规模低于 90 秒。

完整领域测试、PostgreSQL 集成测试、应用层测试和 Qdrant 隔离测试由 CI 执行。本地没有把上一轮的 541、57、13、1 重新计为本轮数字。

## CI

研究提交 `587539d9ccf9eac889055fd2c4b3be0ca02bd577`。

https://github.com/jiashuaidawang-web/tryditto-cn/actions/runs/46035257576

`domain-tests`、`integration-tests`、`qdrant-isolation`、`test-reports` 都是 success。API 没有返回各套件的用例数，这里不沿用上一轮的 541、57、13、1。

## 结论

- 工程质量：GO。上述四个作业都成功，本地 `RecallRankStudyTest` 也通过。
- 召回质量：RRF-only 为 NO-GO。E 和 F 在开发集与独立保留集上覆盖全部手写强匹配，这一覆盖记为 GO，只适用于这两份合成对照。
- 端到端候选评分质量：E 和 F 上，手写强匹配全部获得 M02 推荐资格，干扰候选被评分且未获资格，安全命中为 0。GO 只表示资格与手写标签在这两份对照上一致。
- 真实向量质量：NOT_RUN。
- 生产启用：NO-GO。
- 最终匹配算法质量：NO-GO。没有全局求解，也没有改 Blossom。
