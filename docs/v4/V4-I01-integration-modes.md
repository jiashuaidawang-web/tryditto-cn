# V4-I01 集成模式

配置项是 `aiditto.integration.mode`，环境变量 `INTEGRATION_MODE`。缺省和空白都是 `LEGACY_ONLY`。生产配置写死 `LEGACY_ONLY`。

| 模式 | 能否启动 | 行为 |
| --- | --- | --- |
| `LEGACY_ONLY` | 能，这是默认 | 现有注册、问卷、V3 口味和 V3 学习保持原样。注销不擦 V4 行。不检查数据库地址 |
| `V4_INTEGRATION_TEST` | 只在隔离环境 | 隔离测试可以投影画像，注销会走擦除协调。画像处理开关仍要单独打开 |
| `V4_SHADOW_TEST` | 只在隔离环境 | 留给隔离影子匹配。不授予反馈学习。注销同样走擦除协调 |
| `V4_SHADOW` | 不能 | 启动失败 |
| `V4_PILOT` | 不能 | 启动失败 |
| 其他字符串 | 不能 | 启动失败 |

测试模式还要同时满足三件事，缺一就在启动时失败：`aiditto.integration.environment` 是 `local`、`ci` 或 `isolated`；Spring profile 不是 `prod` 或 `production`；数据源是本机或 CI 的 PostgreSQL，库名是 `aiditto_s01_test`、`aiditto_lab_e2e`，或以 `_test`、`_e2e` 结尾。生产 profile 把环境写成 `production`，模式仍写死 `LEGACY_ONLY`。

这些模式都不会因此打开真实模型、真实向量同步、生产匹配、V4 反馈学习或策略自动激活。调用方不能把外部擦除标成完成。
