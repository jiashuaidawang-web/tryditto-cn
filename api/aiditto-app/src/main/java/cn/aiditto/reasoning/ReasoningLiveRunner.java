package cn.aiditto.reasoning;

import cn.aiditto.ai.OpenAiCompatModel;
import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.reasoning.v4.DirectionalReasoning;
import cn.aiditto.domain.reasoning.v4.ReasoningBudget;
import cn.aiditto.domain.reasoning.v4.ReasoningChallenge;
import cn.aiditto.domain.reasoning.v4.ReasoningDataset;
import cn.aiditto.domain.reasoning.v4.ReasoningExperiment;
import cn.aiditto.domain.reasoning.v4.ReasoningJudgmentMetrics;
import cn.aiditto.domain.reasoning.v4.ReasoningLivePlan;
import cn.aiditto.domain.reasoning.v4.ReasoningMetrics;
import cn.aiditto.domain.reasoning.v4.ReasoningModel;
import cn.aiditto.domain.reasoning.v4.ReasoningPrompt;
import cn.aiditto.domain.reasoning.v4.ReasoningRanking;
import cn.aiditto.domain.reasoning.v4.ReasoningStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One named model, no fallback and no mock. The key is not printed.
 * A missing credential is NOT_RUN. Timeouts are not retried.
 */
public final class ReasoningLiveRunner {
    static final String DATASET_HASH = "ad3ac66b5aa1e4735bb034fbc231a90de4e26ef8f2a6e305a575f79a892b8944";
    static final String PROMPT_HASH = "a675b35e4002fb35b10868dd8530dc34f04b68927ba1be3f6f4ee41a042792fc";

    private ReasoningLiveRunner() {}

    public static void main(String[] args) {
        System.exit(run(args, System.getenv()));
    }

    static int run(String[] args, Map<String, String> env) {
        if (!DATASET_HASH.equals(ReasoningDataset.hash()) || !PROMPT_HASH.equals(ReasoningPrompt.hash())) {
            System.out.println("HASH_MISMATCH");
            return 3;
        }
        String stage = args == null || args.length == 0 ? "preflight" : args[0];
        String fallback = env == null ? null : env.get("LLM_FALLBACK_API_KEY");
        if (fallback != null && !fallback.isBlank()) {
            System.out.println("BLOCKED fallback key is set");
            return 2;
        }
        String key = env == null ? null : env.get("LLM_PRIMARY_API_KEY");
        String base = env == null ? null : env.get("LLM_PRIMARY_BASE_URL");
        String model = env == null ? null : env.get("LLM_PRIMARY_MODEL");
        System.out.println("dataset=" + DATASET_HASH);
        System.out.println("prompt=" + PROMPT_HASH + " version=" + ReasoningPrompt.VERSION);
        System.out.println("policyC1=" + ReasoningExperiment.POLICY + " policyC2=" + ReasoningExperiment.POLICY_C2);
        System.out.println("expectedFullCalls=" + ReasoningLivePlan.FULL_DIRECTION_CALLS
                + " smokeRelations=" + ReasoningLivePlan.SMOKE_RELATIONS
                + " challengeRelations=" + ReasoningChallenge.all().size()
                + " challengeHash=" + ReasoningChallenge.hash()
                + " callCap=" + ReasoningLivePlan.MODEL_CALL_CAP
                + " tokenCap=" + ReasoningLivePlan.MODEL_TOKEN_CAP);
        System.out.println("database=not-connected qdrant=not-connected thinking=disabled temperature=0");
        if (key == null || key.isBlank() || base == null || base.isBlank() || model == null || model.isBlank()) {
            System.out.println("NOT_RUN missing LLM_PRIMARY_API_KEY, LLM_PRIMARY_BASE_URL, or LLM_PRIMARY_MODEL");
            return 2;
        }
        if (!ReasoningLivePlan.MODELS.contains(model) || model.startsWith("mock")) {
            System.out.println("NOT_RUN model " + model + " is outside the frozen list");
            return 2;
        }
        System.out.println("mode=live model=" + model + " key=present fallback=disabled");
        if ("preflight".equals(stage)) return 0;
        long timeout = 45_000L;
        try {
            if (env.get("LLM_TIMEOUT_MS") != null) timeout = Long.parseLong(env.get("LLM_TIMEOUT_MS"));
        } catch (NumberFormatException ex) {
            System.out.println("BLOCKED LLM_TIMEOUT_MS");
            return 2;
        }
        OpenAiCompatModel endpoint = new OpenAiCompatModel(base, key, model, timeout, Map.of("thinking", Map.of("type", "disabled")));
        Path raw = env.get("LIVE_EVAL_OUT") == null ? null : Path.of(env.get("LIVE_EVAL_OUT"), "raw");
        Meter meter = new Meter(new SingleModelReasoningClient(endpoint), model, raw);
        ReasoningExperiment experiment = ReasoningExperiment.v1();
        try {
            if (raw != null) Files.createDirectories(raw);
            return switch (stage) {
                case "smoke" -> smoke(experiment, meter, env.get("LIVE_EVAL_OUT"));
                case "full" -> full(experiment, meter, env.get("LIVE_EVAL_OUT"));
                case "challenge" -> challenge(experiment, meter, env.get("LIVE_EVAL_OUT"));
                default -> {
                    System.out.println("BLOCKED stage " + stage);
                    yield 2;
                }
            };
        } catch (Exception ex) {
            System.out.println("FAILED " + ex.getClass().getSimpleName());
            return 1;
        }
    }

