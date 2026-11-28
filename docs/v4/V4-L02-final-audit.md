# V4-L02-FINAL 审计

审计对象是 `feature/v4-match-lab-loop` 上的沙箱闭环。不改 `WeekRunner`、`Scorer`、`MatchingSolver`、`Blossom`，不改 Flyway，不改冻结案例标签。

## R01 画像保留

`adopt` 只替换来源以 `confirm.` 开头的已确认特征。其余写入保持原值、操作符、强度和证据状态。`learning.seed` 拒绝的行为特征和硬约束不进入学习证据，因此不会在确认时被删掉。直接编辑使画像版本变化后，`evidenceFor` 丢掉旧缓存并按当前画像重新投影。`LabWorkbench.update` 继续携带原来的候选人和代次，年龄硬约束不会在保存时被新建候选人替换。提案上的画像版本和当前版本不一致时返回 `STALE`，不调用 `update`。

## R02 来源绑定

`DIRECT` 提案没有反馈事件。页面和案例 E 都标明这是用户自己的选择。`FEEDBACK` 提案必须指向本账本里一条有效的 `PREFERENCE_CORRECTION_SUBMITTED`，并且行动者、学习授权和未撤回状态来自这条账本，不是写死的通过。`MATCH_ACCEPTED` 返回 `INSUFFICIENT_SEMANTICS`。账本出现撤回或授权撤回后，挂在它上面的未确认提案返回 `RETRACTED_SOURCE`。已经确认过的同一提案再次确认不再增加版本。

## R03 快照

`schema=sandbox-input-1` 保存参与用户、已写入特征、版本、代次、授权、年龄范围、策略版本、观察日和种子。`schema=sandbox-result-1` 保存目标、状态、安全复查、总权重、配对分数和未匹配原因。它不含运行编号和求解毫秒。`resultHash` 与 `semanticResultHash` 都对这份语义正文计算。历史 `LabRun.inputSnapshot` 只在记录当时写入。

## R04 策略绑定

求解时把实际全局目标版本写进 `GlobalMatchingInput`。打开反馈时，匹配编号、观察日、双方画像版本、代次、候选策略、评分策略和目标版本都从那份输入和当时的边读取。G1 与 G2 因此不会共用一个默认目标版本。

## R05 生命周期

计划、账本、事件和提案有上限。重复的同一反馈动作遇到 `DUPLICATE_EFFECT` 时返回当前账本。工作区重置会换掉整个 `LabWorkbench`，旧运行编号不能再打开反馈。确认失败的 `STALE` 和 `RETRACTED_SOURCE` 不写画像。

## 反例

`SandboxLoopFinalTest` 覆盖多类特征保留、编辑后的最新值、过期提案、伪造来源、跨用户来源、接受事件、撤回后确认、授权撤回、重复确认、G1/G2 绑定、历史快照不变，以及重置后的父运行。

浏览器用例 `match-lab-final.spec.ts` 检查接受事件不能生成偏好、保存当前画像后历史输入摘要仍在。原闭环用例同时检查确认后 `self.pace` 仍在表里。作业未绿之前，这两项浏览器断言不算通过。
