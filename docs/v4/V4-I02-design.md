# V4-I02 设计

隔离业务用户经过权威入池和只读画像适配，进入已经冻结的 E、B5、G2。影子结果只写 `shadow_run` 和 `shadow_pair`。

```text
ProductMatchingPoolReader
        |
        v
ProductCompatibilitySubjectAdapter
        |
        v
GlobalMatchingInput
  strategy recall-rank-1
  graph    E_FULL_POOL
  policy   compat-decision-1
  objective global-objective-g2-1
        |
        v
V4GlobalMatchingEngine.solve
        |
        v
ShadowPlanAudit 重新核对
        |
        v
shadow_run / shadow_pair   状态只有 SHADOW
```

模式是 `V4_SHADOW_TEST`。它和 `V4_INTEGRATION_TEST` 使用同一套环境闸门：`local`、`ci` 或 `isolated`，不能是生产 profile，数据库必须是隔离库。`V4_SHADOW` 和 `V4_PILOT` 仍然不能启动。这个模式不授予反馈学习。

关闭影子模式时，`WeekRunner` 的代码和默认 `LEGACY_ONLY` 不变。影子失败只结束自己的运行记录，不进入周匹配。

本轮不改 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom`、M02 公式、M04 权重和 S01。