    private static int smoke(ReasoningExperiment experiment, Meter meter, String out) throws Exception {
        List<ReasoningDataset.Kind> kinds = List.of(ReasoningDataset.Kind.MUTUAL, ReasoningDataset.Kind.ONE_WAY,
                ReasoningDataset.Kind.CONDITIONAL, ReasoningDataset.Kind.MISSING, ReasoningDataset.Kind.CONTRADICTION,
                ReasoningDataset.Kind.COMPLEMENT, ReasoningDataset.Kind.FALSE_COMPLEMENT, ReasoningDataset.Kind.CROSSED,
                ReasoningDataset.Kind.FREQUENCY_INITIATIVE, ReasoningDataset.Kind.PERSONALITY_GAP);
        List<ReasoningDataset.Relation> selected = new ArrayList<>();
        for (ReasoningDataset.Kind kind : kinds) {
            selected.add(ReasoningDataset.all().stream().filter(item -> item.kind() == kind && item.copy() == 0).findFirst().orElseThrow());
        }
        List<ReasoningExperiment.PairResult> results = runRelations(experiment, meter, selected, out);
        int legal = 0;
        int hard = 0;
        int unauthorized = 0;
        for (ReasoningExperiment.PairResult result : results) {
            legal += legal(result.agentA()) + legal(result.agentB());
            hard += hard(result.agentA()) + hard(result.agentB());
            unauthorized += unauthorized(result, result.agentA()) + unauthorized(result, result.agentB());
        }
        double rate = legal / 20.0;
        boolean enter = rate >= ReasoningLivePlan.SMOKE_ENTRY_LEGAL_RATE && hard == 0 && unauthorized == 0;
        System.out.println("smokeRelations=" + selected.size() + " calls=" + meter.calls + " failures=" + meter.failures
                + " legal=" + legal + "/20 hard=" + hard + " unauthorized=" + unauthorized
                + " tokens=" + meter.tokens + " p50=" + meter.p50() + " p95=" + meter.p95());
        System.out.println("entry=" + (enter ? "yes" : "no") + " gate=" + ReasoningLivePlan.SMOKE_ENTRY_LEGAL_RATE);
        return enter ? 0 : 1;
    }

