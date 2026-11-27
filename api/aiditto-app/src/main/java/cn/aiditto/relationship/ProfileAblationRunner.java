package cn.aiditto.relationship;

import cn.aiditto.ai.LlmCallLogger;
import cn.aiditto.ai.LlmException;
import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmProperties;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.ai.OpenAiCompatModel;
import cn.aiditto.domain.relationship.CitedFeature;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.domain.relationship.ExtractionTask;
import cn.aiditto.domain.relationship.FeatureContract;
import cn.aiditto.domain.relationship.FeatureContractCatalog;
import cn.aiditto.domain.relationship.FeatureDefinition;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.ProfileContext;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Statement;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.domain.relationship.ProfileExtractionValidator;
import cn.aiditto.domain.relationship.ProfileLiveEval;
import cn.aiditto.domain.relationship.ProfileLiveEvalV2;
import cn.aiditto.domain.relationship.SemanticSupport;
import cn.aiditto.infra.json.Json;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Offline ablation driver. It does not write a production profile or open free-text export.
 * E0 stays in {@link ProfileLiveEvalRunner}. Each later stage adds one change.
 */
public final class ProfileAblationRunner {
    private static final FeatureRegistry REGISTRY = FeatureRegistry.v1();
    private static final List<FeatureContract> CONTRACTS = FeatureContractCatalog.modelExtractable(REGISTRY);
    private static final Set<String> CONTRACT_CODES = contractCodes();
    private static final long SYNTHETIC_USER = 91_001L;

