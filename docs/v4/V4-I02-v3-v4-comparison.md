# V4-I02 V3 / V4 对照

同一城市、同一周期、同一次读取。

口径 A：各自入池。V3 使用 `People.city` 里 `PoolGate` 通过且 `allowsPool` 的人。V4 使用本轮的权威池。两边人数不同先记入池原因，不记成算法胜负。

口径 B：只在两边都入池的用户上再求解。V3 仍是 `MatchingSolver` 加 `Weights.W1`。V4 仍是 E、B5、G2。

报告字段在 `comparison_document`：两边入池数、配对数、未匹配数、双方都选中的关系、仅 V3、仅 V4、排除原因和特征覆盖。原始总分不比较，两套分数不是同一个语义空间。

V3 求解不调用 `WeekRunner.solve`，因此不写 `draft`。AI 分和学习分都不注入。