    private static int full(ReasoningExperiment experiment, Meter meter, String out) throws Exception {
        List<ReasoningDataset.Relation> relations = ReasoningDataset.all();
        List<ReasoningExperiment.PairResult> results = runRelations(experiment, meter, relations, out);
        if (results.size() != relations.size()) {
            System.out.println("PARTIAL relations=" + results.size() + "/" + relations.size());
            relations = relations.subList(0, results.size());
        }
        reportSplit("all", new Split(relations, results));
        reportSplit("dev", filter(relations, results, "dev"));
        reportSplit("holdout", filter(relations, results, "holdout"));
        System.out.println("calls=" + meter.calls + " failures=" + meter.failures + " budgetStops=" + meter.budgetStops
                + " tokensIn=" + meter.input + " tokensOut=" + meter.output + " reasoningTokens=unavailable cost=unavailable"
                + " p50=" + meter.p50() + " p95=" + meter.p95());
        return meter.failures > 0 && meter.calls == 0 ? 1 : 0;
    }

    private static void reportSplit(String name, Split split) {
        ReasoningMetrics.Report metrics = ReasoningMetrics.summarize(split.relations, split.results, List.of());
        int predicted = metrics.fitTp() + metrics.fitFp();
        System.out.println("metrics split=" + name
                + " relations=" + metrics.relations()
                + " direction=" + metrics.directionCorrect() + "/" + metrics.directionAttempted()
                + " refusals=" + metrics.safetyRefusals()
                + " support=" + metrics.fitTp() + "/" + predicted
                + " supportRelations=" + metrics.fitHit() + "/" + metrics.fitExpected()
                + " conflict=" + metrics.conflictTp() + "/" + (metrics.conflictTp() + metrics.conflictFp())
                + " complement=" + metrics.complementTp() + "/" + (metrics.complementTp() + metrics.complementFp())
                + " unsupportedUsable=" + metrics.unsupportedUsable() + "/" + metrics.usableClaims()
                + " missing=" + metrics.missingDetected() + "/" + metrics.missingExpected()
                + " contradiction=" + metrics.contradictionKept() + "/" + metrics.contradictionExpected()
                + " hardAccepted=" + metrics.illegalHardAccepted()
                + " unauthorized=" + metrics.unauthorizedReads());
        ReasoningRanking.Comparison ranking = ReasoningRanking.compare(split.relations, split.results);
        printScheme(name, ranking.m02());
        printScheme(name, ranking.serverExplanation());
        printScheme(name, ranking.modelExplanation());
        printScheme(name, ranking.c1());
        printScheme(name, ranking.c2());
    }

    private static Split filter(List<ReasoningDataset.Relation> relations, List<ReasoningExperiment.PairResult> results, String split) {
        List<ReasoningDataset.Relation> keptRelations = new ArrayList<>();
        List<ReasoningExperiment.PairResult> keptResults = new ArrayList<>();
        for (int i = 0; i < relations.size(); i++) {
            if (!split.equals(relations.get(i).split())) continue;
            keptRelations.add(relations.get(i));
            keptResults.add(results.get(i));
        }
        return new Split(keptRelations, keptResults);
    }

    private record Split(List<ReasoningDataset.Relation> relations, List<ReasoningExperiment.PairResult> results) {}