    private ProfileAblationRunner() {}

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.out);
        if (code != 0) System.exit(code);
    }

    public static int run(String[] args, Map<String, String> env, PrintStream out) {
        String stage = arg(args, 0, "e1");
        String mode = arg(args, 1, "preflight");
        String dataset = arg(args, 2, "v1");
        if (!stage.matches("e[1-4]") || !mode.matches("preflight|smoke|full") || !dataset.matches("v1|v2holdout")) {
            out.println("usage: ProfileAblationRunner e1|e2|e3|e4 preflight|smoke|full v1|v2holdout");
            return 2;
        }
        if (!ProfileLiveEvalRunner.hashesMatch()) {
            out.println("NOT_RUN the frozen v1 dataset or prompt changed.");
            return 3;
        }
        if (!ProfileLiveEvalRunner.liveConfigured(env)) {
            out.println("NOT_RUN");
            out.println(ProfileLiveEval.BLOCKER);
            return 2;
        }
        if (ProfileLiveEvalRunner.fallbackConfigured(env)) {
            out.println("NOT_RUN fallback is set. This experiment refuses fallback results.");
            return 2;
        }
        String promptVersion = promptVersion(stage);
        if ("preflight".equals(mode)) {
            out.println("READY");
            out.println("stage=" + stage);
            out.println("dataset=" + dataset);
            out.println("mode=live");
            out.println("baseHost=" + hostOf(value(env, "LLM_PRIMARY_BASE_URL")));
            out.println("apiKey=present");
            out.println("model=" + value(env, "LLM_PRIMARY_MODEL"));
            out.println("fallback=disabled");
            out.println("prompt=" + promptVersion);
            out.println("promptHash=" + ProfileExtractionPrompts.digest(promptVersion));
            out.println("verifyHash=" + ProfileExtractionPrompts.digest(ProfileExtractionPrompts.VERIFY_VERSION));
            out.println("datasetHash=" + ("v1".equals(dataset) ? ProfileLiveEval.datasetHash() : ProfileLiveEvalV2.datasetHash()));
            out.println("database=not-connected");
            out.println("samplesSent=0");
            return 0;
        }
        CallTrace trace = new CallTrace();
        LlmGateway gateway = gateway(env, trace);
        List<Unit> selected = select(stage, mode, dataset);
        List<Map<String, Object>> rows = new ArrayList<>();
        String secret = value(env, "LLM_PRIMARY_API_KEY");
        for (Unit unit : selected) {
            trace.error = "";
            Map<String, Object> row = one(unit, stage, gateway, trace, secret);
            rows.add(row);
            if (Boolean.TRUE.equals(row.get("called")) && String.valueOf(row.get("model")).startsWith("mock")) {
                out.println("NOT_RUN refused a mock result for " + unit.id());
                write(env, stage, dataset, mode, rows, secret, out);
                return 4;
            }
            out.println(unit.id() + " stage=" + stage + " called=" + row.get("called")
                    + " status=" + row.get("status") + " raw=" + row.get("rawKeys")
                    + " deterministic=" + row.get("deterministicKeys")
                    + " experiment=" + row.get("experimentKeys")
                    + " latencyMs=" + row.get("latencyMs")
                    + (String.valueOf(row.get("error")).isBlank() ? "" : " error=" + row.get("error")));
        }
        write(env, stage, dataset, mode, rows, secret, out);
        if ("smoke".equals(mode)) {
            for (Map<String, Object> row : rows) {
                if (Boolean.TRUE.equals(row.get("called"))
                        && (!"called".equals(row.get("status")) || !Boolean.TRUE.equals(row.get("structured")))) {
                    out.println("SMOKE_FAILED " + row.get("sampleId") + " status=" + row.get("status"));
                    return 5;
                }
            }
        }
        return 0;
    }

    private static Map<String, Object> one(Unit unit, String stage, LlmGateway gateway, CallTrace trace, String secret) {
        ProfileContext production = ProfileContextBuilder.build(inputs(Map.of(), List.of(new Statement("SELF", unit.text()))));
        boolean leak = production.exportCanonical().contains(unit.text());
        Map<String, Object> row = base(unit, leak);
        if (unit.withheld()) {
            row.put("status", "not-sent");
            row.put("failureClass", "8");
            return row;
        }
        boolean facts = "e1".equals(stage) || "e2".equals(stage);
        List<Statement> statements = "e1".equals(stage)
                ? List.of(new Statement("SELF", unit.text()), new Statement("PREFERENCE", unit.text()))
                : List.of(new Statement("USER", unit.text()));
        ProfileContext synthetic = ProfileContextBuilder.buildEvaluation(inputs(
                facts ? Map.of("hobbies", List.of("reading")) : Map.of(), statements));
        String version = promptVersion(stage);
        String payload = "e1".equals(stage) || "e2".equals(stage)
                ? ProfileExtractionPrompts.userPayload(synthetic.exportCanonical())
                : ExtractionTask.payload(synthetic, CONTRACTS, false);
        LlmResult result = gateway.call(new LlmRequest(ProfileExtractionPrompts.CALL_POINT, version,
                ProfileExtractionPrompts.systemFor(version), payload, 1200, 0.0, true, true));
        row.put("called", true);
        row.put("model", result.model());
        row.put("latencyMs", result.latencyMs());
        row.put("inputTokens", result.inputTokens());
        row.put("outputTokens", result.outputTokens());
        row.put("rawOutput", redact(truncate(result.content()), secret));
        if (result.degraded() || result.model() == null || result.model().startsWith("mock")) {
            row.put("status", result.degraded() ? "degraded" : "refused");
            row.put("error", redact(trace.error, secret));
            row.put("failureClass", "10");
            return row;
        }
        List<CitedFeature> cited = "e1".equals(stage) ? fromLegacy(result.content()) : fromTask(result.content());
        if (cited == null) {
            row.put("status", "called");
            row.put("structured", false);
            row.put("failureClass", "9");
            return row;
        }
        row.put("structured", true);
        row.put("status", "called");
        ProfileExtractionValidator.Result checked = ProfileExtractionValidator.checkCited(synthetic, cited, REGISTRY);
        List<String> raw = new ArrayList<>();
        List<String> deterministic = acceptedKeys(checked);
        List<String> experiment = new ArrayList<>(deterministic);
        int registered = 0;
        int validEnum = 0;
        int unknown = 0;
        int refValid = 0;
        int spanCorrect = 0;
        int knownFact = 0;
        int pending = 0;
        int verifyCalls = 0;
        long verifyLatency = 0;
        int verifyIn = 0;
        int verifyOut = 0;
        Set<String> questionnaire = questionnaireCodes(synthetic);
        for (int i = 0; i < cited.size(); i++) {
            CitedFeature item = cited.get(i);
            ExtractedFeature feature = item.feature();
            String key = feature.section() + "|" + feature.featureCode() + "|" + feature.proposedValue();
            raw.add(key);
            boolean known = REGISTRY.all().stream().anyMatch(definition -> definition.featureCode().equals(feature.featureCode()));
            if (!known) unknown++;
            else {
                registered++;
                FeatureDefinition definition = REGISTRY.require(feature.featureCode());
                if (definition.allowedValues().contains(feature.proposedValue())) validEnum++;
                if (!CONTRACT_CODES.contains(feature.featureCode()) || questionnaire.contains(feature.featureCode())) knownFact++;
            }
            if (refsValid(synthetic, feature)) refValid++;
            if (!"e1".equals(stage) && spanValid(synthetic, item)) spanCorrect++;
            SemanticSupport support = checked.verdicts().get(i).support();
            if (support == SemanticSupport.SEMANTIC_SUPPORT_PENDING && "e4".equals(stage)) {
                trace.error = "";
                String definition = definitionOf(feature.featureCode());
                String allowed = allowedOf(feature.featureCode());
                String ref = feature.evidenceRefs().isEmpty() ? "" : feature.evidenceRefs().get(0);
                LlmResult verdict = gateway.call(new LlmRequest(ProfileExtractionPrompts.VERIFY_CALL_POINT,
                        ProfileExtractionPrompts.VERIFY_VERSION, ProfileExtractionPrompts.verifySystem(),
                        ProfileExtractionPrompts.verifyPayload(ref, item.evidenceSpan(), feature.section(),
                                feature.featureCode(), feature.proposedValue(), definition, allowed),
                        400, 0.0, true, true));
                verifyCalls++;
                verifyLatency += verdict.latencyMs();
                if (verdict.inputTokens() != null) verifyIn += verdict.inputTokens();
                if (verdict.outputTokens() != null) verifyOut += verdict.outputTokens();
                String label = verdict.degraded() ? "" : ProfileExtractionParser.verdict(verdict.content());
                row.put("verifier:" + i, label.isBlank() ? "PENDING" : label);
                if ("SUPPORTED".equals(label) && !experiment.contains(key)) experiment.add(key);
                else pending++;
            } else if (support == SemanticSupport.SEMANTIC_SUPPORT_PENDING) {
                pending++;
            }
        }
        row.put("rawKeys", raw);
        row.put("deterministicKeys", deterministic);
        row.put("experimentKeys", experiment);
        row.put("rejectionReason", checked.rejected().stream().map(item -> item.featureCode() + ":" + item.reason()).toList());
        row.put("registered", registered);
        row.put("validEnum", validEnum);
        row.put("unknownCodes", unknown);
        row.put("refValid", refValid);
        row.put("spanCorrect", spanCorrect);
        row.put("knownFact", knownFact);
        row.put("pending", pending);
        row.put("hardRequests", checked.hardRequests());
        row.put("hardAccepted", hardAccepted(cited, deterministic));
        row.put("verifyCalls", verifyCalls);
        row.put("verifyLatencyMs", verifyLatency);
        row.put("verifyInputTokens", verifyIn);
        row.put("verifyOutputTokens", verifyOut);
        row.put("failureClass", classify(unit, raw, deterministic, experiment));
        return row;
    }

    private static String classify(Unit unit, List<String> raw, List<String> deterministic, List<String> experiment) {
        if ("EXTRACT".equals(unit.disposition())) {
            boolean rawHit = unit.expected().stream().allMatch(raw::contains);
            boolean kept = unit.expected().stream().allMatch(experiment::contains);
            if (rawHit && !kept) return "5";
            if (!rawHit) return "1";
            if (raw.stream().anyMatch(key -> !unit.expected().contains(key))) return "1";
            return "ok";
        }
        if (!raw.isEmpty()) return "1";
        return "ok";
    }

    private static void write(Map<String, String> env, String stage, String dataset, String mode,
                              List<Map<String, Object>> rows, String secret, PrintStream out) {
        String dir = value(env, "LIVE_EVAL_OUT");
        Map<String, Object> summary = summary(stage, dataset, mode, env, rows);
        String json = redact(Json.write(summary), secret);
        out.println(json);
        if (dir.isBlank()) return;
        try {
            Path root = Path.of(dir);
            Files.createDirectories(root);
            Files.writeString(root.resolve("summary-" + mode + ".json"), json + "\n", StandardCharsets.UTF_8);
            StringBuilder lines = new StringBuilder();
            for (Map<String, Object> row : rows) lines.append(redact(Json.write(row), secret)).append('\n');
            Files.writeString(root.resolve("samples-" + mode + ".jsonl"), lines.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("REPORT_WRITE_FAILED " + e.getClass().getSimpleName());
        }
    }

    private static Map<String, Object> summary(String stage, String dataset, String mode,
                                              Map<String, String> env, List<Map<String, Object>> rows) {
        int calls = 0;
        int structured = 0;
        int predicted = 0;
        int rawHits = 0;
        int positives = 0;
        int rawRecalled = 0;
        int detPredicted = 0;
        int detHits = 0;
        int detRecalled = 0;
        int expPredicted = 0;
        int expHits = 0;
        int expRecalled = 0;
        int registered = 0;
        int validEnum = 0;
        int unknown = 0;
        int refValid = 0;
        int spanCorrect = 0;
        int spanBase = 0;
        int knownFact = 0;
        int pending = 0;
        int hardAccepted = 0;
        int leaks = 0;
        int notSent = 0;
        int contradiction = 0;
        int contradictionKept = 0;
        int confusion = 0;
        int confusionBase = 0;
        int expectedCovered = 0;
        int expectedTotal = 0;
        long input = 0;
        long output = 0;
        int verifyCalls = 0;
        long verifyLatency = 0;
        List<Long> latency = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (Boolean.TRUE.equals(row.get("productionLeak"))) leaks++;
            if (!Boolean.TRUE.equals(row.get("called"))) {
                notSent++;
                continue;
            }
            calls++;
            if (Boolean.TRUE.equals(row.get("structured"))) structured++;
            latency.add(((Number) row.getOrDefault("latencyMs", 0)).longValue());
            input += number(row.get("inputTokens"));
            output += number(row.get("outputTokens"));
            verifyCalls += number(row.get("verifyCalls"));
            verifyLatency += number(row.get("verifyLatencyMs"));
            input += number(row.get("verifyInputTokens"));
            output += number(row.get("verifyOutputTokens"));
            List<String> expected = strings(row.get("expected"));
            List<String> raw = strings(row.get("rawKeys"));
            List<String> deterministic = strings(row.get("deterministicKeys"));
            List<String> experiment = strings(row.get("experimentKeys"));
            if ("CONTRADICTION".equals(row.get("disposition"))) {
                contradiction++;
                if (experiment.isEmpty()) contradictionKept++;
            }
            if ("EXTRACT".equals(row.get("disposition"))) {
                for (String key : expected) {
                    positives++;
                    expectedTotal++;
                    if (contractCode(key)) expectedCovered++;
                    if (raw.contains(key)) rawRecalled++;
                    if (deterministic.contains(key)) detRecalled++;
                    if (experiment.contains(key)) expRecalled++;
                }
            }
            for (String key : raw) {
                predicted++;
                if (expected.contains(key)) rawHits++;
                if (confused(expected, key)) confusion++;
                if (!expected.isEmpty()) confusionBase++;
            }
            for (String key : deterministic) {
                detPredicted++;
                if (expected.contains(key)) detHits++;
            }
            for (String key : experiment) {
                expPredicted++;
                if (expected.contains(key)) expHits++;
            }
            registered += number(row.get("registered"));
            validEnum += number(row.get("validEnum"));
            unknown += number(row.get("unknownCodes"));
            refValid += number(row.get("refValid"));
            knownFact += number(row.get("knownFact"));
            pending += number(row.get("pending"));
            hardAccepted += number(row.get("hardAccepted"));
            if (!"e1".equals(stage)) {
                spanCorrect += number(row.get("spanCorrect"));
                spanBase += raw.size();
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stage", stage);
        summary.put("dataset", dataset);
        summary.put("mode", mode);
        summary.put("model", value(env, "LLM_PRIMARY_MODEL"));
        summary.put("prompt", promptVersion(stage));
        summary.put("promptHash", ProfileExtractionPrompts.digest(promptVersion(stage)));
        summary.put("datasetHash", "v1".equals(dataset) ? ProfileLiveEval.datasetHash() : ProfileLiveEvalV2.datasetHash());
        summary.put("samples", rows.size());
        summary.put("modelCalls", calls);
        summary.put("notSent", notSent);
        summary.put("structuredSuccess", structured);
        summary.put("schemaComplianceRate", ProfileLiveEvalRunner.ratio(structured, calls));
        summary.put("rawPrecision", ProfileLiveEvalRunner.ratio(rawHits, predicted));
        summary.put("rawRecall", ProfileLiveEvalRunner.ratio(rawRecalled, positives));
        summary.put("rawF1", ProfileLiveEvalRunner.f1(ProfileLiveEvalRunner.ratio(rawHits, predicted), ProfileLiveEvalRunner.ratio(rawRecalled, positives)));
        summary.put("deterministicPrecision", ProfileLiveEvalRunner.ratio(detHits, detPredicted));
        summary.put("deterministicRecall", ProfileLiveEvalRunner.ratio(detRecalled, positives));
        summary.put("experimentPrecision", ProfileLiveEvalRunner.ratio(expHits, expPredicted));
        summary.put("experimentRecall", ProfileLiveEvalRunner.ratio(expRecalled, positives));
        summary.put("registeredFeatureCodeRate", ProfileLiveEvalRunner.ratio(registered, predicted));
        summary.put("unknownFeatureCodeRate", ProfileLiveEvalRunner.ratio(unknown, predicted));
        summary.put("validEnumRate", ProfileLiveEvalRunner.ratio(validEnum, registered));
        summary.put("evidenceReferenceValidity", ProfileLiveEvalRunner.ratio(refValid, predicted));
        summary.put("evidenceSpanAccuracy", ProfileLiveEvalRunner.ratio(spanCorrect, spanBase));
        summary.put("semanticSupportPrecision", ProfileLiveEvalRunner.ratio(expHits, expPredicted));
        summary.put("semanticExtractionRecall", ProfileLiveEvalRunner.ratio(expRecalled, positives));
        summary.put("knownFactDuplicationRate", ProfileLiveEvalRunner.ratio(knownFact, predicted));
        summary.put("confusion", confusion);
        summary.put("confusionRate", ProfileLiveEvalRunner.ratio(confusion, confusionBase));
        summary.put("unsupportedClaimRate", ProfileLiveEvalRunner.ratio(predicted - rawHits, predicted));
        summary.put("pendingFeatureRate", ProfileLiveEvalRunner.ratio(pending, predicted));
        summary.put("contradictionPreservationRate", ProfileLiveEvalRunner.ratio(contradictionKept, contradiction));
        summary.put("registryCoverageRate", ProfileLiveEvalRunner.ratio(expectedCovered, expectedTotal));
        summary.put("hardAccepted", hardAccepted);
        summary.put("productionLeaks", leaks);
        summary.put("latencyP50Ms", percentile(latency, 0.50));
        summary.put("latencyP95Ms", percentile(latency, 0.95));
        summary.put("inputTokens", input);
        summary.put("outputTokens", output);
        summary.put("verifyCalls", verifyCalls);
        summary.put("verifyLatencyMs", verifyLatency);
        summary.put("cost", "adapter-did-not-provide");
        return summary;
    }

    private static boolean contractCode(String key) {
        String[] parts = key.split("\\|", -1);
        return parts.length >= 2 && CONTRACT_CODES.contains(parts[1]);
    }

    private static boolean confused(List<String> expected, String predicted) {
        String[] right = predicted.split("\\|", -1);
        if (right.length < 2) return false;
        for (String key : expected) {
            String[] left = key.split("\\|", -1);
            if (left.length >= 2 && left[1].equals(right[1]) && !left[0].equals(right[0])) return true;
        }
        return false;
    }

    private static boolean refsValid(ProfileContext context, ExtractedFeature feature) {
        if (feature.evidenceRefs().isEmpty()) return false;
        for (String ref : feature.evidenceRefs()) if (context.item(ref) == null) return false;
        return true;
    }

    private static boolean spanValid(ProfileContext context, CitedFeature cited) {
        if (cited.evidenceSpan().isBlank() || cited.feature().evidenceRefs().isEmpty()) return false;
        for (String ref : cited.feature().evidenceRefs()) {
            ProfileContext.Item item = context.item(ref);
            if (item == null || item.text() == null || !item.text().contains(cited.evidenceSpan())) return false;
        }
        return true;
    }

    private static Set<String> questionnaireCodes(ProfileContext context) {
        Set<String> codes = new TreeSet<>();
        for (ProfileContext.Item item : context.items()) {
            if ("QUESTIONNAIRE".equals(item.kind()) && item.featureCode() != null) codes.add(item.featureCode());
        }
        return codes;
    }

    private static int hardAccepted(List<CitedFeature> cited, List<String> accepted) {
        int count = 0;
        for (CitedFeature item : cited) {
            ExtractedFeature feature = item.feature();
            String key = feature.section() + "|" + feature.featureCode() + "|" + feature.proposedValue();
            if (feature.hardConstraint() && accepted.contains(key)) count++;
        }
        return count;
    }

    private static List<String> acceptedKeys(ProfileExtractionValidator.Result result) {
        List<String> keys = new ArrayList<>();
        for (ProfileEvidence evidence : result.accepted()) {
            String key = evidence.section().name() + "|" + evidence.featureCode() + "|" + claimText(evidence.claim());
            if (!keys.contains(key)) keys.add(key);
        }
        return keys;
    }

    private static String claimText(FeatureValue claim) {
        if (claim instanceof FeatureValue.EnumOne one) return one.value();
        if (claim instanceof FeatureValue.Bool value) return Boolean.toString(value.value());
        if (claim instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        return "";
    }

    private static List<CitedFeature> fromLegacy(String content) {
        ProfileExtractionParser.Parse parsed = ProfileExtractionParser.parse(content);
        if (!parsed.structured()) return null;
        List<CitedFeature> cited = new ArrayList<>();
        for (ExtractedFeature feature : parsed.features()) cited.add(new CitedFeature(feature));
        return cited;
    }

    private static List<CitedFeature> fromTask(String content) {
        ProfileExtractionParser.TaskParse parsed = ProfileExtractionParser.parseTask(content);
        return parsed.structured() ? parsed.cited() : null;
    }

    private static String definitionOf(String code) {
        for (FeatureContract contract : CONTRACTS) {
            if (contract.featureCode().equals(code)) return contract.semanticDefinition();
        }
        return "";
    }

    private static String allowedOf(String code) {
        for (FeatureContract contract : CONTRACTS) {
            if (contract.featureCode().equals(code)) return String.join(",", contract.allowedValues());
        }
        return "";
    }

    private static Map<String, Object> base(Unit unit, boolean leak) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sampleId", unit.id());
        row.put("category", unit.category());
        row.put("disposition", unit.disposition());
        row.put("expected", unit.expected());
        row.put("called", false);
        row.put("structured", false);
        row.put("status", "not-sent");
        row.put("model", "");
        row.put("rawOutput", "");
        row.put("rawKeys", List.of());
        row.put("deterministicKeys", List.of());
        row.put("experimentKeys", List.of());
        row.put("rejectionReason", List.of());
        row.put("latencyMs", 0);
        row.put("inputTokens", 0);
        row.put("outputTokens", 0);
        row.put("error", "");
        row.put("failureClass", "8");
        row.put("productionLeak", leak);
        row.put("registered", 0);
        row.put("validEnum", 0);
        row.put("unknownCodes", 0);
        row.put("refValid", 0);
        row.put("spanCorrect", 0);
        row.put("knownFact", 0);
        row.put("pending", 0);
        row.put("hardRequests", 0);
        row.put("hardAccepted", 0);
        row.put("verifyCalls", 0);
        row.put("verifyLatencyMs", 0);
        row.put("verifyInputTokens", 0);
        row.put("verifyOutputTokens", 0);
        return row;
    }

    private static List<Unit> select(String stage, String mode, String dataset) {
        List<Unit> all = "v2holdout".equals(dataset) ? holdout() : v1();
        if (!"smoke".equals(mode)) return all;
        List<String> ids = "v2holdout".equals(dataset)
                ? ProfileLiveEvalV2.HOLDOUT_SMOKE_IDS : ProfileLiveEvalRunner.SMOKE_IDS;
        List<Unit> out = new ArrayList<>();
        for (String id : ids) {
            for (Unit unit : all) if (unit.id().equals(id)) out.add(unit);
        }
        return List.copyOf(out);
    }

    private static List<Unit> v1() {
        List<Unit> out = new ArrayList<>();
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) {
            boolean withheld = "private-boundary".equals(sample.category());
            List<String> expected = sample.expectedKey() == null || sample.expectedKey().isBlank()
                    ? List.of() : List.of(sample.expectedKey());
            String disposition = withheld ? "WITHHOLD"
                    : !expected.isEmpty() ? "EXTRACT"
                    : "contradiction".equals(sample.category()) ? "CONTRADICTION" : "UNKNOWN";
            out.add(new Unit(sample.id(), sample.category(), sample.text(), expected, disposition, withheld));
        }
        return out;
    }

    private static List<Unit> holdout() {
        List<Unit> out = new ArrayList<>();
        for (ProfileLiveEvalV2.Sample sample : ProfileLiveEvalV2.holdout()) {
            out.add(new Unit(sample.id(), sample.category(), sample.text(), sample.expectedKeys(),
                    sample.disposition(), sample.withheld()));
        }
        return out;
    }

    private static String promptVersion(String stage) {
        return switch (stage) {
            case "e1" -> ProfileExtractionPrompts.VERSION_V2;
            case "e2" -> ProfileExtractionPrompts.VERSION_V3;
            default -> ProfileExtractionPrompts.VERSION_V4;
        };
    }

    private static ProfileContextBuilder.Inputs inputs(Map<String, Object> answers, List<Statement> statements) {
        return new ProfileContextBuilder.Inputs(SYNTHETIC_USER, 1, 1, true, true, answers, statements, List.of(), 1L);
    }

    private static LlmGateway gateway(Map<String, String> env, CallTrace trace) {
        LlmProperties props = new LlmProperties("live", value(env, "LLM_PRIMARY_BASE_URL"),
                value(env, "LLM_PRIMARY_API_KEY"), value(env, "LLM_PRIMARY_MODEL"),
                "", "", "deepseek-flash", 2, longValue(env, "LLM_TIMEOUT_MS", 45_000L),
                0L, 5, value(env, "LLM_PRIMARY_EXTRA_BODY"), "");
        if (props.fallbackAvailable()) throw new IllegalStateException("fallback");
        LlmModel primary = new OpenAiCompatModel(props.primaryBaseUrl(), props.primaryApiKey(),
                props.primaryModel(), props.timeoutMs(), props.primaryExtraBodyMap());
        LlmModel recording = new LlmModel() {
            @Override
            public String name() { return primary.name(); }

            @Override
            public Raw call(LlmRequest request) {
                try {
                    return primary.call(request);
                } catch (LlmException e) {
                    trace.error = e.kind().name() + " " + e.getMessage();
                    throw e;
                }
            }
        };
        return new LlmGateway(recording, null, new LlmCallLogger.InMemory(), props,
                content -> content != null && !content.isBlank(), () -> 1L, delay -> { });
    }

    private static Set<String> contractCodes() {
        Set<String> codes = new TreeSet<>();
        for (FeatureContract contract : CONTRACTS) codes.add(contract.featureCode());
        return Set.copyOf(codes);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    private static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;
        return sorted.get(index);
    }

    private static String redact(String text, String secret) {
        return ProfileLiveEvalRunner.redact(text, secret);
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 6000 ? text : text.substring(0, 6000) + " [truncated]";
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "unparsed" : host;
        } catch (RuntimeException e) {
            return "unparsed";
        }
    }

    private static String arg(String[] args, int index, String fallback) {
        return args != null && args.length > index && args[index] != null && !args[index].isBlank()
                ? args[index].strip() : fallback;
    }

    private static String value(Map<String, String> env, String key) {
        String found = env.get(key);
        return found == null ? "" : found.strip();
    }

    private static long longValue(Map<String, String> env, String key, long fallback) {
        String found = value(env, key);
        if (found.isBlank()) return fallback;
        try {
            return Long.parseLong(found);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static final class CallTrace {
        String error = "";
    }

    private record Unit(String id, String category, String text, List<String> expected, String disposition,
                        boolean withheld) {}
}
