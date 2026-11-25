package cn.aiditto.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 修尺子实验（创始人 2026-11-24 拍板钥匙 A）：开池滚动窗口 vs 封闭池，24 种子成对显著性。
 *
 * 背景：旧仿真封闭池跑 12 周期，pastPairs 累积 + 全局最优 → 学习组/影子组一起跌，
 * 净收益被结构性下降淹没；6 种子 3 正 3 负，符号检验 p=1.0 是统计功效不足，不是"学习无效"。
 * 本实验：① 开池每周期换 20 人（贴近真实流动）② 种子 6→24 ③ 出均值/95%CI/符号检验，不出点估计。
 *
 * 默认跳过（重实验）；AIDITTO_OPENPOOL_EXP=1 开启。报告落 docs/eval/开池修尺子实验.md。
 * 不改生产代码、不写生产表、不碰真实用户。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_OPENPOOL_EXP", matches = "1")
class OpenPoolExperimentTest {

    private static final int SEEDS = 24;
    private static final int REFRESH = 10;   // 开池每周期换 10 人（200→310 人，控制 O(n²) 成本）

    private record Pair(Double top1, Double regret, Double rho) {}

    private static Pair last(SimDriver.Run run) {
        SimDriver.CycleMetric m = run.cycles().get(run.cycles().size() - 1);
        return new Pair(m.top1Hit(), m.regret(), m.predTrueCorr());
    }

    /** 净收益 = 学习组 - 影子组（同种子成对），返回 top1/regret/ρ 三条差值序列。 */
    private static List<Double[]> collect(SimDriver.Config learnCfg, SimDriver.Config frozenCfg, long[] seeds) {
        List<Double[]> diffs = new ArrayList<>();
        for (long s : seeds) {
            Pair learn = last(SimDriver.run(learnCfg.withSeed(s)));
            Pair frozen = last(SimDriver.run(frozenCfg.withSeed(s)));
            diffs.add(new Double[]{
                    learn.top1() - frozen.top1(),
                    learn.regret() - frozen.regret(),   // regret 越小越好，负=学习更好
                    learn.rho() - frozen.rho()});
        }
        return diffs;
    }

    private record Stat(double mean, double sd, double ciLo, double ciHi, int positive, double signP) {}

    private static Stat stat(List<Double> xs) {
        int n = xs.size();
        double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = xs.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / Math.max(1, n - 1);
        double sd = Math.sqrt(var);
        double ci = 1.96 * sd / Math.sqrt(n);   // 大样本近似 z=1.96
        long pos = xs.stream().filter(x -> x > 0).count();
        // 符号检验双侧 p = 2*P(X<=min(pos,neg))，X~Bin(n,0.5)
        int k = (int) Math.min(pos, n - pos);
        double tail = 0;
        for (int i = 0; i <= k; i++) tail += binom(n, i) / Math.pow(2, n);
        return new Stat(mean, sd, mean - ci, mean + ci, (int) pos, Math.min(1.0, 2 * tail));
    }

    private static double binom(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }

    private static double col(List<Double[]> diffs, int idx) {
        return diffs.stream().mapToDouble(d -> d[idx]).average().orElse(0);
    }

    private static List<Double> column(List<Double[]> diffs, int idx) {
        return diffs.stream().map(d -> d[idx]).toList();
    }

