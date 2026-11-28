package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.feedback.v4.FeedbackLedger;
import cn.aiditto.domain.feedback.v4.FeedbackProjection;
import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.RelationshipFeedbackEvent;
import cn.aiditto.domain.feedback.v4.strategy.MatchScoreSnapshot;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation;
import cn.aiditto.domain.feedback.v4.strategy.ObservationBuilder;
import cn.aiditto.domain.feedback.v4.strategy.ObservationWindow;
import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels;
import cn.aiditto.domain.feedback.v4.strategy.ScoreCalibration;
import cn.aiditto.domain.feedback.v4.strategy.StrategyComparison;
import cn.aiditto.domain.feedback.v4.strategy.StrategyDataset;
import cn.aiditto.domain.feedback.v4.strategy.StrategyLearningProposal;
import cn.aiditto.domain.matchlab.LabWorkbench.LabRejection;
import cn.aiditto.domain.matchlab.LabWorkbench.LabRun;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only evaluation over frozen F03 rows and the current sandbox ledgers.
 * It does not activate a policy or write a production table.
 */
public final class SandboxEvaluation {
    private SandboxEvaluation() {}

    public static Report strategy(SandboxLoop loop, String source) {
        String selected = source == null || source.isBlank() ? "FROZEN_EXPERIMENT" : source;
        List<MatchingObservation> rows = "SYNTHETIC_SANDBOX".equals(selected) ? sandboxRows(loop) : StrategyDataset.observations();
        if (!"SYNTHETIC_SANDBOX".equals(selected) && !"FROZEN_EXPERIMENT".equals(selected)) {
            throw new LabRejection("DATASET");
        }
        boolean sandbox = "SYNTHETIC_SANDBOX".equals(selected);
        String dataset = sandbox ? "sandbox-ledger-1" : StrategyDataset.VERSION;
        List<Count> counts = sandbox ? sandboxCounts(loop) : frozenCounts(rows);
        List<CalibrationRow> calibration = sandbox ? insufficient(rows.size()) : frozenCalibration();
        StrategyLearningProposal proposal = proposal(StrategyDataset.VERSION, learnable(rows), true);
        String limits = sandbox
                ? "观察数据集是 sandbox-ledger-1。策略对照表来自冻结实验 " + StrategyDataset.VERSION + "，不随沙箱切换。"
                : "观察和策略对照都来自冻结实验 " + StrategyDataset.VERSION + "。";
        return new Report(selected, rows.size(), dataset, "f03-labels-2",
                "历史标签文件保持原样。本页计数使用 F03-FINAL 的 RelationshipLabels，不另造一份旧标签。",
                counts, calibration, StrategyComparison.compare(StrategyDataset.observations()), proposal,
                limits + "合成频率不是真实用户接受概率。未发布候选的接受、见面和继续意愿不可知。真实策略提升是 NOT_IDENTIFIED。"
                        + "回放只使用内存中的候选图。没有独立反序列化回放，没有特征级差异，也没有校准曲线。",
                StrategyDataset.VERSION);
    }

    public static ReplayResult replay(SandboxLoop loop, String runId, String commitSha) {
        SandboxLoop.Held held = loop.plans.get(runId);
        if (held == null) throw new LabRejection("HISTORY_UNAVAILABLE");
        LabRun original = find(loop, runId);
        GlobalMatchingResult again = cn.aiditto.domain.matching.v4.V4GlobalMatchingEngine.solve(held.graph(), held.result().objective());
        String semantic = SandboxLoop.canonicalResult(again);
        String replayId = loop.lab().record("REPLAY", original.datasetVersion(), original.inputSnapshot(), original.policyVersion(),
                commitSha, "CURRENT_ENGINE_REPLAY", again.status(), semantic,
                "用当前引擎重新执行已保存的输入。这不是检出历史 Git 版本后的复现。", runId);
        boolean same = SandboxLoop.digest(semantic).equals(original.resultHash());
        return new ReplayResult(runId, replayId, "CURRENT_ENGINE_REPLAY", same, original.inputHash(),
                original.resultHash(), SandboxLoop.digest(semantic),
                "历史节点还在。新运行只增加一条回放记录。");
    }

