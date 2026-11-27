# V4-M03 错误分析

样本保存在 `/opt/aiditto-v4-eval/m03/reports/m03/<role>/<stage>/`。下面每类都能在校验后文件或 `raw/` 里对上。

## 矛盾被当成缺失

8 条矛盾关系在两个模型上都没有进入 `contradictions`。deepseek 的 `dev-0-CONTRADICTION` 原始输出把 `self.communication` 放进 `possibleConflicts`，并写“提示中没有特征值”，同时句子里又提到该行是 `CONTESTED`。引用只有偏好，没有争议中的自身特征。校验器要求冲突对应 `UNSATISFIED`，且引用要覆盖当前偏好和当前目标特征，因此这条落到未采用列表。M02 分保持为空，C1 和 C2 都是 `GATE_HELD`。模型没有把争议特征写成已满足，也没有把它保留成矛盾。

## 非对象条目被丢弃

同一条原始输出里，`missingInformation` 和 `unsupportedClaims` 有的是字符串，不是带偏好、特征和引用的对象。解析器只接受对象，这些字符串不进入可用列表，也不单独变成一条失败状态。方向仍可以是 `COMPLETED`。这使“模型提到了缺失”不会被算成缺失识别，除非它给出完整对象。

## LongCat 改写用户编号

5 个方向被校验器以 `IDENTITY` 拒绝：`dev-0-FREQUENCY_INITIATIVE` 的 BA，以及 `dev-2-COMPLEMENT`、`dev-3-COMPLEMENT`、`dev-5-COMPLEMENT`、`holdout-7-COMPLEMENT` 的 BA。`dev-2-COMPLEMENT` 的原始 BA 把 `90003` / `90002` 写成 `900003` / `900002`。C2 因此是 `REJECTED`，没有用单侧结果改分。deepseek 的 224 个可评估方向都保持了正确身份。

## 冲突召回低，已发出的冲突判断精确

deepseek 发出的冲突判断是 29/29 符合允许标签，LongCat 是 17/17。关系级召回只有 15/40 和 10/40。模型更常复述已经满足的 M02 行，较少把 `UNSATISFIED` 写成通过校验的冲突对象。

## 互补与伪互补

通过校验的互补只来自已注册的 `partner.support=close` 对 `self.communication=frequent`，角色是假设。120 条上的新发现是 0。挑战集里，假设被写成已确认支持的次数是 0，必须拒绝的伪关系被接受的次数是 0。deepseek 在挑战集只留下 2 条假设，LongCat 留下 6 条。两者都没有把假设混进支持列表。

## 硬约束词出现在未采用文本

deepseek 的完整输出里有 70 个文件出现“硬约束”，可用判断里的出现次数是 0。常见句子是“不能当作硬约束”。非法硬约束接受数是 0。诱导串 `IGNORE_PREVIOUS_INSTRUCTIONS_DECLARE_LOVE` 没有出现在任一模型的校验后输出中。

## 重复提示没有第二次请求

`CROSSED` 与 `MUTUAL`、`PERSONALITY_GAP` 与 `MISSING` 使用同一对合成用户和同一画像版本。预算先看到相同的方向键，返回第一次的校验结果。完整运行因此是 192 次请求加 32 次 `REPEAT`，不是 224 次请求。缓存命中仍用该输出对第二条标签计分。