    @Test
    void openPoolVsClosedPool24Seeds() throws IOException {
        long[] seeds = new long[SEEDS];
        for (int i = 0; i < SEEDS; i++) seeds[i] = 20261118L + i;

        System.out.println("=== 修尺子实验：24 种子 × 4 组，每周期换 " + REFRESH + " 人 ===");
        long t0 = System.currentTimeMillis();
        List<Double[]> closed = collect(SimDriver.Config.defaults(), SimDriver.Config.frozen(), seeds);
        System.out.println("封闭池跑完 " + (System.currentTimeMillis() - t0) / 1000 + "s");
        List<Double[]> open = collect(SimDriver.Config.openPool(REFRESH), SimDriver.Config.openPoolFrozen(REFRESH), seeds);
        System.out.println("开池跑完 " + (System.currentTimeMillis() - t0) / 1000 + "s");

        Stat cTop1 = stat(column(closed, 0)), oTop1 = stat(column(open, 0));
        Stat cRegret = stat(column(closed, 1)), oRegret = stat(column(open, 1));
        Stat cRho = stat(column(closed, 2)), oRho = stat(column(open, 2));

        StringBuilder md = new StringBuilder();
        md.append("# 开池修尺子实验（24 种子，2026-11-24）\n\n");
        md.append("> 目的：验证「封闭池耗竭掩盖学习信号」假设。开池每周期换 ").append(REFRESH)
                .append(" 人（200 人池的 10%），种子 6→24，出 95% 置信区间 + 符号检验。\n")
                .append("> 净收益 = 学习组 − 影子组（同种子成对）；regret 负=学习更好。\n\n");
        md.append("## 结果对比（末周期净收益，均值 ± 95%CI）\n\n");
        md.append("| 模式 | 指标 | 均值 | 95%CI | 正值/总数 | 符号检验 p | 判读 |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        md.append(row("封闭池", "top1 净收益", cTop1)).append("\n");
        md.append(row("封闭池", "regret 净变化", cRegret)).append("\n");
        md.append(row("封闭池", "ρ 净收益", cRho)).append("\n");
        md.append(row("开池", "top1 净收益", oTop1)).append("\n");
        md.append(row("开池", "regret 净变化", oRegret)).append("\n");
        md.append(row("开池", "ρ 净收益", oRho)).append("\n");

        boolean openBetter = oTop1.mean() > cTop1.mean()
                && oRegret.mean() <= cRegret.mean()
                && oRho.mean() >= cRho.mean();
        md.append("\n## 结论\n\n");
        if (openBetter && oTop1.positive() >= 17 && oTop1.signP() < 0.05) {
            md.append("**开池后学习净收益稳定为正且统计显著**（top1 ").append(oTop1.positive())
                    .append("/24，p=").append(String.format("%.4f", oTop1.signP()))
                    .append("）。原 NO-GO 的根因是封闭池耗竭 + 种子不足，尺子修正后学习有效。")
                    .append("建议据此重签 Go/No-Go 报告。\n");
        } else if (openBetter) {
            md.append("开池后学习净收益方向变好（top1 均值 ").append(String.format("%+.4f", oTop1.mean()))
                    .append(" vs 封闭池 ").append(String.format("%+.4f", cTop1.mean()))
                    .append("），但统计显著性未达标（top1 ").append(oTop1.positive())
                    .append("/24，p=").append(String.format("%.4f", oTop1.signP()))
                    .append("）。方向稳健、功效仍不足，建议继续扩大种子或提高反馈密度。\n");
        } else {
            md.append("开池未稳定改善学习净收益——说明问题不止是尺子，").append("需转攻学习目标（钥匙 B：pairwise learning-to-rank）。\n");
        }
        md.append("\n> 本实验只改离线仿真配置，不改生产代码、不接真实用户；NO-GO 判定在报告重签前不变。\n");

        // 先打印再写盘：万一写盘路径出错，26 分钟算出的结论也不会丢
        System.out.println("===== 实验结果 =====");
        System.out.println(md);
        // surefire 工作目录是 api/aiditto-app 或 api/，向上找到含 docs/eval 的项目根
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path abs = cwd.resolve("docs/eval/开池修尺子实验.md");
        if (!Files.isDirectory(cwd.resolve("docs/eval"))) {
            abs = cwd.getParent().resolve("docs/eval/开池修尺子实验.md");
        }
        if (!Files.isDirectory(abs.getParent())) {
            abs = cwd.getParent().getParent().resolve("docs/eval/开池修尺子实验.md");
        }
        Files.createDirectories(abs.getParent());
        Files.writeString(abs, md.toString(), StandardCharsets.UTF_8);
        System.out.println("报告写入: " + abs);

        // 闸门：实验本身至少要把两条序列算出来、报告写出来；结论好坏不让测试炸（NO-GO 归人判读）
        assertThat(open).hasSize(SEEDS);
        assertThat(Files.exists(abs)).isTrue();
    }

    private static String row(String mode, String metric, Stat s) {
        String verdict;
        if (metric.contains("top1")) verdict = s.mean() > 0 && s.signP() < 0.05 ? "显著为正" : (s.mean() > 0 ? "方向正但不显著" : "非正");
        else if (metric.contains("regret")) verdict = s.mean() < 0 && s.signP() < 0.05 ? "显著更优" : (s.mean() < 0 ? "方向优但不显著" : "非优");
        else verdict = s.mean() > 0 ? "方向正" : "非正";
        return String.format("| %s | %s | %+.4f | [%+.4f, %+.4f] | %d/24 | %.4f | %s |",
                mode, metric, s.mean(), s.ciLo(), s.ciHi(), s.positive(), s.signP(), verdict);
    }
}