    public static CompareResult compare(SandboxLoop loop, String leftId, String rightId) {
        LabRun left = find(loop, leftId);
        LabRun right = find(loop, rightId);
        boolean sameInput = left.inputHash().equals(right.inputHash());
        boolean samePolicy = left.policyVersion().equals(right.policyVersion());
        boolean sameResult = left.resultHash().equals(right.resultHash());
        String note = sameInput && samePolicy
                ? "输入和策略相同。语义摘要一致只说明这次计算相同。"
                : "输入或策略不同。两组分数差不能写成策略增益。";
        return new CompareResult(left.runId(), right.runId(), left.experimentType(), right.experimentType(),
                sameInput, samePolicy, sameResult, left.inputHash(), right.inputHash(), left.resultHash(), right.resultHash(), note);
    }

    public static SafetyReport safety(SandboxLoop loop) {
        int violations = 0;
        int plans = 0;
        for (SandboxLoop.Held held : loop.plans.values()) {
            plans++;
            violations += held.result().safetyViolations();
        }
        return new SafetyReport(plans, violations, "NO-GO", "NOT_RUN", "NOT_IDENTIFIED", "NO-GO", risks(), gates(), capabilities());
    }

    public static String export(SandboxLoop loop, String runId) {
        LabRun run = find(loop, runId);
        return "{\"schemaVersion\":\"sandbox-export-1\",\"workspaceId\":\"" + escape(run.workspaceId())
                + "\",\"runId\":\"" + escape(run.runId())
                + "\",\"experimentType\":\"" + escape(run.experimentType())
                + "\",\"inputHash\":\"" + run.inputHash()
                + "\",\"resultHash\":\"" + run.resultHash()
                + "\",\"inputSnapshot\":\"" + escape(run.inputSnapshot())
                + "\",\"result\":\"" + escape(run.result()) + "\"}";
    }

    public static void refuseActivation() {
        proposal(StrategyDataset.VERSION, 128, true).activate();
    }

    static StrategyLearningProposal proposal(String dataset, int learnable, boolean safe) {
        return StrategyLearningProposal.from(dataset, learnable, safe, Instant.parse("2026-11-28T00:00:00Z"));
    }

    private static List<MatchingObservation> sandboxRows(SandboxLoop loop) {
        List<MatchingObservation> rows = new ArrayList<>();
        int index = 0;
        for (var entry : loop.ledgers.entrySet()) {
            FeedbackLedger ledger = entry.getValue();
            if (retracted(ledger)) continue;
            try {
                rows.add(observe(entry.getKey(), ledger, loop, index++));
            } catch (FeedbackRejection ignored) {
                // A ledger that fails the observation contract is counted, not relabeled.
            }
        }
        return List.copyOf(rows);
    }

