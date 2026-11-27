# V4-M03 真实模型运行说明

运行位置是服务器上的 `/opt/aiditto-v4-eval/m03/`。子进程只收到所选模型的三个字段：地址、密钥和模型名。备用密钥、数据库和 Qdrant 不传给子进程。日志里只写 `key=present`。脚本发现密钥原文出现在日志中时，输出 `LOG_SUPPRESSED` 并停止转发。

没有连接生产数据库，没有连接生产 Qdrant，没有重启生产容器，没有读取真实用户画像。`fallback` 和 mock 关闭。思考模式关闭，温度为 0，每个方向只尝试一次。超时不重试。

## 命令

```text
python3 /opt/aiditto-v4-eval/m03/scripts/m03-live-remote.py longcat|deepseek preflight|smoke|full|challenge
```

`longcat` 读取 `LLM_PRIMARY_*`。`deepseek` 读取 `LLM_FALLBACK_*`，映射成子进程的主模型字段，并清掉备用字段。主类是 `cn.aiditto.reasoning.ReasoningLiveRunner`。容器是 `eclipse-temurin:17-jre`，内存 800m，堆 256m，类路径 `lib/*`。

缺少所选模型的字段时，脚本打印 `NOT_RUN missing <字段名>`，退出码 2。本地没有环境变量时，用上面的服务器目录继续；不要把本地缺失当成实验通过。

## 本轮凭据

`/opt/aiditto/.env` 里有值的模型名是 `LongCat-2.5` 和 `deepseek-flash`。没有 `hy4-flash` 或 `qwen4-max` 的地址、密钥和模型字段。这两个模型是 `NOT_RUN`，阻塞原因是实验环境没有对应配置。实验改用已经配置的两个模型，没有为此新建模型平台。

## 预算

预算在看到结果之前固定：

| 项 | 上限 |
| --- | --- |
| 冒烟 | 10 条关系，每个方向各一次 |
| 完整方向评估 | 224。8 条硬约束关系的 16 个方向在封存后拒绝，不调用模型 |
| 挑战 | 36 条关系，72 个方向 |
| 单模型调用 | 320 |
| 单模型 token | 1,500,000 |
| 冒烟进入完整实验 | 结构化合法率至少 0.80，硬约束接受 0，越权引用 0 |

0.80 只决定是否花费完整运行，不是 0.90 的质量门槛。

相同用户、相同画像版本和相同方向的第二次关系会命中 `REPEAT`，复用第一次的校验结果，不再调用模型。120 条里，`CROSSED` 与 `MUTUAL`、`PERSONALITY_GAP` 与 `MISSING` 是同一对合成用户。因此完整运行的模型请求是 192，另有 32 次缓存命中，加上 16 次封存拒绝，合计 240 个方向槽位。

## 输出

校验后的文本在：

```text
/opt/aiditto-v4-eval/m03/reports/m03/<role>/<stage>/<relation>.txt
```

原始模型 JSON 在同级 `raw/` 目录，旁边的 `.meta` 记录模型名、延迟和 token。费用和推理 token 适配器没有返回，报告写不可得。密钥不写入这些文件。
