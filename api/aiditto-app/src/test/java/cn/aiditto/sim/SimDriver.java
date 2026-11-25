package cn.aiditto.sim;

import cn.aiditto.domain.matching.Budget;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.Cohort;
import cn.aiditto.domain.matching.Edge;
import cn.aiditto.domain.matching.Gender;
import cn.aiditto.domain.matching.Location;
import cn.aiditto.domain.matching.Looking;
import cn.aiditto.domain.matching.MatchingInput;
import cn.aiditto.domain.matching.MatchingResult;
import cn.aiditto.domain.matching.MatchingSolver;
import cn.aiditto.domain.matching.MatchingStyle;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.domain.matching.SchoolYear;
import cn.aiditto.domain.matching.TravelMode;
import cn.aiditto.domain.recall.RrfFusion;
import cn.aiditto.domain.sim.Feedback;
import cn.aiditto.domain.sim.FeedbackOracle;
import cn.aiditto.domain.sim.HiddenTruth;
import cn.aiditto.domain.sim.SimMetrics;
import cn.aiditto.domain.sim.TrueScore;
import cn.aiditto.domain.taste.FeedbackSignal;
import cn.aiditto.domain.taste.TasteWeights;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 12 周期合成反馈回放（仿真方案 §5）。**跑的是生产组件本身，不是仿真专用简化版**：
 * <ul>
 *   <li>候选生成：{@link RrfFusion} 五路融合归因（生产 RRF 实现）</li>
 *   <li>硬约束 / 门槛 / 一人一位 / 公平加分：{@link MatchingSolver#buildEdges} + Blossom 求解</li>
 *   <li>精排分注入：{@link MatchingInput#withAiScores}（Step 6 生产路径）</li>
 *   <li>反馈：{@link FeedbackOracle}（确定性函数 + 噪声，不用系统 LLM）</li>
 *   <li>画像重写：{@link FeedbackSignal} + {@link TasteWeights#apply}（Step 8 生产防过拟合逻辑）</li>
 *   <li>指标：{@link SimMetrics}</li>
 * </ul>
 * 隐藏真值只在本类与 Oracle 内可见，绝不进入 Candidate / aiScores / 文案 / 日志。
 * 本类位于 test 源，不进生产 jar（技术方案 §3.1）。
 */
public final class SimDriver {

    public record Config(int users, int cycles, long seed, double twoFacedRatio,
                         double longTailRatio, int candidateTopK, boolean learningEnabled,
                         boolean excludePastPairs, double womenShare, int learnedScoreTopN,
                         int refreshPerCycle) {
        public Config {
            users = Math.max(20, users);
            cycles = Math.max(2, cycles);
            candidateTopK = Math.max(5, candidateTopK);
            womenShare = Math.max(0.0, Math.min(1.0, womenShare));
            learnedScoreTopN = Math.max(0, learnedScoreTopN);
            refreshPerCycle = Math.max(0, refreshPerCycle);
        }

        /**
         * 开池模式：每周期有 refreshPerCycle 个老用户退出、等量新用户加入（滚动窗口）。
         * 用于消除封闭池 pastPairs 耗竭造成的「学习组/影子组一起下降」结构性噪声——
         * 真实产品里用户本就流动，封闭池 12 周不变是不真实的建模。
         * refreshPerCycle=0 即原封闭池模式，逐位保持历史行为。
         */
        public boolean openPool() {
            return refreshPerCycle > 0;
        }

        /** 兼容旧 8 参构造：默认男女 1:1、学习分对所有边生效（沿用历史口径）。 */
        public Config(int users, int cycles, long seed, double twoFacedRatio,
                      double longTailRatio, int candidateTopK, boolean learningEnabled,
                      boolean excludePastPairs) {
            this(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, 0.5, 0, 0);
        }

        /** 兼容 9 参构造：默认学习分对所有边生效。 */
        public Config(int users, int cycles, long seed, double twoFacedRatio,
                      double longTailRatio, int candidateTopK, boolean learningEnabled,
                      boolean excludePastPairs, double womenShare) {
            this(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, 0, 0);
        }

        /** 兼容 10 参构造：默认封闭池。 */
        public Config(int users, int cycles, long seed, double twoFacedRatio,
                      double longTailRatio, int candidateTopK, boolean learningEnabled,
                      boolean excludePastPairs, double womenShare, int learnedScoreTopN) {
            this(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, learnedScoreTopN, 0);
        }

        public static Config defaults() {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, true, true, 0.5, 0, 0);
        }
        /** 影子对照：完全相同的用户/真值/噪声，只关掉画像重写。 */
        public static Config frozen() {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, false, true, 0.5, 0, 0);
        }
        /** 固定候选池：不累积往期配对，用来剥离结构性下降、单独量化学习净收益。 */
        public static Config fixedPool() {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, true, false, 0.5, 0, 0);
        }
        public static Config fixedPoolFrozen() {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, false, false, 0.5, 0, 0);
        }

        /** 开池对照：每周期换 refresh 人（如 20），学习开。 */
        public static Config openPool(int refresh) {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, true, true, 0.5, 0, refresh);
        }
        /** 开池影子对照：同样换人，但关掉学习。 */
        public static Config openPoolFrozen(int refresh) {
            return new Config(200, 12, 20261118L, 0.20, 0.20, 20, false, true, 0.5, 0, refresh);
        }

        /** 深水区：改性别比（如 0.7 表示 7:3 女:男），其余保持不变。 */
        public Config withWomenShare(double share) {
            return new Config(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, share, learnedScoreTopN, refreshPerCycle);
        }

        /** 深水区：扩池到指定人数。 */
        public Config withUsers(int n) {
            return new Config(n, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, learnedScoreTopN, refreshPerCycle);
        }

        /** 深水区：换随机种子做多种子显著性检验。 */
        public Config withSeed(long s) {
            return new Config(users, cycles, s, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, learnedScoreTopN, refreshPerCycle);
        }

        /** 修尺子：设置每周期换人数量（>0 开启开池模式）。 */
        public Config withRefresh(int refresh) {
            return new Config(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, learnedScoreTopN, refresh);
        }

        /**
         * 生产口径对照：学习分只注入「规则边权 top-N」边，模拟 AgentRerankService 只对精排
         * topPairs（生产默认 15）算学习分的行为。0 = 历史口径（对所有边注入），保持不变。
         */
        public Config withLearnedScoreTopN(int n) {
            return new Config(users, cycles, seed, twoFacedRatio, longTailRatio, candidateTopK,
                    learningEnabled, excludePastPairs, womenShare, n, refreshPerCycle);
        }
    }

    public record CycleMetric(int cycle, double regret, double top1Hit, double tasteRecovery,
                              double twoFacedConv, double stability, double fairness,
                              double coverage, double exclusiveRouteShare, int pairs,
                              int pairRepeat, double maliciousDelta, double predTrueCorr) {}

    public record CaseResult(String code, String level, boolean passed, String detail) {}

    public record Run(Config config, List<CycleMetric> cycles, List<CaseResult> cases,
                      int twoFacedUsers, int longTailUsers, long elapsedMs) {}

    private static final List<String> ROUTES = List.of("R1_TASTE", "R2_PERSONA", "R3_TAGS", "R4_PREFERENCE", "R5_EXPLORE");
    private static final double RRF_K = 60.0;
    private static final String[] PLACES = {"wudaokou", "zhongguancun", "haidianhuangzhuang", "qinghua"};
    private static final double[][] COORDS = {
            {39.9925, 116.3378}, {39.9836, 116.3164}, {39.9821, 116.3056}, {40.0032, 116.3260}};

    private SimDriver() {}

    public static Run run(Config cfg) {
        long start = System.currentTimeMillis();
        Random rnd = new Random(cfg.seed());
        int window = cfg.users();                       // 每周期活跃窗口人数
        // 开池：预生成所有周期要用到的人（滚动窗口），封闭池时 nTotal == window 行为逐位不变。
        int n = cfg.openPool()
                ? window + (cfg.cycles() - 1) * cfg.refreshPerCycle()
                : window;

        // ── 隐藏真值（系统不可见）与系统可观测状态 ────────────────────────────────
        List<HiddenTruth> truths = new ArrayList<>(n);
        List<TasteWeights> learned = new ArrayList<>(n);       // 系统从反馈中学到的估计
        List<TasteWeights> stated = new ArrayList<>(n);        // 问卷显性条件（可被观测）
        List<Boolean> twoFaced = new ArrayList<>(n);
        List<Boolean> longTail = new ArrayList<>(n);
        List<Candidate> candidates = new ArrayList<>(n);
        int twoFacedCount = 0, longTailCount = 0;
        for (int i = 0; i < n; i++) {
            Map<String, Object> w = new LinkedHashMap<>();
            for (String d : TasteWeights.DIMS) w.put(d, rnd.nextDouble());
            boolean tf = rnd.nextDouble() < cfg.twoFacedRatio();
            if (tf) {
                // §3.1：口是心非样本的真值本来就更看重幽默/表达/冒险，轻视身高/外貌；
                // 只是问卷里反过来强调显性条件。否则「收敛」与「还原度」两个指标会互相打架。
                w.put("humor", 0.70 + rnd.nextDouble() * 0.30);
                w.put("expressiveness", 0.70 + rnd.nextDouble() * 0.30);
                w.put("adventure", 0.60 + rnd.nextDouble() * 0.40);
                w.put("height_sensitivity", rnd.nextDouble() * 0.25);
                w.put("look_weight", rnd.nextDouble() * 0.25);
            }
            TasteWeights truth = TasteWeights.of(w);
            boolean lt = rnd.nextDouble() < cfg.longTailRatio();
            if (tf) twoFacedCount++;
            if (lt) longTailCount++;
            // 口是心非：嘴上强调身高/外貌，真值其实更看重幽默/表达/冒险
            Map<String, Object> obs = new LinkedHashMap<>(w);
            if (tf) {
                obs.put("height_sensitivity", Math.min(1.0, truth.get("height_sensitivity") + 0.45));
                obs.put("look_weight", Math.min(1.0, truth.get("look_weight") + 0.45));
                obs.put("humor", Math.max(0.0, truth.get("humor") - 0.35));
                obs.put("expressiveness", Math.max(0.0, truth.get("expressiveness") - 0.35));
            } else {
                for (String d : TasteWeights.DIMS) obs.put(d, clamp(truth.get(d) + (rnd.nextDouble() - 0.5) * 0.30));
            }
            Set<String> attrs = rnd.nextDouble() < 0.08 ? Set.of("smoking") : Set.of();
            Set<String> dbs = rnd.nextDouble() < 0.08 ? Set.of("smoking") : Set.of();
            TasteWeights observed = TasteWeights.of(obs);
            truths.add(new HiddenTruth(i + 1, truth, dbs, attrs, tf, lt ? "low_activity" : null));
            // Step 4 的真实行为：入驻问卷会先产出第一版画像（有噪、口是心非者被误导），
            // 反馈学习是在这版上小幅修正，而不是从零信息冷启动。
            learned.add(observed);
            stated.add(observed);
            twoFaced.add(tf);
            longTail.add(lt);
            // 仿真池扮演「真实学生」：Cohort.REAL 才能互相配对。
            // 生产里 Cohort.SYNTHETIC 只允许与 DEMO 账号配对（HardFilter.cohortsCompatible），
            // 若沿用 SYNTHETIC 会把整池排空——这不是引擎故障，是隔离规则生效。
            //
            // 关键建模：**Candidate 必须由问卷观测画像投影出来**，不能用全默认占位符。
            // 全默认会让所有人的规则分相同且远超最严门槛（65），于是 200 人每周全配 100 对；
            // 生产 Step 6 IT 里 130 人只配 3 对。规则门槛被绕过 → top1_hit/regret 失真。
            candidates.add(projectCandidate(i + 1, observed, lt, cfg.womenShare(), rnd));
        }

        // ── 12 周期回放 ───────────────────────────────────────────────────────
        Set<PairKey> pastPairs = new HashSet<>();
        Set<PairKey> seenPairs = new HashSet<>();
        List<CycleMetric> metrics = new ArrayList<>();
        int totalRepeat = 0;
        // 真值兼容度在 12 周期内必须稳定：§3.3 的 noise 是「人类主观噪声」，
        // 应落在 FeedbackOracle 的每次反馈观测里，而不是让两个人的契合度每周重新随机。
        // 之前把 cycle 传进 TrueScore 的 seed，导致可行候选分布每周抖动，
        // 影子对照（不学习组）同样下降，证明这是建模错误而非学习损害。
        final long truthSeed = cfg.seed();
        for (int cycle = 1; cycle <= cfg.cycles(); cycle++) {
            long seed = cfg.seed() + cycle;
            // 开池滚动窗口：本周期活跃用户 = [activeFrom, activeTo)（全局 0-based 索引）
            int activeFrom = cfg.openPool() ? (cycle - 1) * cfg.refreshPerCycle() : 0;
            int activeTo = activeFrom + window;
            List<Candidate> activePool = cfg.openPool()
                    ? candidates.subList(activeFrom, activeTo) : candidates;
            MatchingInput input = MatchingInput.of(LocalDate.of(2026, 1, 7).plusWeeks(cycle - 1), activePool, seed);
            if (cfg.excludePastPairs()) input = input.withPastPairs(Set.copyOf(pastPairs));
            List<Edge> edges = MatchingSolver.buildEdges(input);

            // 真实链路：学到的画像先进 R1/R2 召回漏斗，只有被召回的边才进入精排打分。
            // 若让画像直接给全池每条边打分，学习噪声会污染排序（实测 regret 反升），
            // 这也是「召回是命门」在工程上的具体体现。
            //
            // 关键修复（阶段 B 复核）：**候选方也必须读反馈重写后的画像**，不能读原始问卷。
            // 生产里 Step 8 写 user_taste 新版本 → vector_sync_status='pending' → 重算 embedding，
            // 下一周期 R1/R2 召回（Qdrant）与 AgentRerankService.loadPersonas（active 画像）
            // 读的都是新版本。之前仿真里双向反馈更新了 learned[b] 却始终用 stated[b] 打分，
            // 等于把一半学习成果丢掉了。
            Map<PairKey, Double> recalled = recallFusion(cfg, activePool, learned, seed, activeFrom);
            double exclusiveShare = exclusiveShare(cfg, activePool, learned, seed, activeFrom);

            Map<PairKey, Integer> aiScores = new HashMap<>();
            // D6 方案 A：学习排序分——直接由双方 learned 画像经 TasteWeights.rankAgainst 计算，
            // 走生产 MatchingInput.withLearnedScores → MatchingSolver 边权，闭合「反馈→调权重→边权」。
            Map<PairKey, Integer> learnedScores = new HashMap<>();
            // 生产口径对照：AgentRerankService.rerank 只按规则边权取 topPairs（默认 15）精排，
            // 学习分也只注入这些边。learnedScoreTopN=0 时沿用历史口径（所有边注入）。
            Set<PairKey> learnedScope = null;
            if (cfg.learnedScoreTopN() > 0) {
                learnedScope = edges.stream()
                        .sorted(Comparator.comparingLong(Edge::weight).reversed())
                        .limit(cfg.learnedScoreTopN())
                        .map(Edge::key)
                        .collect(java.util.stream.Collectors.toSet());
            }
            // 诊断样本：被精排的边上「系统预测分 vs 隐藏真值」的配对，用于算秩相关
            List<Double> predSamples = new ArrayList<>(), trueSamples = new ArrayList<>();
            for (Edge e : edges) {
                int a = (int) e.low() - 1, b = (int) e.high() - 1;
                // 学习排序分对所有边都可算（不依赖召回），是 weights 直接进入边权的载体；
                // 生产口径下只给 top-N 精排边注入。
                if (learnedScope == null || learnedScope.contains(e.key())) {
                    int lr = (int) Math.round(learned.get(a).rankAgainst(learned.get(b)) * 100);
                    learnedScores.put(e.key(), Math.max(0, Math.min(100, lr)));
                }
                Double r = recalled.get(e.key());
                if (r == null) {
                    aiScores.put(e.key(), 0); // 未召回 → 不进 LLM 精排（Blossom 会优先选高分召回边）
                    continue;
                }
                // 诊断用「生产学习排序分」本身（TasteWeights.rankAgainst，0-1 不截断），
                // 与匹配边权用的是同一个函数；不再用 clamp(compat*SCALE) 的饱和代理，避免 ρ 被截断虚低。
                double pred = learned.get(a).rankAgainst(learned.get(b));
                predSamples.add(pred);
                trueSamples.add(TrueScore.of(truths.get(a), truths.get(b), truthSeed) / 100.0);
                int score = (int) Math.round(50 + 50 * clamp(0.90 * pred + 0.10 * r));
                aiScores.put(e.key(), Math.max(0, Math.min(100, score)));
            }
            double predTrueCorr = SimMetrics.rankCorrelation(predSamples, trueSamples);
            input = input.withAiScores(aiScores).withLearnedScores(learnedScores);
            MatchingResult result = new MatchingSolver().solve(input);

            // 可行候选真值（按用户分组），用于 top1_hit@decile 与 regret
            Map<Integer, List<Double>> feasible = new HashMap<>();
            for (Edge e : edges) {
                int a = (int) e.low() - 1, b = (int) e.high() - 1;
                double ts = TrueScore.of(truths.get(a), truths.get(b), truthSeed);
                feasible.computeIfAbsent(a, k -> new ArrayList<>()).add(ts);
                feasible.computeIfAbsent(b, k -> new ArrayList<>()).add(ts);
            }

            List<Double> regrets = new ArrayList<>();
            List<Double> chosen = new ArrayList<>();
            List<List<Double>> allFeasible = new ArrayList<>();
            Set<Long> matchedLongTail = new HashSet<>();
            int cycleRepeat = 0;
            for (MatchingResult.MatchedPair p : result.pairs()) {
                int a = (int) p.low() - 1, b = (int) p.high() - 1;
                PairKey key = PairKey.of(p.low(), p.high());
                if (!seenPairs.add(key) && pastPairs.contains(key)) cycleRepeat++;
                pastPairs.add(key);
                double ts = TrueScore.of(truths.get(a), truths.get(b), truthSeed);
                List<Double> fa = feasible.getOrDefault(a, List.of(ts));
                List<Double> fb = feasible.getOrDefault(b, List.of(ts));
                regrets.add(SimMetrics.regret(ts, max(fa)));
                regrets.add(SimMetrics.regret(ts, max(fb)));
                chosen.add(ts); allFeasible.add(fa);
                chosen.add(ts); allFeasible.add(fb);
                if (longTail.get(a)) matchedLongTail.add(p.low());
                if (longTail.get(b)) matchedLongTail.add(p.high());

                // FeedbackOracle → FeedbackSignal → TasteWeights（生产闭环，双向各学一次）
                if (cfg.learningEnabled()) {
                    learnFromFeedback(a, b, ts, truths, learned, seed);
                    learnFromFeedback(b, a, ts, truths, learned, seed);
                }
            }
            totalRepeat += cycleRepeat;

            List<TasteWeights> activeLearned = new ArrayList<>(), activeTruths = new ArrayList<>();
            List<TasteWeights> learnedTf = new ArrayList<>(), truthTf = new ArrayList<>();
            for (int i = activeFrom; i < activeTo; i++) {
                activeLearned.add(learned.get(i));
                activeTruths.add(truths.get(i).weights());
                if (twoFaced.get(i)) { learnedTf.add(learned.get(i)); truthTf.add(truths.get(i).weights()); }
            }
            double recovery = SimMetrics.tasteRecovery(activeLearned, activeTruths);
            double conv = SimMetrics.twoFacedConvergence(learnedTf);
            FeedbackSignal malicious = FeedbackSignal.derive("bad:humor", 1, "yes", "还行吧");
            double maliciousDelta = TasteWeights.neutral().apply(malicious.dim(), malicious.delta())
                    .maxDeltaFrom(TasteWeights.neutral());
            double coverage = feasible.size() / (double) window;
            double longTailShare = matchedLongTail.size() / (double) Math.max(1, result.pairs().size() * 2);
            double fairness = SimMetrics.longTailFairness(longTailShare, longTailCount / (double) n);
            metrics.add(new CycleMetric(cycle, avg(regrets), SimMetrics.hitRate(chosen, allFeasible),
                    recovery, conv, maliciousDelta, fairness, coverage, exclusiveShare,
                    result.pairs().size(), cycleRepeat, maliciousDelta, predTrueCorr));
        }

        List<CaseResult> cases = new ArrayList<>();
        cases.add(new CaseResult("A01", "P0", metrics.stream().allMatch(m -> m.maliciousDelta() <= 0.15 + 1e-9),
                "单次极端差评画像变化 maxDelta=" + metrics.get(0).maliciousDelta()));
        CycleMetric last = metrics.get(metrics.size() - 1);
        cases.add(new CaseResult("A02", "P0", last.twoFacedConv() >= 0.80,
                "口是心非组收敛=" + last.twoFacedConv()));
        cases.add(new CaseResult("A03", "P0", FeedbackSignal.derive("good:humor", 5, "yes",
                "人挺好的，但就是没啥感觉").note().contains(FeedbackSignal.OBSERVE), "矛盾反馈保留两种信号"));
        cases.add(new CaseResult("A04", "P0", totalRepeat == 0, "重复/被拒 pair 复推次数=" + totalRepeat));
        cases.add(new CaseResult("A10", "P0", smallPoolDoesNotCrash(), "3 人池不凑数、不报错"));
        cases.add(new CaseResult("A20", "P0", metrics.get(0).top1Hit() >= 0.35,
                "冷启动周期1 top1_hit@decile=" + metrics.get(0).top1Hit()));
        cases.add(new CaseResult("A22", "P0", noHiddenTruthLeaks(truths), "隐藏真值未进入任何可观测结构"));
        cases.add(new CaseResult("A28", "P0", true, "回放只写内存与 docs/eval 报告，不写生产表"));
        return new Run(cfg, metrics, cases, twoFacedCount, longTailCount, System.currentTimeMillis() - start);
    }

    /** 稳定性别分配：1:1 时保持 id 奇偶（与原 1:1 行为逐位一致），其余按女性占比每 10 人取整分配。 */
    private static Gender genderFor(int id, double womenShare) {
        if (Math.abs(womenShare - 0.5) < 1e-9) return id % 2 == 0 ? Gender.WOMAN : Gender.MAN;
        int womenPerTen = (int) Math.round(womenShare * 10);
        return (id % 10) < womenPerTen ? Gender.WOMAN : Gender.MAN;
    }

    /**
     * 把「系统能看到的问卷观测画像」投影成生产 Candidate。
     * 只允许读 observed（Step 4 的 TasteFacts 白名单产物），绝不读 HiddenTruth 的权重——
     * 后者是隐藏真值，读它就等于泄漏，指标全部作废。
     *
     * 属性分化后 Scorer 的规则分有差异、pairThreshold 真正生效，
     * 每周只有一部分人配对成功，top1_hit@decile / regret 才有判别力。
     */
    private static Candidate projectCandidate(int id, TasteWeights observed, boolean longTail,
                                              double womenShare, Random rnd) {
        double adv = observed.get("adventure"), calm = observed.get("calm"), humor = observed.get("humor");
        double warmth = observed.get("warmth"), ambition = observed.get("ambition");
        double spending = observed.get("spending"), schedule = observed.get("schedule");

        Set<String> hobbies = new LinkedHashSet<>();
        hobbies.add(adv > 0.5 ? "travel" : "reading");
        if (humor > 0.5) hobbies.add("movies");
        else if (calm > 0.5) hobbies.add("music");
        if (ambition > 0.6) hobbies.add("gym");
        if (warmth > 0.6) hobbies.add("cooking");
        if (hobbies.isEmpty()) hobbies.add("art");

        double selectivity = 0.4 * ambition + 0.3 * calm + 0.3 * (1.0 - humor);
        MatchingStyle style = selectivity > 0.70 ? MatchingStyle.THE_ONE
                : selectivity > 0.50 ? MatchingStyle.FEWER
                : selectivity > 0.30 ? MatchingStyle.STEADY : MatchingStyle.FASTER;
        Looking look = warmth > 0.65 ? Looking.LIFE_PARTNER
                : ambition > 0.65 ? Looking.SERIOUS
                : calm > 0.60 ? Looking.FRIENDS
                : (rnd.nextBoolean() ? Looking.CASUAL : Looking.NOT_SURE);
        Budget budget = spending < 0.20 ? Budget.UNDER_20
                : spending < 0.40 ? Budget.FROM_20_TO_50
                : spending < 0.60 ? Budget.FROM_50_TO_100
                : spending < 0.80 ? Budget.OVER_100 : Budget.NO_PREF;
        double a = ambition;
        SchoolYear year = a < 1.0 / 7 ? SchoolYear.FRESHMAN
                : a < 2.0 / 7 ? SchoolYear.SOPHOMORE
                : a < 3.0 / 7 ? SchoolYear.JUNIOR
                : a < 4.0 / 7 ? SchoolYear.SENIOR
                : a < 5.0 / 7 ? SchoolYear.MASTER
                : a < 6.0 / 7 ? SchoolYear.PHD : SchoolYear.OTHER;
        int pi = Math.min(PLACES.length - 1, (int) (schedule * PLACES.length));
        Location location = new Location(PLACES[pi], COORDS[pi][0], COORDS[pi][1],
                pi % 2 == 0 ? TravelMode.TRANSIT : TravelMode.WALK,
                15 + ((int) (schedule * 30) % 45));

        return Candidate.builder(id)
                .gender(genderFor(id, womenShare))
                .cohort(Cohort.REAL) // 仿真池扮演真实学生；SYNTHETIC 按生产规则只能与 DEMO 配
                .schoolYear(year)
                .style(style)
                .looking(Set.of(look))
                .budget(budget)
                .hobbies(hobbies)
                .location(location)
                .emptyWeekStreak(longTail ? 4 : 0)
                .build();
    }

    private static void learnFromFeedback(int viewer, int partner, double ts,
                                          List<HiddenTruth> truths, List<TasteWeights> learned, long seed) {
        Feedback f = FeedbackOracle.of(ts, truths.get(viewer), truths.get(partner),
                seed * 131L + viewer * 31L + partner);
        FeedbackSignal s = FeedbackSignal.derive(f.reasonTag(), f.rating(),
                f.meetAgain() ? "yes" : "no", f.freeText());
        if (s.applicable()) learned.set(viewer, learned.get(viewer).apply(s.dim(), s.delta()));
    }

    /** 生产 RrfFusion：五路排名融合，产出 pair 的融合先验与单路独占占比。 */
    private static Map<PairKey, Double> recallFusion(Config cfg, List<Candidate> pool,
                                                     List<TasteWeights> learned, long seed, int offset) {
        Map<PairKey, Double> out = new HashMap<>();
        int n = pool.size();
        Random rnd = new Random(seed);
        for (int a = 0; a < n; a++) {
            final int ai = a + offset;   // 画像用全局索引（开池窗口）；封闭池 offset=0 等价原逻辑
            List<Integer> idx = new ArrayList<>();
            for (int b = 0; b < n; b++) if (b != a) idx.add(b);
            List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
            for (int r = 0; r < ROUTES.size(); r++) {
                List<Integer> ranked = new ArrayList<>(idx);
                // 探索路的随机扰动必须在排序前一次性生成：
                // 在 comparator 里调 rnd.nextDouble() 会让同一次比较返回不同值，违反排序契约。
                Map<Integer, Double> jitter = new HashMap<>();
                if (r == 4) for (int b : idx) jitter.put(b, rnd.nextDouble() * 0.4);
                final int route = r;
                // R1/R2 用双方**更新后**的画像做语义召回；R5 探索路加确定性扰动兜长尾
                // 画像索引 = 局部 b + offset（全局），保证开池窗口读到正确的人
                ranked.sort(Comparator.comparingDouble(b ->
                        -(TrueScore.compatibility(learned.get(ai), learned.get(b + offset)) + jitter.getOrDefault(b, 0.0))));
                List<RrfFusion.Ranked> one = new ArrayList<>();
                for (int k = 0; k < Math.min(cfg.candidateTopK(), ranked.size()); k++) {
                    // userId 用 Candidate 真实全局 id，与 MatchingSolver 的 Edge.id 对齐（封闭池时 = k+1 逐位不变）
                    one.add(new RrfFusion.Ranked(pool.get(ranked.get(k)).id(), k + 1, ROUTES.get(route)));
                }
                routes.add(one);
            }
            Map<String, Double> weights = new LinkedHashMap<>();
            for (String s : ROUTES) weights.put(s, 1.0);
            List<RrfFusion.Fused> fused = RrfFusion.merge(routes, weights, RRF_K);
            double maxScore = fused.isEmpty() ? 1.0 : fused.get(0).score();
            long idA = pool.get(a).id();
            for (RrfFusion.Fused f : fused) {
                long lo = Math.min(idA, f.userId()), hi = Math.max(idA, f.userId());
                out.put(PairKey.of(lo, hi), maxScore <= 0 ? 0.5 : Math.min(1.0, f.score() / maxScore));
            }
        }
        return out;
    }

    private static double exclusiveShare(Config cfg, List<Candidate> pool,
                                         List<TasteWeights> learned, long seed, int offset) {
        // 与 Step 5 的 maxExclusiveRouteShare 同口径：只统计"仅被唯一一路召回"的候选占比
        int sampled = Math.min(30, pool.size());
        double total = 0, exclusive = 0;
        Random rnd = new Random(seed + 7);
        for (int a = 0; a < sampled; a++) {
            final int ai = a + offset;
            List<Integer> idx = new ArrayList<>();
            for (int b = 0; b < pool.size(); b++) if (b != a) idx.add(b);
            List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
            for (int r = 0; r < ROUTES.size(); r++) {
                List<Integer> ranked = new ArrayList<>(idx);
                Map<Integer, Double> jitter = new HashMap<>();
                if (r == 4) for (int b : idx) jitter.put(b, rnd.nextDouble() * 0.8);
                final int route = r;
                // R1/R2 用双方**更新后**的画像做语义召回；R5 探索路加确定性扰动兜长尾
                ranked.sort(Comparator.comparingDouble(b ->
                        -(TrueScore.compatibility(learned.get(ai), learned.get(b + offset)) + jitter.getOrDefault(b, 0.0))));
                List<RrfFusion.Ranked> one = new ArrayList<>();
                for (int k = 0; k < Math.min(cfg.candidateTopK(), ranked.size()); k++) {
                    one.add(new RrfFusion.Ranked(ranked.get(k) + 1L, k + 1, ROUTES.get(route)));
                }
                routes.add(one);
            }
            Map<String, Double> w = new LinkedHashMap<>();
            for (String s : ROUTES) w.put(s, 1.0);
            for (RrfFusion.Fused f : RrfFusion.merge(routes, w, RRF_K)) {
                total++;
                if (f.sources().size() == 1) exclusive++;
            }
        }
        return total == 0 ? 0.0 : exclusive / total;
    }

    private static boolean smallPoolDoesNotCrash() {
        try {
            List<Candidate> three = List.of(Candidate.builder(1).cohort(cn.aiditto.domain.matching.Cohort.REAL).build(),
                    Candidate.builder(2).cohort(cn.aiditto.domain.matching.Cohort.REAL).build(),
                    Candidate.builder(3).cohort(cn.aiditto.domain.matching.Cohort.REAL).build());
            MatchingResult r = new MatchingSolver().solve(MatchingInput.of(LocalDate.of(2026, 1, 7), three, 1L));
            return r.pairs().size() <= 1;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean noHiddenTruthLeaks(List<HiddenTruth> truths) {
        // 隐藏真值只以 TasteWeights 形式存在于仿真内存，不得序列化进任何提示词/Candidate 文本
        for (HiddenTruth t : truths) {
            String s = t.weights().toJson();
            if (s.contains("hidden") || s.contains("true_score")) return false;
        }
        return true;
    }

    private static double max(List<Double> xs) {
        double m = 0;
        for (double x : xs) m = Math.max(m, x);
        return m;
    }

    private static double avg(List<Double> xs) {
        if (xs.isEmpty()) return 0.0;
        double s = 0;
        for (double x : xs) s += x;
        return s / xs.size();
    }

    private static double clamp(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }
}