    private static MatchingObservation observe(String ledgerKey, FeedbackLedger ledger, SandboxLoop loop, int index) {
        GlobalMatchingResult.MatchedPair pair = pair(loop, ledger);
        MatchScoreSnapshot scores = new MatchScoreSnapshot(
                scorePoints(pair.scoreLowToHigh()), scorePoints(pair.scoreHighToLow()),
                coverageFraction(pair.coverageLowToHigh()), coverageFraction(pair.coverageHighToLow()),
                scorePoints(pair.fitScore()), "m02");
        Instant published = ledger.events().get(0).recordedAt();
        ObservationWindow window = new ObservationWindow(published.plus(7, ChronoUnit.DAYS),
                published.plus(14, ChronoUnit.DAYS), published.plus(21, ChronoUnit.DAYS));
        java.util.Set<String> superseded = new java.util.HashSet<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (event.supersedesEventId() != null) superseded.add(event.supersedesEventId());
        }
        List<String> sources = new ArrayList<>();
        for (RelationshipFeedbackEvent event : ledger.events()) {
            if (!superseded.contains(event.eventId())) sources.add(event.eventId());
        }
        return ObservationBuilder.fromLedger(ledger.context(), ledger, scores, window,
                "sandbox-obs-" + index, published, Instant.now(), sources);
    }

    private static GlobalMatchingResult.MatchedPair pair(SandboxLoop loop, FeedbackLedger ledger) {
        for (SandboxLoop.Held held : loop.plans.values()) {
            if (!held.graph().input().matchingRunId().equals(ledger.context().matchingRunId())) continue;
            for (GlobalMatchingResult.MatchedPair pair : held.result().pairs()) {
                if (pair.lowId() == ledger.context().userA() && pair.highId() == ledger.context().userB()) return pair;
            }
        }
        throw new LabRejection("PAIR");
    }

    private static List<Count> sandboxCounts(SandboxLoop loop) {
        int published = loop.ledgers.size();
        int viewed = 0;
        int oneResponse = 0;
        int bothResponse = 0;
        int bothAccepted = 0;
        int scheduled = 0;
        int reported = 0;
        int bothAttended = 0;
        int oneContinue = 0;
        int bothContinue = 0;
        int missing = 0;
        int contested = 0;
        int retracted = 0;
        int feedbackGranted = 0;
        int learningConsent = 0;
        int learningEligible = 0;
        for (FeedbackLedger ledger : loop.ledgers.values()) {
            FeedbackProjection projection = ledger.project();
            if (projection.viewedA() || projection.viewedB()) viewed++;
            boolean responseA = responded(projection.responseA().name());
            boolean responseB = responded(projection.responseB().name());
            if (responseA || responseB) oneResponse++;
            if (responseA && responseB) bothResponse++;
            if (projection.mutualAcceptance()) bothAccepted++;
            if ("SCHEDULED".equals(projection.schedule().name())) scheduled++;
            String attendance = projection.attendance().name();
            if ("ONE_REPORTED_ATTENDED".equals(attendance) || "BOTH_REPORTED_ATTENDED".equals(attendance)) reported++;
            if ("BOTH_REPORTED_ATTENDED".equals(attendance)) bothAttended++;
            if ("EXPRESSED".equals(projection.continueA().name()) || "EXPRESSED".equals(projection.continueB().name())) oneContinue++;
            if ("EXPRESSED".equals(projection.continueA().name()) && "EXPRESSED".equals(projection.continueB().name())) bothContinue++;
            if (!projection.unknown().isEmpty()) missing++;
            if (projection.contested()) contested++;
            if (projection.retracted()) retracted++;
            if (ledger.context().feedbackConsentA() && ledger.context().feedbackConsentB()) feedbackGranted++;
            if (ledger.context().learningConsentA() && ledger.context().learningConsentB()) learningConsent++;
            if (ledger.eligibility().matchFactEligible()) learningEligible++;
        }
        int y1Immature = 0;
        int y2Immature = 0;
        int y3Immature = 0;
        int cohort = 0;
        List<MatchingObservation> observed = sandboxRows(loop);
        for (MatchingObservation row : observed) {
            RelationshipLabels labels = RelationshipLabels.of(row);
            if (labels.y1() == RelationshipLabels.Mutual.IMMATURE) y1Immature++;
            if (labels.y2() == RelationshipLabels.AttendanceLabel.IMMATURE) y2Immature++;
            if (labels.y3() == RelationshipLabels.ContinueLabel.IMMATURE) y3Immature++;
            if (row.feedbackAuthorization() == MatchingObservation.Access.OPEN && !row.retracted()) cohort++;
        }
        String window = "匹配事实。分母是工作区账本。授权列是双方反馈处理授权，不是学习资格，也不是群体评估可读数。";
        String factAuth = feedbackGranted + "/" + published;
        int unreadable = Math.max(0, published - retracted - observed.size());
        return List.of(
                count("已发布匹配", "MATCH", published, published, window, missing, contested, y1Immature, retracted, factAuth),
                count("查看", "MATCH", viewed, published, window, missing, contested, y1Immature, retracted, factAuth),
                count("至少一方响应", "MATCH", oneResponse, published, window, missing, contested, y1Immature, retracted, factAuth),
                count("双方响应", "MATCH", bothResponse, published, window, missing, contested, y1Immature, retracted, factAuth),
                count("双方接受", "MATCH", bothAccepted, published, window, missing, contested, y1Immature, retracted, factAuth),
                count("见面安排", "MATCH", scheduled, published, window, missing, contested, y2Immature, retracted, factAuth),
                count("用户报告见面", "MATCH", reported, published, window, missing, contested, y2Immature, retracted, factAuth),
                count("双方确认见面", "MATCH", bothAttended, published, window, missing, contested, y2Immature, retracted, factAuth),
                count("单方继续意愿", "MATCH", oneContinue, published, window, missing, contested, y3Immature, retracted, factAuth),
                count("双方继续意愿", "MATCH", bothContinue, published, window, missing, contested, y3Immature, retracted, factAuth),
                count("群体评估可读", "COHORT", cohort, observed.size(),
                        "F03 标签可读的观察。分母是成功建成的观察，失败的观察记在缺失，不另造分母。",
                        unreadable, contested, y1Immature, retracted, cohort + "/" + observed.size()),
                count("学习资格", "LEARNING", learningEligible, published,
                        "matchFactEligible。学习授权覆盖是 " + learningConsent + "/" + published + "，不拿它代替反馈授权。",
                        missing, contested, y1Immature, retracted, learningConsent + "/" + published));
    }

    private static List<Count> frozenCounts(List<MatchingObservation> rows) {
        int bothAccepted = 0;
        int unknown = 0;
        int expired = 0;
        int contested = 0;
        int withheld = 0;
        int immature = 0;
        for (MatchingObservation row : rows) {
            RelationshipLabels labels = RelationshipLabels.of(row);
            if (labels.y1() == RelationshipLabels.Mutual.BOTH_ACCEPTED) bothAccepted++;
            if (labels.y1() == RelationshipLabels.Mutual.UNKNOWN) unknown++;
            if (labels.y1() == RelationshipLabels.Mutual.EXPIRED) expired++;
            if (labels.y1() == RelationshipLabels.Mutual.IMMATURE) immature++;
            if (labels.y1() == RelationshipLabels.Mutual.WITHHELD) withheld++;
            if (labels.y2() == RelationshipLabels.AttendanceLabel.CONTESTED) contested++;
        }
        String authorization = (rows.size() - withheld) + "/" + rows.size();
        return List.of(count("双方接受", "MATCH", bothAccepted, rows.size(), "冻结观察窗口", unknown, contested, immature, 0, authorization),
                count("未知或过期", "MATCH", unknown + expired, rows.size(), "冻结观察窗口", unknown, contested, immature, 0, authorization));
    }

    private static List<CalibrationRow> frozenCalibration() {
        List<MatchingObservation> dev = new ArrayList<>();
        List<MatchingObservation> holdout = new ArrayList<>();
        for (MatchingObservation row : StrategyDataset.observations()) {
            if (StrategyDataset.development(row)) dev.add(row);
            else holdout.add(row);
        }
        String note = "开发集 " + dev.size() + "，保留集 " + holdout.size()
                + "。样本列是可二分类的验证行。未知、过期、争议和撤回没有记成负例。合成频率不是真实用户接受概率。校准结果只有汇总误差，没有分箱曲线点。";
        List<CalibrationRow> rows = new ArrayList<>();
        for (ScoreCalibration.Result result : ScoreCalibration.evaluate(dev, holdout)) {
            rows.add(new CalibrationRow(result.target().name(), result.method().name(), result.status(), result.sample(),
                    result.brier(), result.logLoss(), result.calibrationError(), note));
        }
        return List.copyOf(rows);
    }

    private static List<CalibrationRow> insufficient(int sample) {
        List<CalibrationRow> rows = new ArrayList<>();
        for (ScoreCalibration.Target target : ScoreCalibration.Target.values()) {
            for (ScoreCalibration.Method method : ScoreCalibration.Method.values()) {
                rows.add(new CalibrationRow(target.name(), method.name(), "INSUFFICIENT", sample, null, null, null,
                        "沙箱反馈太少，不训练有效模型。"));
            }
        }
        return List.copyOf(rows);
    }

    private static int learnable(List<MatchingObservation> rows) {
        int count = 0;
        for (MatchingObservation row : rows) {
            if (RelationshipLabels.of(row).y1Learnable()) count++;
        }
        return count;
    }

    private static boolean retracted(FeedbackLedger ledger) {
        return ledger.events().stream().anyMatch(event -> event.eventType().name().contains("RETRACT"));
    }

    private static boolean responded(String name) {
        return "ACCEPTED".equals(name) || "DECLINED".equals(name);
    }

    private static Count count(String metric, String level, int numerator, int denominator, String window,
                               int missing, int contested, int immature, int retracted, String authorization) {
        return new Count(metric, level, numerator, denominator, window, "server-projection", missing, contested, immature, retracted,
                authorization);
    }

    /**
     * M02 writes a 0–100 point score, or null when there is no known weight.
     * A value is never scaled because it is small. Null and out-of-range values are rejected.
     */
    static int scorePoints(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 100) {
            throw new FeedbackRejection("SCORE");
        }
        return (int) Math.round(value);
    }

    /** M02 coverage is a 0–1 fraction, or null when nothing is evaluable. It is not a point score. */
    static double coverageFraction(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 1) {
            throw new FeedbackRejection("COVERAGE");
        }
        return value;
    }

    private static LabRun find(SandboxLoop loop, String runId) {
        return loop.lab().runs().stream().filter(run -> run.runId().equals(runId)).findFirst()
                .orElseThrow(() -> new LabRejection("RUN"));
    }

    private static List<Risk> risks() {
        return List.of(
                risk("R-ACCOUNT-DELETE", "S01", "P0", "生产销户还没有接入 eraseActor", "OPEN", "生产账号", "AccountService.requestDelete 调用擦除", "risk-register.md", "2026-11-28"),
                risk("R-MATCH-SNAPSHOT", "M04", "P0", "真实匹配快照还没有接入", "OPEN", "生产匹配", "只读发布快照接入并经过安全复查", "V4-PROJECT-STATUS.md", "2026-11-28"),
                risk("R-FEEDBACK-API", "F01", "P0", "生产反馈接口还没有接入", "OPEN", "生产反馈", "独立生产适配器通过 F01 契约", "risk-register.md", "2026-11-28"),
                risk("R-V3-LEARNING", "F02", "P1", "V3 旧自动学习路径还需要治理", "OPEN", "旧学习写入", "旧路径不能绕过画像准入", "risk-register.md", "2026-11-28"),
                risk("R-PROFILE-QUALITY", "P01", "P1", "真实画像语义质量未过门槛", "OPEN", "真实画像", "独立语义评估达到门槛", "V4-PROJECT-STATUS.md", "2026-11-28"),
                risk("R-VECTOR-QUALITY", "M01", "P1", "真实向量质量未验证", "OPEN", "召回", "真实向量对照完成", "V4-PROJECT-STATUS.md", "2026-11-28"),
                risk("R-GRAPH-LIMIT", "M04", "P1", "大规模候选图仍受资源限制", "OPEN", "全局匹配", "有保护的资源实验", "V4-PROJECT-STATUS.md", "2026-11-28"),
                risk("R-BLOSSOM-TIMEOUT", "M04", "P1", "Blossom 超时还没有单独的资源隔离", "OPEN", "求解器", "超时不能拖垮其他租户", "risk-register.md", "2026-11-28"),
                risk("R-REAL-MODEL", "M03", "P0", "历史真实模型实验结论是 NO-GO", "OPEN", "推理", "新的预注册评估，而不是把本页未调用写成通过", "V4-M03-live-eval-report.md", "2026-11-27"),
                risk("R-RELATIONSHIP-QUALITY", "F03", "P0", "真实用户关系质量未验证", "OPEN", "关系质量", "真实反馈队列和受控实验", "V4-PROJECT-STATUS.md", "2026-11-28"));
    }

    private static List<Gate> gates() {
        return List.of(
                new Gate("工程质量", "SEE_CI", "以该提交的五个 CI 作业为准，页面不申报通过。"),
                new Gate("离线合成质量", "PASS_OFFLINE", "只覆盖已经冻结的合成集。"),
                new Gate("本页模型调用", "NOT_RUN", "策略、回放和安全页没有调用模型。这个状态不覆盖历史实验结论。"),
                new Gate("历史真实模型质量", "NO-GO", "V4-M03-live-eval-report.md：解释、新增关系和排序增益是 NO-GO。"),
                new Gate("真实语义向量质量", "NOT_RUN", "没有真实向量对照。"),
                new Gate("大规模候选图性能", "NO-GO", "候选图规模和求解资源仍受限制。"),
                new Gate("真实校准质量", "NOT_RUN", "合成频率不是真实接受概率。"),
                new Gate("真实策略提升", "NOT_IDENTIFIED", "没有真实受控实验。"),
                new Gate("真实关系质量", "NOT_RUN", "没有真实反馈队列。"),
                new Gate("生产启用", "NO-GO", "页面、地址参数和沙箱请求都不能打开生产 V4。"));
    }

    private static List<Capability> capabilities() {
        return List.of(
                new Capability("M04_CURRENT_ENGINE_REPLAY", "LIMITED", "用当前引擎重放内存中的 Held 图。回收后返回 HISTORY_UNAVAILABLE。"),
                new Capability("DESERIALIZED_REPLAY", "NOT_IMPLEMENTED", "不能只凭已保存文本重建候选图再执行。"),
                new Capability("FEATURE_DIFF", "NOT_IMPLEMENTED", "两次运行只比较输入摘要、策略版本和结果摘要。"),
                new Capability("CALIBRATION_CURVE", "NOT_IMPLEMENTED", "校准结果只有汇总误差，没有分箱曲线点。"),
                new Capability("M02_STANDALONE_REPLAY", "NOT_IMPLEMENTED", "没有单独从历史输入重跑双向评分。"),
                new Capability("F01_F03_ON_LEDGER", "LIMITED", "当前账本可以做事实投影和 F03 标签。"));
    }

    private static Risk risk(String id, String module, String priority, String statement, String status, String scope,
                             String resolution, String source, String sourceVersion) {
        return new Risk(id, module, priority, statement, status, scope, resolution, source, sourceVersion);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public record Report(String source, int observations, String datasetVersion, String labelVersion, String labelNote,
                         List<Count> counts, List<CalibrationRow> calibration, List<StrategyComparison.Row> strategies,
                         StrategyLearningProposal proposal, String limitations, String strategyDatasetVersion) {}

    public record Count(String metric, String level, int numerator, int denominator, String window, String source,
                        int missing, int contested, int immature, int retracted, String authorization) {}

    public record CalibrationRow(String target, String method, String status, int sample, Double brier, Double logLoss,
                                 Double calibrationError, String note) {}

    public record ReplayResult(String sourceRunId, String replayRunId, String executionMode, boolean semanticMatch,
                               String inputHash, String previousResultHash, String replayResultHash, String limitations) {}

    public record CompareResult(String leftRunId, String rightRunId, String leftType, String rightType,
                                boolean sameInput, boolean samePolicy, boolean sameSemanticResult,
                                String leftInputHash, String rightInputHash, String leftResultHash, String rightResultHash,
                                String note) {}

    public record SafetyReport(int plans, int safetyViolations, String production, String realRelationshipQuality,
                               String realLift, String algorithmQuality, List<Risk> risks, List<Gate> gates,
                               List<Capability> capabilities) {}

    public record Gate(String name, String status, String note) {}

    public record Capability(String name, String status, String note) {}

    public record Risk(String riskId, String module, String priority, String statement, String status, String scope,
                       String resolution, String source, String sourceVersion) {}
}
