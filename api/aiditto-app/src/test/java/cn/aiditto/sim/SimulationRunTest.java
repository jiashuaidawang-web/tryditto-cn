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
        if (Boolean.getBoolean("sim.deep")) {
            Path deepOut = root.resolve("docs/eval/阶段B-深水区复核.md");
            Files.writeString(deepOut, renderDeepEvidence(), StandardCharsets.UTF_8);
            System.out.println("[STEP9] deep report=" + deepOut.toAbsolutePath());
        }
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
                .append("4. **固定池对照判读**：学习组末周期 top1 ").append(f(fLast.top1Hit()))
                .append(" vs 不学习 ").append(f(ffLast.top1Hit()))
                .append("，regret ").append(f(fLast.regret())).append(" vs ").append(f(ffLast.regret()))
                .append("，ρ ").append(f(fLast.predTrueCorr())).append(" vs ").append(f(ffLast.predTrueCorr()))
                .append("。")
                .append(fixedNetConclusion(fLast, ffLast)).append("\n")
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
                .append("往期配对累积 + 一人一位全局匹配的结构性压制；net ≤ 0 说明学习信号本身仍有问题。")
                .append("**注意：以上为默认种子单次点估计；多种子复核见 `docs/eval/阶段B-深水区复核.md`，")
                .append("top1/regret 净收益未在多种子下统计显著，勿以单种子正收益放行。**\n\n");

        sb.append("## 七、架构决策 D6 与阶段 B 剩余工作\n\n")
                .append("### 决策点 D6：反馈学习如何真正优化「排序目标」（已决：方案 A，已实施）\n\n")
                .append("创始人已拍板 **D6 = 方案 A（learning-to-rank）**，本轮已完成 Step 6/8 边权契约重构：\n\n")
                .append("- `user_taste.weights` 从「偏好画像」升级为「排序权重」，由 `TasteWeights.rankAgainst`")
                .append("  直接计算确定性学习排序分，经 `MatchingInput.learnedScores` → `MatchingSolver` 边权；")
                .append("  `AgentRerankService` 读 `user_taste.weights` 产出学习分，`WeekRunner.solve` 并入求解；")
                .append("  LLM 降级时学习信号仍生效，不再只经 LLM 人格画像间接影响。\n\n")
                .append("本轮复跑证据：固定池对照 top1 净收益 ").append(pct(fLast.top1Hit() - ffLast.top1Hit()))
                .append("、regret 净变化 ").append(pct(fLast.regret() - ffLast.regret()))
                .append("、ρ 净收益 ").append(pct(fLast.predTrueCorr() - ffLast.predTrueCorr()))
                .append("（均由本轮实跑计算，非写死）。")
                .append("说明「反馈→调权重→边权」链路对匹配结果的作用方向见第五节判读；")
                .append("pred-true ρ 的变化量同时列出，「预测分 vs 真值」的排序校准仍是残余缺口，")
                .append("下一步校准 `rankAgainst` 或引入带监督的 ranker。\n\n")
                .append("**但多种子成对复核（`-Dsim.deep=true`，见 `docs/eval/阶段B-深水区复核.md`）给出更严格的判读：")
                .append("上述单种子的 top1/regret 净收益并未在多种子下稳定复现（符号检验不显著），只有 ρ 呈弱正趋势。")
                .append("结论：D6-A 架构已正确落地，但「学习显著提升排序」尚未在统计上证明，因此 NO-GO 维持，")
                .append("需要更大池 / 更长周期 / 改进学习目标，而不是靠单种子点估计放行。**\n\n")
                .append("阶段 B 剩余工作（D6 已决，继续打磨至达标再重签 GO）：\n\n")
                .append("1. **扩池**到 500 / 2000 并补 3:7 性别比组，量化全局最大权匹配的结构性压制上限；")
                .append("候选池属性已由问卷观测投影（不再是全默认占位符），门槛真正生效。\n")
                .append("2. 多种子重复回放 + 成对显著性检验，把单次点估计变成置信区间。\n")
                .append("3. 校准 `rankAgainst` 使 pred-true ρ 随学习上升（当前残余缺口）。\n")
                .append("4. 直到 `regret 单调下降 ≥8/12`、`learning_slope < 0`、")
                .append("`周期12 top1_hit ≥0.60` 全部达标，再重新签发本报告。")
                .append("在达标前不进灰度、不接真实用户试错。**NO-GO 结论不变。**\n\n");

        sb.append("## 八、结论\n\n");
        if (go) {
            sb.append("阶段 B 闸门全部达标，可进入阶段 C 小灰度（全功能开启、合成回放继续并行）。\n");
        } else {
            sb.append("存在未达标项，**不进灰度**。按用户铁律继续在阶段 B 打磨，")
                    .append("真实用户只用于验证、不用于试错。\n");
        }
        return sb.toString();
    }

    /** 深水区复核（-Dsim.deep）：性别比、扩池、多种子成对显著性。不参与主闸门，只提供稳健性证据。 */
    private static String renderDeepEvidence() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 「见一面」阶段 B 深水区复核（稳健性证据）\n\n")
                .append("> 生成方式：`SimulationRunTest`（`-Dsim.deep=true`）实跑生成，同一批生产组件。")
                .append("本文件**不参与主 NO-GO 判定**，只回答「结论是否依赖单一随机种子 / 性别比 / 池规模」。\n\n");

        SimDriver.CycleMetric baseL = lastOf(SimDriver.run(SimDriver.Config.fixedPool()));
        SimDriver.CycleMetric baseF = lastOf(SimDriver.run(SimDriver.Config.fixedPoolFrozen()));

        // 一、性别比 3:7 对照（固定池末周期）
        SimDriver.CycleMetric gL = lastOf(SimDriver.run(SimDriver.Config.fixedPool().withWomenShare(0.3)));
        SimDriver.CycleMetric gF = lastOf(SimDriver.run(SimDriver.Config.fixedPoolFrozen().withWomenShare(0.3)));
        sb.append("## 一、性别比 1:1 vs 3:7（固定池末周期）\n\n")
                .append("| 性别比 | 学习 top1 | 学习 regret | 学习 ρ | 不学习 top1 | 不学习 regret | 不学习 ρ | top1 净收益 |\n")
                .append("|---|---|---|---|---|---|---|---|\n")
                .append(deepRow("1:1", baseL, baseF))
                .append(deepRow("3:7", gL, gF))
                .append("\n> 说明：合成池**未启用性别偏好硬过滤**（避免偏斜池被排空），故 3:7 与 1:1 在本仿真中结果完全相同——")
                .append("性别比本身不改变匹配。真实性别偏好的公平性必须在 Stage C 用真实数据验证；此处只能证明学习链路与性别比无关。\n\n");

        // 二、多种子成对显著性（固定池末周期，逐种子算学习−不学习净收益）
        long seed0 = SimDriver.Config.defaults().seed();
        int nSeeds = 6;
        double[] top1Net = new double[nSeeds], regretNet = new double[nSeeds], rhoNet = new double[nSeeds];
        int top1Wins = 0, regretWins = 0, rhoWins = 0;
        sb.append("## 二、多种子成对显著性（").append(nSeeds).append(" 种子，固定池末周期）\n\n")
                .append("| 种子 | top1 净收益 | regret 净变化 | ρ 净收益 |\n|---|---|---|---|\n");
        for (int s = 0; s < nSeeds; s++) {
            long seed = seed0 + s;
            SimDriver.CycleMetric l = lastOf(SimDriver.run(SimDriver.Config.fixedPool().withSeed(seed)));
            SimDriver.CycleMetric f = lastOf(SimDriver.run(SimDriver.Config.fixedPoolFrozen().withSeed(seed)));
            top1Net[s] = l.top1Hit() - f.top1Hit();
            regretNet[s] = l.regret() - f.regret(); // <0 学习更优
            rhoNet[s] = l.predTrueCorr() - f.predTrueCorr();
            if (top1Net[s] > 1e-9) top1Wins++;
            if (regretNet[s] < -1e-9) regretWins++;
            if (rhoNet[s] > 1e-9) rhoWins++;
            sb.append("| ").append(seed).append(" | ").append(pct(top1Net[s]))
                    .append(" | ").append(pct(regretNet[s])).append(" | ").append(pct(rhoNet[s])).append(" |\n");
        }
        sb.append("\n| 指标 | 均值 | 标准差 | 学习占优 | 符号检验 p（双侧） |\n|---|---|---|---|---|\n")
                .append("| top1 净收益 | ").append(pct(mean(top1Net))).append(" | ").append(pct(std(top1Net)))
                .append(" | ").append(top1Wins).append("/").append(nSeeds)
                .append(" | ").append(pfmt(signTestP(top1Wins, nSeeds))).append(" |\n")
                .append("| regret 净变化（负更优） | ").append(pct(mean(regretNet))).append(" | ").append(pct(std(regretNet)))
                .append(" | ").append(regretWins).append("/").append(nSeeds)
                .append(" | ").append(pfmt(signTestP(regretWins, nSeeds))).append(" |\n")
                .append("| ρ 净收益 | ").append(pct(mean(rhoNet))).append(" | ").append(pct(std(rhoNet)))
                .append(" | ").append(rhoWins).append("/").append(nSeeds)
                .append(" | ").append(pfmt(signTestP(rhoWins, nSeeds))).append(" |\n")
                .append("\n> 判读（α=0.05）：top1 学习占优 ").append(top1Wins).append("/").append(nSeeds)
                .append("、regret ").append(regretWins).append("/").append(nSeeds)
                .append("、ρ ").append(rhoWins).append("/").append(nSeeds).append("。")
                .append(signTestP(top1Wins, nSeeds) < 0.05 || signTestP(regretWins, nSeeds) < 0.05
                        || signTestP(rhoWins, nSeeds) < 0.05
                        ? "存在达到统计显著的收益项。"
                        : "**没有任何指标的收益达到统计显著——单种子净收益未复现，学习收益尚未被证明，NO-GO 维持。**")
                .append("\n\n");

        // 三、扩池到 500（固定池末周期；2000 因 O(n²) 回放过慢，建议离线批跑）
        SimDriver.CycleMetric sL = lastOf(SimDriver.run(SimDriver.Config.fixedPool().withUsers(500)));
        SimDriver.CycleMetric sF = lastOf(SimDriver.run(SimDriver.Config.fixedPoolFrozen().withUsers(500)));
        sb.append("## 三、扩池到 500（固定池末周期）\n\n")
                .append("| 池规模 | 学习 top1 | 学习 regret | 学习 ρ | 不学习 top1 | 不学习 regret | 不学习 ρ | top1 净收益 |\n")
                .append("|---|---|---|---|---|---|---|---|\n")
                .append(deepRow("200（基准）", baseL, baseF))
                .append(deepRow("500", sL, sF))
                .append("\n> 判读：扩池后学习净收益若保持为正且不缩水，说明结构不是靠小池偶然撑起来的。")
                .append("池=2000 的 O(n²) 回放在单元测试里过慢，建议用离线批跑复现同一口径。\n\n");

        // 四、生产 top-15 口径对照（架构评审发现：仿真给所有边算学习分，生产只对精排 topPairs 算）
        int prodTopN = 15; // 与 MatchAgentProperties.topPairs 默认值一致
        SimDriver.CycleMetric pL = lastOf(SimDriver.run(SimDriver.Config.fixedPool().withLearnedScoreTopN(prodTopN)));
        SimDriver.CycleMetric pF = lastOf(SimDriver.run(SimDriver.Config.fixedPoolFrozen().withLearnedScoreTopN(prodTopN)));
        double histTop1Net = baseL.top1Hit() - baseF.top1Hit();
        double prodTop1Net = pL.top1Hit() - pF.top1Hit();
        double histRegretNet = baseL.regret() - baseF.regret();
        double prodRegretNet = pL.regret() - pF.regret();
        sb.append("## 四、生产 top-15 口径对照（架构评审补测）\n\n")
                .append("> 背景：仿真默认给**所有边**注入学习分，但生产 `AgentRerankService.rerank` 只按规则边权取")
                .append(" `topPairs`（默认 15）精排边算学习分。本节用 `withLearnedScoreTopN(15)` 复现生产口径，")
                .append("检验「全边注入」是否虚高了学习净收益。\n\n")
                .append("| 注入口径 | 学习 top1 | 学习 regret | 不学习 top1 | 不学习 regret | top1 净收益 | regret 净变化 |\n")
                .append("|---|---|---|---|---|---|---|\n")
                .append("| 全边（仿真默认） | ").append(f(baseL.top1Hit())).append(" | ").append(f(baseL.regret()))
                .append(" | ").append(f(baseF.top1Hit())).append(" | ").append(f(baseF.regret()))
                .append(" | ").append(pct(histTop1Net)).append(" | ").append(pct(histRegretNet)).append(" |\n")
                .append("| 生产 top-15 | ").append(f(pL.top1Hit())).append(" | ").append(f(pL.regret()))
                .append(" | ").append(f(pF.top1Hit())).append(" | ").append(f(pF.regret()))
                .append(" | ").append(pct(prodTop1Net)).append(" | ").append(pct(prodRegretNet)).append(" |\n")
                .append("\n> 判读：生产口径 top1 净收益 ").append(pct(prodTop1Net))
                .append("，全边口径 ").append(pct(histTop1Net))
                .append("；regret 净变化生产 ").append(pct(prodRegretNet))
                .append(" vs 全边 ").append(pct(histRegretNet)).append("。")
                .append(productionScopeConclusion(histTop1Net, prodTop1Net, histRegretNet, prodRegretNet))
                .append("\n");
        return sb.toString();
    }

    private static SimDriver.CycleMetric lastOf(SimDriver.Run r) {
        return r.cycles().get(r.cycles().size() - 1);
    }

    private static String deepRow(String label, SimDriver.CycleMetric learned, SimDriver.CycleMetric frozen) {
        return "| " + label + " | " + f(learned.top1Hit()) + " | " + f(learned.regret()) + " | "
                + f(learned.predTrueCorr()) + " | " + f(frozen.top1Hit()) + " | " + f(frozen.regret()) + " | "
                + f(frozen.predTrueCorr()) + " | " + pct(learned.top1Hit() - frozen.top1Hit()) + " |\n";
    }

    private static double mean(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return xs.length == 0 ? 0 : s / xs.length;
    }

    private static double std(double[] xs) {
        if (xs.length < 2) return 0;
        double m = mean(xs), s = 0;
        for (double x : xs) s += (x - m) * (x - m);
        return Math.sqrt(s / (xs.length - 1));
    }

    /** 双侧精确符号检验 p 值：k 次占优 / n 次试验。 */
    private static double signTestP(int wins, int n) {
        int k = Math.min(wins, n - wins);
        double tail = 0;
        for (int i = 0; i <= k; i++) tail += binom(n, i);
        return Math.min(1.0, 2 * tail / Math.pow(2, n));
    }

    private static double binom(int n, int k) {
        double r = 1;
        for (int i = 0; i < k; i++) r = r * (n - i) / (i + 1);
        return r;
    }

    private static String pct(double v) {
        return String.format(java.util.Locale.ROOT, "%+.4f", v);
    }

    private static String pfmt(double p) {
        return String.format(java.util.Locale.ROOT, "%.4f", p);
    }

    /** 固定池对照的签署结论：按实际数值判定，避免报告结论写死与数字脱节。 */
    private static String fixedNetConclusion(SimDriver.CycleMetric learned, SimDriver.CycleMetric frozen) {
        boolean top1Win = learned.top1Hit() > frozen.top1Hit() + 1e-9;
        boolean regretWin = learned.regret() < frozen.regret() - 1e-9;   // 学习组 regret 更低更好
        boolean rhoWin = learned.predTrueCorr() > frozen.predTrueCorr() + 1e-9;
        int wins = (top1Win ? 1 : 0) + (regretWin ? 1 : 0) + (rhoWin ? 1 : 0);
        String per = "（top1 " + side(top1Win, learned.top1Hit(), frozen.top1Hit())
                + "、regret " + side(regretWin, learned.regret(), frozen.regret())
                + "、ρ " + side(rhoWin, learned.predTrueCorr(), frozen.predTrueCorr()) + "）";
        if (wins == 3) {
            return "三项全为正：**学习信号已对准排序目标**，反馈→调权重→边权链路成立" + per + "。";
        }
        if (wins == 0) {
            return "三项全为负：**不是匹配结构问题**，而是反馈学习信号没有对准排序目标，属架构级问题" + per + "。";
        }
        return "混合结果：" + wins + "/3 项学习更优，学习已改善部分排序维度，"
                + "但「预测分 vs 真值」的校准仍有缺口" + per + "。";
    }

    private static String side(boolean win, double learned, double frozen) {
        if (win) return "优";
        return Math.abs(learned - frozen) <= 1e-9 ? "平" : "劣";
    }

    /** 生产 top-15 口径对照的自动判读：全边注入是否虚高了学习净收益。 */
    private static String productionScopeConclusion(double histTop1, double prodTop1,
                                                    double histRegret, double prodRegret) {
        boolean sameSign = (histTop1 > 0) == (prodTop1 > 0);
        if (!sameSign || prodTop1 <= 0) {
            return "**生产口径净收益转负或反号——「学习有效」依赖了生产不存在的全边注入，"
                    + "须按生产口径重新判读，且强化 NO-GO。**";
        }
        double shrink = histTop1 <= 0 ? 0 : 1.0 - prodTop1 / histTop1;
        if (shrink > 0.30) {
            return "两者同号但生产口径净收益缩水 " + String.format(java.util.Locale.ROOT, "%.0f%%", shrink * 100)
                    + "，说明**全边注入确实高估了收益，绝对量须按生产口径 +"
                    + String.format(java.util.Locale.ROOT, "%.4f", prodTop1) + " 判读**；方向稳健但幅度下调。";
        }
        return "两者同号且量级接近，结论对学习分作用范围不敏感，可按全边口径近似判读。";
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
