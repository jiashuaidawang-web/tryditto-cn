# TryDitto 国内版

大模型是可替换的推理能力。用户画像、双向匹配、真实反馈和评估体系才是长期资产。

## 长期约束

- 先有证据、双向评分和评估，再谈模型输出。合成结果不能写成真实关系质量。
- 不启用生产 V4 匹配，不做真实反馈学习，不调用生产数据库或生产 Qdrant。
- 不修改 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom`，不降低 S01 安全门槛。
- 不改历史 Flyway migration，不改已经冻结的数据集和案例标签。
- 不把未校准的 `score/100`、合成校准或模型回放写成生产概率或真实模型输出。
- Match Lab 默认关闭。只打开开关不够，环境还必须是 `local`、`ci` 或 `isolated`，生产 profile 保持关闭。演示验证码不是生产管理员认证。
- 产品集成默认 `LEGACY_ONLY`。`V4_INTEGRATION_TEST` 和 `V4_SHADOW_TEST` 只能在 `local`、`ci` 或 `isolated` 环境、非生产 profile、隔离数据库上启动。`V4_SHADOW` 和 `V4_PILOT` 不能启动。这些模式不打开真实模型、生产匹配、真实反馈学习，也不能把调用方传入的成功标志当成外部存储已删除。影子配对只写隔离的影子表，不能进入发布、通知或生产 `match`。
- 不提交服务器密钥、数据库密码或访问凭据。
- Maven 在 `api/` 下执行。应用层测试要带 `-am`，避免用旧的已安装领域包。
- 易变的提交 SHA 和任务状态写在 `docs/v4/V4-PROJECT-STATUS.md`，不写进本文件。
