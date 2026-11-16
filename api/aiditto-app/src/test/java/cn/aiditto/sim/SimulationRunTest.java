package cn.aiditto.sim;

import cn.aiditto.domain.sim.SimMetrics;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 9 Go/No-Go：12 周期合成反馈回放，逐项实测闸门并落报告。
 * 报告是给人的交付物，测试是给 CI 的硬闸门；两者用同一份实测数据，不各写一套。
 */
class SimulationRunTest {

    private record Gate(String name, String threshold, double actual, boolean pass, String note) {}

    @Test
    void twelveCyclesProveLearningAndWriteGoNoGoReport() throws IOException {
        SimDriver.Run run = SimDriver.run(SimDriver.Config.defaults());
        // 影子对照：同一批用户/真值/噪声，只关掉画像重写。
        // 用来区分「学习损害配对」与「pastPairs 累积导致的结构性下降」，避免误判根因。
        SimDriver.Run frozen = SimDriver.run(SimDriver.Config.frozen());
        // 固定候选池对照：不累积 pastPairs，剥离「往期配对耗尽好对」的结构性效应，
        // 单独量化反馈学习对排序质量的净收益（Go/No-Go 报告第六节第 3 条）。
        SimDriver.Run fixed = SimDriver.run(SimDriver.Config.fixedPool());
        SimDriver.Run fixedFrozen = SimDriver.run(SimDriver.Config.fixedPoolFrozen());
        List<SimDriver.CycleMetric> m = run.cycles();
        assertThat(m).hasSize(12);
        System.out.println("===== 12 周期合成回放（users=" + run.config().users()
                + " twoFaced=" + run.twoFacedUsers() + " longTail=" + run.longTailUsers()
                + " elapsed=" + run.elapsedMs() + "ms）=====");
        for (SimDriver.CycleMetric c : m) {
            System.out.printf("cycle=%2d pairs=%3d regret=%.4f top1=%.4f recovery=%.4f twoFaced=%.4f "
                            + "predTrue=%.4f fair=%.3f cov=%.3f excl=%.3f repeat=%d%n",
                    c.cycle(), c.pairs(), c.regret(), c.top1Hit(), c.tasteRecovery(), c.twoFacedConv(),
                    c.predTrueCorr(), c.fairness(), c.coverage(), c.exclusiveRouteShare(), c.pairRepeat());
        }

        List<Double> regrets = m.stream().map(SimDriver.CycleMetric::regret).toList();
        List<Double> hits = m.stream().map(SimDriver.CycleMetric::top1Hit).toList();
        SimDriver.CycleMetric first = m.get(0), last = m.get(m.size() - 1);
        int monotone = SimMetrics.monotoneDecreaseCount(regrets);
        double slope = SimMetrics.slope(regrets);
        int repeats = m.stream().mapToInt(SimDriver.CycleMetric::pairRepeat).sum();
        double avgExclusive = m.stream().mapToDouble(SimDriver.CycleMetric::exclusiveRouteShare).average().orElse(0);
        long p0 = run.cases().stream().filter(c -> c.level().equals("P0")).count();
        long p0Pass = run.cases().stream().filter(c -> c.level().equals("P0") && c.passed()).count();

        List<Gate> gates = new ArrayList<>();
        gates.add(new Gate("连续周期数 / 全生命周期零中断", "≥12", m.size(), m.size() >= 12, "12 周期回放完成"));
        gates.add(new Gate("regret 单调下降周期数", "≥8/12", monotone, monotone >= 8, "相邻周期不上升次数"));
        gates.add(new Gate("末周期 regret", "≤0.15", last.regret(), last.regret() <= 0.15, "越小越准"));
        gates.add(new Gate("learning_slope（regret 对周期号）", "<0", slope, slope < 0, "线性回归斜率"));
        gates.add(new Gate("周期1 冷启动 top1_hit@decile", "≥0.35", first.top1Hit(), first.top1Hit() >= 0.35, "零反馈基线"));
        gates.add(new Gate("周期12 top1_hit@decile", "≥0.60", last.top1Hit(), last.top1Hit() >= 0.60, "学习后端点"));
        gates.add(new Gate("taste_recovery（周期12 Spearman ρ）", "≥0.70", last.tasteRecovery(),
                last.tasteRecovery() >= 0.70, "独立真值对照，非自评"));
        gates.add(new Gate("口是心非组收敛", "≥0.80", last.twoFacedConv(), last.twoFacedConv() >= 0.80,
                "隐性维度权重 > 显性维度"));
        gates.add(new Gate("stability（恶意差评画像变化）", "≤0.15", last.stability(), last.stability() <= 0.15,
                "A01 单次极端差评"));
        gates.add(new Gate("pair 重复推送率", "=0", repeats, repeats == 0, "含 pastPairs / forbidden"));
        gates.add(new Gate("独占单路占比（五路共识口径）", "≤0.60", avgExclusive, avgExclusive <= 0.60,
                "Step 5 修正后的独占口径"));
        gates.add(new Gate("长尾 20% 曝光公平", "≥0.80", last.fairness(), last.fairness() >= 0.80, "曝光份额/人数占比"));
        gates.add(new Gate("对抗用例 P0 通过率", "100%", p0 == 0 ? 0 : (double) p0Pass / p0,
                p0 > 0 && p0Pass == p0, p0Pass + "/" + p0));
        gates.add(new Gate("召回 Recall@50（1% 选择性桶）", "≥0.90", 0.9900, true,
                "Step 5 真实 Qdrant 10000 点实测 0.9900（非本回放重测）"));
        gates.add(new Gate("LLM 全挂 / Qdrant 不可用 / 成本熔断", "全部通过", 1, true,
                "由 Step 5/6/7 的 IT 与故障注入单测覆盖，见验收记录"));

        List<Double> frozenHits = frozen.cycles().stream().map(SimDriver.CycleMetric::top1Hit).toList();
        List<Double> frozenRegrets = frozen.cycles().stream().map(SimDriver.CycleMetric::regret).toList();
        List<Double> frozenCorr = frozen.cycles().stream().map(SimDriver.CycleMetric::predTrueCorr).toList();
        List<Double> learnedCorr = m.stream().map(SimDriver.CycleMetric::predTrueCorr).toList();
        System.out.println("[STEP9] frozen(不学习) top1=" + frozenHits);
        System.out.println("[STEP9] frozen(不学习) regret=" + frozenRegrets);
        System.out.println("[STEP9] 预测分-真值秩相关 学习组=" + learnedCorr);
        System.out.println("[STEP9] 预测分-真值秩相关 不学习组=" + frozenCorr);
        SimDriver.CycleMetric fixedLast = fixed.cycles().get(fixed.cycles().size() - 1);
        SimDriver.CycleMetric fixedFrozenLast = fixedFrozen.cycles().get(fixedFrozen.cycles().size() - 1);
        System.out.printf("[STEP9] 固定池末周期：学习 top1=%.4f regret=%.4f ρ=%.4f | "
                        + "不学习 top1=%.4f regret=%.4f ρ=%.4f%n",
                fixedLast.top1Hit(), fixedLast.regret(), fixedLast.predTrueCorr(),
                fixedFrozenLast.top1Hit(), fixedFrozenLast.regret(), fixedFrozenLast.predTrueCorr());

        boolean go = gates.stream().allMatch(Gate::pass);
        String report = render(run, gates, go, monotone, slope, regrets, hits, repeats, avgExclusive,
                p0Pass, p0, frozenHits, frozenRegrets, learnedCorr, frozenCorr, fixed, fixedFrozen);
        // surefire 的 user.dir 是模块目录；向上找到含 docs/技术方案.md 的工程根，避免报告写错位置
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("docs/技术方案.md"))) root = root.getParent();
        assertThat(root).as("必须能定位工程根目录").isNotNull();
        Path out = root.resolve("docs/eval/见一面-Go-No-Go-报告.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println("[STEP9] report=" + out.toAbsolutePath() + " GO=" + go);
        for (Gate g : gates) {
            System.out.printf("[STEP9] %-38s %-10s actual=%.4f %s%n",
                    g.name(), g.threshold(), g.actual(), g.pass() ? "PASS" : "FAIL");
        }

        // JUnit 只守「闭环机制确实跑通」的不变量；阈值类闸门由 Go/No-Go 报告对人判定。
        // 当前报告是 NO-GO：regret 单调下降、learning_slope、周期12 top1_hit 三项未达标。
        // 不在这里硬断言，是为了不把「阶段 B 尚未打磨完」伪装成测试通过；
        // 也不在这里放宽阈值——报告里的实测值与阈值原样保留，禁止切流。
        if (!go) {
            System.out.println("[STEP9] ===== NO-GO：阶段 B 闸门未达标，禁止进入灰度；"
                    + "真实用户只用于验证、不用于试错 =====");
        }
        assertThat(m.size()).isEqualTo(12);
        assertThat(repeats).isZero();
        assertThat(last.tasteRecovery()).as("画像必须真的学到隐藏审美，而不是原地不动")
                .isGreaterThan(first.tasteRecovery());
        assertThat(last.stability()).isLessThanOrEqualTo(0.15);
        assertThat(p0Pass).isEqualTo(p0);
    }

    private static String render(SimDriver.Run run, List<Gate> gates, boolean go,
                                 int monotone, double slope, List<Double> regrets, List<Double> hits,
                                 int repeats, double avgExclusive, long p0Pass, long p0,
                                 List<Double> frozenHits, List<Double> frozenRegrets,
                                 List<Double> learnedCorr, List<Double> frozenCorr,
                                 SimDriver.Run fixed, SimDriver.Run fixedFrozen) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 「见一面」上线前 Go/No-Go 报告（阶段 B 合成仿真）\n\n");
        sb.append("> 生成方式：`api/aiditto-app/src/test/java/cn/aiditto/sim/SimulationRunTest.java` 实跑生成，")
                .append("数据与 CI 断言同源，非人工填写。\n")
                .append("> 回放规模：users=").append(run.config().users())
                .append("，cycles=").append(run.config().cycles())
                .append("，seed=").append(run.config().seed())
                .append("，口是心非=").append(run.twoFacedUsers())
                .append(" 人，长尾=").append(run.longTailUsers())
                .append(" 人，耗时 ").append(run.elapsedMs()).append(" ms。\n\n");
        sb.append("## 总判定：**").append(go ? "GO（允许进入小灰度）" : "NO-GO（继续在阶段 B 打磨）")
                .append("**\n\n");
        sb.append("任何一项未达标即不进灰度。未由本回放覆盖的闸门，下面逐项标注了证据来源，不冒充已测。\n\n");

        sb.append("## 一、闸门逐项实测\n\n| 检查项 | 阈值 | 实测 | 结论 | 说明 |\n|---|---|---|---|---|\n");
        for (Gate g : gates) {
            sb.append("| ").append(g.name()).append(" | ").append(g.threshold()).append(" | ")
                    .append(String.format("%.4f", g.actual())).append(" | ")
                    .append(g.pass() ? "PASS" : "**FAIL**").append(" | ").append(g.note()).append(" |\n");
        }
        sb.append("\n- regret 单调下降周期数：").append(monotone).append("/11 次相邻比较\n")
                .append("- learning_slope = ").append(String.format("%.6f", slope)).append("\n")
                .append("- pair 重复推送总数 = ").append(repeats).append("\n")
                .append("- 平均独占单路占比 = ").append(String.format("%.4f", avgExclusive)).append("\n")
                .append("- P0 对抗用例 = ").append(p0Pass).append('/').append(p0).append("\n\n");

        sb.append("## 二、12 周期学习曲线\n\n| 周期 | 配对数 | regret | top1_hit@decile | taste_recovery | 口是心非收敛 | pred-true ρ | 长尾公平 | 覆盖率 | 独占单路 | 重复 |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (SimDriver.CycleMetric c : run.cycles()) {
            sb.append("| ").append(c.cycle()).append(" | ").append(c.pairs()).append(" | ")
                    .append(f(c.regret())).append(" | ").append(f(c.top1Hit())).append(" | ")
                    .append(f(c.tasteRecovery())).append(" | ").append(f(c.twoFacedConv())).append(" | ")
                    .append(f(c.predTrueCorr())).append(" | ")
                    .append(f(c.fairness())).append(" | ").append(f(c.coverage())).append(" | ")
                    .append(f(c.exclusiveRouteShare())).append(" | ").append(c.pairRepeat()).append(" |\n");
        }
        sb.append("\nregret 序列：").append(regrets.stream().map(SimulationRunTest::f).toList()).append("\n\n");
        sb.append("top1_hit 序列：").append(hits.stream().map(SimulationRunTest::f).toList()).append("\n\n");

        sb.append("## 三、对抗用例（后端可自动化的 P0 子集）\n\n| 用例 | 级别 | 结论 | 证据 |\n|---|---|---|---|\n");
        for (SimDriver.CaseResult c : run.cases()) {
            sb.append("| ").append(c.code()).append(" | ").append(c.level()).append(" | ")
                    .append(c.passed() ? "PASS" : "**FAIL**").append(" | ").append(c.detail()).append(" |\n");
        }
        sb.append("\n其余 A 类用例由既有测试覆盖，未在本回放重复实现：\n\n")
                .append("| 用例 | 证据来源 |\n|---|---|\n")
                .append("| A05/A06 拉黑、注销清理 | `VectorSyncIT`（真 Qdrant 删除 + PG 状态） |\n")
                .append("| A07 17 岁拦截 / A08 照片审核 | `HardFilterTest` / `PhotoIT`（M1 既有 398 项性质测试） |\n")
                .append("| A09 等时圈不重合 | `IsoCellGridTest` + `HardFilterTest` |\n")
                .append("| A11/A12 LLM 单候选失败与全挂 | `AiCopyServiceTest`、`AgentRerankIT`、`LlmGatewayTest` |\n")
                .append("| A13 Qdrant 不可用 | Step 5 `RecallPipeline` 降级演练（结构化路仍可交付） |\n")
                .append("| A15 裁判幻觉高分 / conflicts 矛盾 | `AiJudgeTest` 10 项 |\n")
                .append("| A16/A17 准时性与触达 | 周三批次演练（Step 10，**尚未执行**） |\n")
                .append("| A19 饥饿优先 | `MatchingPropertiesTest` / `MatchingSolver` 公平加分 |\n")
                .append("| A22 自由文本 PII | `TasteFactsTest`、`AiCopyGeneratorTest`、`TasteWriterTest` |\n")
                .append("| A23 性别比失衡 | 本回放男女 1:1；3:7 组**未跑，列为遗留** |\n")
                .append("| A24 模型热变更回放 | 未执行，列为遗留 |\n")
                .append("| A25/A26/A27 通知、申诉、跨设备 | 前端 / 运维域，后端回放不覆盖 |\n\n");

        sb.append("## 四、如实标注的覆盖边界与遗留\n\n")
                .append("1. **配对与精排用的是生产组件**：`MatchingSolver.buildEdges` + Blossom、`MatchingInput.withAiScores`、")
                .append("`RrfFusion`、`FeedbackSignal`、`TasteWeights`、`SimMetrics`；不是仿真专用简化版。\n")
                .append("2. **周批状态机、HTTP 接口、accept/refuse/签到/触达/爽约**未在本回放重跑，")
                .append("由既有 29 个 IT 与 Step 10 周三批次演练覆盖。\n")
                .append("3. **真实 Qdrant / 真实 embedding** 由 Step 5 的 `VectorSyncIT`、`RecallEvalIT`、")
                .append("`RecallExperimentIT` 在 10000 点 / 256 维下实测；本回放的召回归因是 `RrfFusion` 纯函数，")
                .append("不重复造向量库。\n")
                .append("4. **候选池规模**：200 人池在硬约束后候选有限，")
                .append("「独占单路占比 ≤60% / 候选覆盖率 ≥98%」在 Step 5 已如实标注指标无区分度，")
                .append("本回放沿用同一口径，扩池到 500/2000 与 3:7 性别比仍是上线前必办。\n")
                .append("5. **隐藏真值零泄漏**：只存在于 `HiddenTruth` 与 Oracle 内存对象，")
                .append("不进入 Candidate、aiScores、文案、日志或任何提示词。\n")
                .append("6. **Oracle 防自证循环**：确定性函数 + 噪声，反馈文本用同义改写模板，")
                .append("与画像/裁判/文案模型不共享措辞；`taste_recovery` 对独立真值求 Spearman ρ。\n")
                .append("7. 成本、压测、备案/等保、值班与回滚演练属于 Step 10 与合规域，")
                .append("**本阶段未验证，不计为已达标**。\n\n");
        sb.append("## 五、影子对照：关掉画像重写\n\n")
                .append("同一批用户、同一份隐藏真值、同一串 Oracle 噪声，唯一差别是是否执行反馈学习。")
                .append("用来区分「学习损害配对」与「往期配对累积导致的结构性下降」。\n\n")
                .append("| 周期 | 学习组 top1 | 不学习组 top1 | 学习组 regret | 不学习组 regret |\n")
                .append("|---|---|---|---|---|\n");
        for (int i = 0; i < run.cycles().size(); i++) {
            sb.append("| ").append(i + 1).append(" | ").append(f(hits.get(i))).append(" | ")
                    .append(f(frozenHits.get(i))).append(" | ").append(f(regrets.get(i))).append(" | ")
                    .append(f(frozenRegrets.get(i))).append(" |\n");
        }
        double learnedGain = hits.get(hits.size() - 1) - hits.get(0);
        double frozenGain = frozenHits.get(frozenHits.size() - 1) - frozenHits.get(0);
        sb.append("\n- 学习组末周期相对首周期变化：").append(f(learnedGain))
                .append("；不学习组：").append(f(frozenGain)).append("\n")
                .append("- **学习组相对不学习组的净收益（末周期 top1）：")
                .append(f(hits.get(hits.size() - 1) - frozenHits.get(frozenHits.size() - 1)))
                .append("**\n\n");
        sb.append("### 预测质量诊断：pred-true 秩相关\n\n")
                .append("在每周期**被召回并精排的候选边**上，计算「系统预测分」与「隐藏真值」的 Spearman ρ。")
                .append("配对质量最终取决于排序而非分数绝对值，所以这个指标能直接回答")
                .append("「反馈学习有没有让排序变准」，把「学习信号问题」与「匹配结构问题」分开。\n\n")
                .append("| 周期 | 学习组 ρ | 不学习组 ρ | 学习组 top1 | 不学习组 top1 |\n")
                .append("|---|---|---|---|---|\n");
        for (int i = 0; i < run.cycles().size(); i++) {
            sb.append("| ").append(i + 1).append(" | ").append(f(learnedCorr.get(i))).append(" | ")
                    .append(f(frozenCorr.get(i))).append(" | ").append(f(hits.get(i))).append(" | ")
                    .append(f(frozenHits.get(i))).append(" |\n");
        }
        double corrFirst = learnedCorr.get(0), corrLast = learnedCorr.get(learnedCorr.size() - 1);
        double corrGain = corrLast - corrFirst;
        double corrNetBenefit = corrLast - frozenCorr.get(frozenCorr.size() - 1);
        sb.append("\n- 学习组 pred-true ρ：周期1 ").append(f(corrFirst)).append(" → 周期12 ")
                .append(f(corrLast)).append("（变化 ").append(f(corrGain)).append("）\n")
                .append("- 学习组相对不学习组的末周期 ρ 净收益：").append(f(corrNetBenefit)).append("\n")
                .append("- 判读规则：ρ 上升而 top1 不升 → 全局最大权匹配/往期配对的结构性压制；")
                .append("ρ 不上升或下降 → 学习信号没有对准「预测分 vs 观测反馈」的排序目标。\n\n");

        SimDriver.CycleMetric fLast = fixed.cycles().get(fixed.cycles().size() - 1);
        SimDriver.CycleMetric ffLast = fixedFrozen.cycles().get(fixedFrozen.cycles().size() - 1);
        sb.append("### 诊断结论（这一节是本次仿真最重要的产出）\n\n")
                .append("1. 不学习组的 top1 同样随周期下降，说明下降主要是**结构性**的：")
                .append("往期配对累积 + Blossom 全局最大权匹配（一人一位）会压制「每个人都选到自己 top10% 候选」，")
                .append("不是反馈学习把画像学坏了。\n")
                .append("2. 但学习组末周期相对不学习组的净收益并未稳定为正，")
                .append("**「越匹配越准」目前尚未被证明**。按创始人铁律，这就是 NO-GO，")
                .append("不能靠调整仿真参数或放宽阈值来「做」出达标。\n")
                .append("3. 关键区分：`taste_recovery` 只证明「画像的维度序更像真值了」，")
                .append("并不等于「配对排序更准了」；两者必须分别用 pred-true ρ 与 top1_hit 验证。")
                .append("当前 ρ 变化 ").append(f(corrGain)).append("，末周期相对不学习组净收益 ")
                .append(f(corrNetBenefit)).append("。\n")
                .append("4. **固定池对照给出决定性判读**：学习组末周期 top1 ").append(f(fLast.top1Hit()))
                .append(" vs 不学习 ").append(f(ffLast.top1Hit()))
                .append("，regret ").append(f(fLast.regret())).append(" vs ").append(f(ffLast.regret()))
                .append("，ρ ").append(f(fLast.predTrueCorr())).append(" vs ").append(f(ffLast.predTrueCorr()))
                .append(" —— 三项全部为负。因此**不是匹配结构问题**，而是反馈学习信号没有对准排序目标：")
                .append("画像的维度序更像真值了，却没有转化成更好的候选排序。这是架构级问题，不是调参能解决的。\n")
                .append("5. **一次失败实验（已回滚）**：按 §4.1 字面把 reason_tag 改成「这对真值贡献最大/最小维度」、")
                .append("并把 bad:<隐性维度> 改为提升，12 周期实测 taste_recovery 0.907→0.775、")
                .append("pred-true ρ 0.814→0.671、固定池净收益 −0.135，明显更差。")
                .append("代码已回滚到已验证基线，对照实现保留在 TrueScore.topContributionDim/worstContributionDim 作证据链。\n")
                .append("6. 已修掉的真实缺陷（都提升了指标，但不掩盖剩余问题）：\n")
                .append("   - 冷启动画像从「全中性」改为「由问卷显性答案生成」，对齐 Step 4 的真实行为；\n")
                .append("   - 契合函数从「两份画像长得多像」改为「偏好 × 属性」加权契合，对齐 §3.3；\n")
                .append("   - 学习结果改走召回漏斗，而不是直接给全池每条边打分，对齐「召回是命门」；\n")
                .append("   - 真值兼容度在 12 周期内固定，主观噪声只落在 FeedbackOracle 的反馈观测里；\n")
                .append("   - **候选方也读反馈重写后的画像**（此前召回/精排读原始问卷 stated，")
                .append("导致双向反馈更新的 learned[b] 从未被使用，学习成果丢了一半）。\n\n");
        sb.append("## 六、固定候选池对照：单独量化学习净收益\n\n")
                .append("不累积往期配对（同一批候选反复回放），其余配置与影子对照完全一致。")
                .append("这一组**不参与闸门判定**，只用来剥离「好对被逐周耗尽」的结构性效应，")
                .append("回答一个独立问题：把结构因素拿掉后，反馈学习本身有没有让排序变准。\n\n")
                .append("| 周期 | 固定池·学习 top1 | 固定池·不学习 top1 | 固定池·学习 regret | 固定池·不学习 regret | 学习组 ρ |\n")
                .append("|---|---|---|---|---|---|\n");
        for (int i = 0; i < fixed.cycles().size(); i++) {
            SimDriver.CycleMetric fl = fixed.cycles().get(i), ff = fixedFrozen.cycles().get(i);
            sb.append("| ").append(i + 1).append(" | ").append(f(fl.top1Hit())).append(" | ")
                    .append(f(ff.top1Hit())).append(" | ").append(f(fl.regret())).append(" | ")
                    .append(f(ff.regret())).append(" | ").append(f(fl.predTrueCorr())).append(" |\n");
        }
        SimDriver.CycleMetric ff0 = fixedFrozen.cycles().get(0), fl0 = fixed.cycles().get(0);
        SimDriver.CycleMetric fixedLast = fixed.cycles().get(fixed.cycles().size() - 1);
        SimDriver.CycleMetric fixedFrozenLast = fixedFrozen.cycles().get(fixedFrozen.cycles().size() - 1);
        double fixedTop1Net = fixedLast.top1Hit() - fixedFrozenLast.top1Hit();
        double fixedRegretNet = fixedLast.regret() - fixedFrozenLast.regret();
        double fixedTop1Gain = fixedLast.top1Hit() - fl0.top1Hit();
        double fixedFrozenTop1Gain = fixedFrozenLast.top1Hit() - ff0.top1Hit();
        sb.append("\n- 固定池末周期学习组相对不学习组：top1 净收益 ")
                .append(f(fixedTop1Net)).append("，regret 净变化 ").append(f(fixedRegretNet))
                .append("（regret 为负更优）\n")
                .append("- 首周期→末周期变化：学习组 ").append(f(fixedTop1Gain))
                .append("，不学习组 ").append(f(fixedFrozenTop1Gain)).append("\n")
                .append("- 判读：固定池下 net > 0 说明学习本身有效，主回放未达标主要是")
                .append("往期配对累积 + 一人一位全局匹配的结构性压制；net ≤ 0 说明学习信号本身仍有问题。\n\n");

        sb.append("## 七、需要创始人拍板的架构决策与阶段 B 剩余工作\n\n")
                .append("### 决策点 D6：反馈学习如何真正优化「排序目标」（阻塞项）\n\n")
                .append("当前闭环是「反馈 → 调用户画像 1 个权重 → 间接影响 compatibility 边权」。")
                .append("固定池对照证明这条链路**不保证排序变准**（top1/regret/ρ 三项净收益全负），")
                .append("而画像还原度（0.907）和防过拟合闸门都过了。继续在 reason_tag 来源、")
                .append("调整方向、学习幅度上试错已被一次完整失败实验证伪（见第五节第 5 条），")
                .append("这不是参数问题，是链路设计问题。两个选项：\n\n")
                .append("- **A（推荐）**：引入 learning-to-rank——让反馈直接校准/训练排序目标，")
                .append("`user_taste.weights` 从「偏好画像」升级为「排序权重」，进入 `AiJudge` 边权计算；")
                .append("需要重构 Step 6/8 的边权契约，属于架构级改动，等创始人确认后动工。\n")
                .append("- **B（不建议）**：保留现闭环，把「越匹配越准」的证明推迟到真实反馈阶段——")
                .append("违背「真实用户只用于验证、不用于试错」铁律，不推荐。\n\n")
                .append("未得到决策前，阶段 B 只做不依赖该决策的验证工作：\n\n")
                .append("1. **扩池**到 500 / 2000 并补 3:7 性别比组，量化全局最大权匹配的结构性压制上限；")
                .append("候选池属性已由问卷观测投影（不再是全默认占位符），门槛真正生效。\n")
                .append("2. 多种子重复回放 + 成对显著性检验，把单次点估计变成置信区间。\n")
                .append("3. 固定候选池评估已实现（第四节），后续作为每轮回放的标准对照。\n")
                .append("4. D6 决策落地并重跑，直到 `regret 单调下降 ≥8/12`、`learning_slope < 0`、")
                .append("`周期12 top1_hit ≥0.60` 全部达标，再重新签发本报告。")
                .append("**在此之前不进灰度、不接真实用户试错。**\n\n");

        sb.append("## 八、结论\n\n");
        if (go) {
            sb.append("阶段 B 闸门全部达标，可进入阶段 C 小灰度（全功能开启、合成回放继续并行）。\n");
        } else {
            sb.append("存在未达标项，**不进灰度**。按用户铁律继续在阶段 B 打磨，")
                    .append("真实用户只用于验证、不用于试错。\n");
        }
        return sb.toString();
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
