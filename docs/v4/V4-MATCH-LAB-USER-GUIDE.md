# Match Lab 使用说明

Match Lab 是内部研究控制台。它使用虚构画像和冻结实验，不读取生产用户，不打开生产匹配。

## 隔离环境

```bash
cd api
MATCH_LAB_ENABLED=true MATCH_LAB_ENVIRONMENT=local ADMIN_PHONES=<管理员手机号> mvn -pl aiditto-app -am spring-boot:run
cd web && npm run dev
```

环境必须是 `local`、`ci` 或 `isolated`。只设置 `MATCH_LAB_ENABLED=true` 不会打开接口。`prod` 或 `production` 保持关闭。用管理员手机号登录。隔离环境的演示码是 `888888`，它不是生产管理员认证。

工作区在进程内存里。重启后画像、运行、反馈和学习提案都会丢失。每个管理员只能访问自己的工作区。

## 十一个页面

| 页面 | 入口 | 做什么 |
| --- | --- | --- |
| 实验总览 | `/match-lab` | 查看目录、环境、工作区和生产 NO-GO |
| 用户画像 | `/match-lab/profiles` | 创建和修改虚构画像，查看四层特征 |
| 候选发现 | `/match-lab/candidates` | 冻结召回对照和沙箱候选 |
| 双向评分 | `/match-lab/score` | 调用 M02。单向高分和证据不足会被拒绝 |
| AI 关系推理 | `/match-lab/reasoning` | 回放规则夹具。真实模型是 NOT_RUN |
| 全局匹配 | `/match-lab/global` | 用当前工作区用户跑 M04 |
| 反馈事件 | `/match-lab/feedback` | 按 F01 提交双方事件 |
| 画像学习 | `/match-lab/learning` | 确认或拒绝 F02 提案 |
| 策略学习 | `/match-lab/strategy` | 冻结或沙箱观察、校准和策略对照 |
| 实验回放 | `/match-lab/replay` | 查看快照、重放和对照 |
| 安全审计 | `/match-lab/safety` | 运行结果、风险登记和生产闸门 |

## 一条演示

1. 打开全局匹配，执行沙箱求解。
2. 从一对结果进入反馈，分别以双方身份接受。互相接受由服务端派生。
3. 进入画像学习，生成相处节奏提案。这是用户明确选择，不是从接受事件推断的。
4. 确认后查看其他特征仍在，再重新匹配。旧运行的输入摘要不变。
5. 打开策略学习。冻结来源能看到历史观察。沙箱样本不足时校准是 INSUFFICIENT。激活会被拒绝。
6. 打开实验回放，重放刚才的运行，并和另一次目标对照。
7. 打开安全审计，确认生产启用仍是 NO-GO。

## 重置和常见问题

`POST /api/match-lab/workspace/reset` 会清掉当前管理员的画像、运行、反馈和学习提案。别人的工作区编号会返回 403。

接受事件不能当作某项伴侣偏好的证据。反馈撤回后，绑定该账本的未确认提案不能再生效。历史快照不会因为后来修改画像而改变。分数变化不是真实关系质量提升。