    private static int challenge(ReasoningExperiment experiment, Meter meter, String out) throws Exception {
        List<ReasoningChallenge.Case> cases = ReasoningChallenge.all();
        List<ReasoningExperiment.PairResult> results = new ArrayList<>();
        Path directory = out == null ? null : Path.of(out);
        if (directory != null) Files.createDirectories(directory);
        ReasoningBudget budget = new ReasoningBudget(16, 2, ReasoningLivePlan.MODEL_CALL_CAP);
        for (ReasoningChallenge.Case item : cases) {
            ReasoningExperiment.PairResult result = experiment.reason(item.seeker(), item.candidate(),
                    CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                            List.of(item.seeker(), item.candidate())), meter, budget);
            results.add(result);
            write(directory, item.id(), result);
            if (meter.tokens >= ReasoningLivePlan.MODEL_TOKEN_CAP) break;
        }
        ReasoningJudgmentMetrics.Report report = ReasoningJudgmentMetrics.evaluate(cases, results);
        printSplit(report.dev());
        printSplit(report.holdout());
        System.out.println("budgetUsed=" + budget.used() + " cacheOrSkip=" + budget.skips().size()
                + " reasons=" + reasons(budget.skips()));
        System.out.println("calls=" + meter.calls + " failures=" + meter.failures + " tokens=" + meter.tokens
                + " p50=" + meter.p50() + " p95=" + meter.p95() + " cost=unavailable");
        return 0;
    }

    private static List<ReasoningExperiment.PairResult> runRelations(ReasoningExperiment experiment, Meter meter,
                                                                     List<ReasoningDataset.Relation> relations, String out) throws Exception {
        Path directory = out == null ? null : Path.of(out);
        if (directory != null) Files.createDirectories(directory);
        ReasoningBudget budget = new ReasoningBudget(16, 2, ReasoningLivePlan.MODEL_CALL_CAP);
        List<ReasoningExperiment.PairResult> results = new ArrayList<>();
        for (ReasoningDataset.Relation relation : relations) {
            ReasoningExperiment.PairResult result = experiment.reason(relation.seeker(), relation.candidate(),
                    CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                            List.of(relation.seeker(), relation.candidate())), meter, budget);
            results.add(result);
            write(directory, relation.id(), result);
            if (meter.tokens >= ReasoningLivePlan.MODEL_TOKEN_CAP) {
                System.out.println("TOKEN_CAP");
                break;
            }
        }
        System.out.println("budgetUsed=" + budget.used() + " cacheOrSkip=" + budget.skips().size()
                + " reasons=" + reasons(budget.skips()));
        return results;
    }

    private static String reasons(List<String> skips) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String skip : skips) {
            int mark = skip.lastIndexOf(' ');
            String reason = mark < 0 ? skip : skip.substring(mark + 1);
            counts.merge(reason, 1, Integer::sum);
        }
        return counts.toString();
    }

    private static void write(Path directory, String id, ReasoningExperiment.PairResult result) throws Exception {
        if (directory == null) return;
        StringBuilder text = new StringBuilder();
        text.append(id).append(" A=").append(result.agentA().status()).append(" B=").append(result.agentB().status())
                .append(" c1=").append(result.schemeC1().status()).append(" c2=").append(result.schemeC2().status())
                .append(" m02=").append(result.schemeA().m02Score())
                .append(" c1score=").append(result.schemeC1().experimentalScore())
                .append(" c2score=").append(result.schemeC2().experimentalScore())
                .append(" explanations=").append(result.ledger().m02Explanations())
                .append(" hypotheses=").append(result.ledger().hypotheses())
                .append(" findings=").append(result.ledger().newFindings())
                .append(" duplicates=").append(result.ledger().duplicates()).append('\n');
        append(text, "AB", result.agentA());
        append(text, "BA", result.agentB());
        Files.writeString(directory.resolve(id + ".txt"), text.toString());
    }

    private static void append(StringBuilder text, String side, DirectionalReasoning reasoning) {
        text.append(side).append(" failure=").append(reasoning.failure()).append('\n');
        dump(text, side, "usable", reasoning.usable());
        dump(text, side, "unsupported", reasoning.unsupportedClaims());
        dump(text, side, "finding", reasoning.experimentFindings());
    }

    private static void dump(StringBuilder text, String side, String role, List<cn.aiditto.domain.reasoning.v4.ReasoningClaim> claims) {
        for (cn.aiditto.domain.reasoning.v4.ReasoningClaim claim : claims) {
            text.append(side).append(' ').append(role).append(' ').append(claim.category())
                    .append(' ').append(claim.preferenceCode()).append(" -> ").append(claim.targetFeatureCode())
                    .append(" refs=").append(claim.evidenceRefs())
                    .append(" statement=").append(claim.statement().replace('\n', ' ')).append('\n');
        }
    }

    private static int legal(DirectionalReasoning reasoning) {
        return reasoning.status() == ReasoningStatus.COMPLETED || reasoning.status() == ReasoningStatus.REFUSED ? 1 : 0;
    }

    private static int hard(DirectionalReasoning reasoning) {
        return reasoning.usable().stream().anyMatch(claim -> claim.statement().contains("硬约束")) ? 1 : 0;
    }

    private static int unauthorized(ReasoningExperiment.PairResult result, DirectionalReasoning reasoning) {
        return reasoning.usable().stream().mapToInt(claim -> (int) claim.evidenceRefs().stream()
                .filter(ref -> !result.context().view(reasoning.direction()).allows(ref)).count()).sum();
    }

    private static void printScheme(String split, ReasoningRanking.Scheme scheme) {
        System.out.println("split=" + split + " scheme=" + scheme.name() + " pairwise=" + scheme.pairwise() + "/" + scheme.total()
                + " ndcg=" + scheme.ndcg() + " moves=" + scheme.scoreMoves()
                + " ineligibleMoves=" + scheme.ineligibleMoves()
                + " insufficientRecommended=" + scheme.insufficientRecommended()
                + " oneWayRecommended=" + scheme.oneWayRecommended());
    }

    private static void printSplit(ReasoningJudgmentMetrics.Split split) {
        System.out.println("split=" + split.name()
                + " legal=" + split.structuredLegal() + "/" + split.structuredAttempted()
                + " direction=" + split.directionCorrect() + "/" + split.directionAttempted()
                + " refs=" + split.refsLegal() + "/" + split.refsSeen()
                + " relevantClaims=" + split.refsRelevant()
                + " support=" + split.supportTp() + "/" + split.supportPredicted() + " expected=" + split.supportExpected()
                + " unsupported=" + split.unsupportedClaims() + "/" + split.emittedClaims()
                + " unsupportedUsable=" + split.unsupportedUsable() + "/" + split.usableClaims()
                + " duplicates=" + split.duplicateClaims()
                + " hypothesisConfused=" + split.hypothesisConfused() + "/" + split.hypothesisExpected()
                + " mustRejectAccepted=" + split.mustRejectAccepted() + "/" + split.mustRejectExpected());
    }

    private static final class Meter implements ReasoningModel {
        private final ReasoningModel delegate;
        private final String modelName;
        private final Path raw;
        private final List<Long> latency = new ArrayList<>();
        private int calls;
        private int failures;
        private int budgetStops;
        private int input;
        private int output;
        private int tokens;

        private Meter(ReasoningModel delegate, String modelName, Path raw) {
            this.delegate = delegate;
            this.modelName = modelName;
            this.raw = raw;
        }

        @Override
        public ReasoningCompletion complete(ReasoningPrompt.Rendered prompt) {
            if (calls >= ReasoningLivePlan.MODEL_CALL_CAP || tokens >= ReasoningLivePlan.MODEL_TOKEN_CAP) {
                budgetStops++;
                return ReasoningCompletion.failed(delegate == null ? "capped" : "capped", "BUDGET");
            }
            calls++;
            long started = System.nanoTime();
            ReasoningCompletion completion = delegate.complete(prompt);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            latency.add(elapsed);
            if (raw != null && completion != null) {
                String id = String.format("%04d-%s", calls, prompt.view() == null ? "na" : prompt.view().direction());
                try {
                    Files.writeString(raw.resolve(id + ".txt"), completion.content() == null ? "" : completion.content());
                    Files.writeString(raw.resolve(id + ".meta"), "model=" + modelName + " latencyMs=" + elapsed
                            + " in=" + completion.inputTokens() + " out=" + completion.outputTokens()
                            + " failed=" + completion.failed() + " failure=" + completion.failure() + "\n");
                } catch (java.io.IOException ex) {
                    System.out.println("RAW_WRITE_FAILED " + id);
                }
            }
            if (completion == null || completion.failed()) failures++;
            else {
                input += completion.inputTokens() == null ? 0 : completion.inputTokens();
                output += completion.outputTokens() == null ? 0 : completion.outputTokens();
                tokens = input + output;
            }
            return completion;
        }

        private long p50() { return percentile(0.50); }
        private long p95() { return percentile(0.95); }

        private long percentile(double p) {
            if (latency.isEmpty()) return 0;
            List<Long> sorted = new ArrayList<>(latency);
            sorted.sort(Long::compare);
            int index = (int) Math.ceil(p * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }
    }
}
