package cn.aiditto.relationship;

import cn.aiditto.ai.LlmCallLogger;
import cn.aiditto.ai.LlmException;
import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmProperties;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.ai.OpenAiCompatModel;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.ProfileContext;
import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Statement;
import cn.aiditto.domain.relationship.ProfileEvidence;
import cn.aiditto.domain.relationship.ProfileExtractionPrompts;
import cn.aiditto.domain.relationship.ProfileExtractionValidator;
import cn.aiditto.domain.relationship.ProfileLiveEval;
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

/**
 * Offline experiment driver for the frozen live-profile-v1 set.
 * It reuses {@link LlmGateway}. It does not read a production database or enable profile processing.
 * A missing key stops the process before any model call. Fallback configuration stops it too.
 */
public final class ProfileLiveEvalRunner {
    public static final String DATASET_HASH = "4d4c9b0759dc91a2d872829b756a2d332fd0e9dbb86fd43d217f0a5ab99ddc1c";
    public static final String PROMPT_HASH = "bfbbcf6903ba5cac0c31cd71c8b7375d10ef53d835b86a7a2601b52a0a510195";
    public static final List<String> SMOKE_IDS = List.of("L09", "L06", "L25", "L48", "L55");

    private static final FeatureRegistry REGISTRY = FeatureRegistry.v1();
    private static final long SYNTHETIC_USER = 91_001L;

    private ProfileLiveEvalRunner() {}

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.out);
        if (code != 0) System.exit(code);
    }

    public static int run(String[] args, Map<String, String> env, PrintStream out) {
        if (!hashesMatch()) {
            out.println("NOT_RUN hash mismatch. The frozen dataset or prompt changed.");
            out.println("dataset=" + ProfileLiveEval.datasetHash());
            out.println("prompt=" + ProfileExtractionPrompts.digest());
            return 3;
        }
        String mode = arg(args, 0, "preflight");
        if (!"smoke".equals(mode) && !"full".equals(mode) && !"preflight".equals(mode)) {
            out.println("usage: ProfileLiveEvalRunner preflight|smoke|full");
            return 2;
        }
        if (!liveConfigured(env)) {
            out.println("NOT_RUN");
            out.println(ProfileLiveEval.BLOCKER);
            printRequiredConfig(out);
            return 2;
        }
        if (fallbackConfigured(env)) {
            out.println("NOT_RUN fallback is set. This experiment refuses fallback results.");
            return 2;
        }
        if ("preflight".equals(mode)) {
            printPreflight(env, out);
            return 0;
        }
        CallTrace trace = new CallTrace();
        LlmGateway gateway;
        try {
            gateway = gateway(env, trace);
        } catch (IllegalStateException e) {
            out.println("NOT_RUN fallback is set. This experiment refuses fallback results.");
            return 2;
        }
        List<ProfileLiveEval.Sample> selected = "smoke".equals(mode) ? smokeSamples() : ProfileLiveEval.samples();
        List<SampleRecord> records = new ArrayList<>();
        String secret = value(env, "LLM_PRIMARY_API_KEY");
        int calls = 0;
        for (ProfileLiveEval.Sample sample : selected) {
            trace.error = "";
            SampleRecord record = one(sample, gateway, trace, secret);
            records.add(record);
            calls += record.modelCalled() ? 1 : 0;
            if (record.modelCalled() && (record.model() == null || record.model().startsWith("mock"))) {
                out.println("NOT_RUN refused a mock or fallback result for " + sample.id());
                writeReport(env, mode, records, secret, out);
                return 4;
            }
            out.println(sample.id() + " lane=" + record.lane() + " called=" + record.modelCalled()
                    + " status=" + record.status() + " class=" + record.failureClass()
                    + " parsed=" + record.parsed() + " latencyMs=" + record.latencyMs()
                    + " tokens=" + record.inputTokens() + "/" + record.outputTokens()
                    + (record.error().isBlank() ? "" : " error=" + record.error()));
        }
        out.println("samples=" + selected.size() + " modelCalls=" + calls + " notSent=" + countNotSent(records));
        writeReport(env, mode, records, secret, out);
        if ("smoke".equals(mode)) {
            for (SampleRecord record : records) {
                if (!record.modelCalled() || !"called".equals(record.status()) || !record.parsed()) {
                    out.println("SMOKE_FAILED " + record.sampleId() + " status=" + record.status()
                            + " error=" + record.error());
                    return 5;
                }
            }
        }
        return 0;
    }

    static boolean hashesMatch() {
        return DATASET_HASH.equals(ProfileLiveEval.datasetHash())
                && PROMPT_HASH.equals(ProfileExtractionPrompts.digest());
    }

    static boolean liveConfigured(Map<String, String> env) {
        return "live".equalsIgnoreCase(value(env, "LLM_MODE"))
                && !value(env, "LLM_PRIMARY_BASE_URL").isBlank()
                && !value(env, "LLM_PRIMARY_API_KEY").isBlank();
    }

    static boolean fallbackConfigured(Map<String, String> env) {
        return !value(env, "LLM_FALLBACK_BASE_URL").isBlank()
                || !value(env, "LLM_FALLBACK_API_KEY").isBlank();
    }

    static List<ProfileLiveEval.Sample> smokeSamples() {
        Map<String, ProfileLiveEval.Sample> byId = new LinkedHashMap<>();
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) byId.put(sample.id(), sample);
        List<ProfileLiveEval.Sample> out = new ArrayList<>();
        for (String id : SMOKE_IDS) out.add(byId.get(id));
        return List.copyOf(out);
    }

    static ProfileContext production(ProfileLiveEval.Sample sample) {
        return ProfileContextBuilder.build(inputs(List.of(new Statement("SELF", sample.text()))));
    }

    static ProfileContext synthetic(ProfileLiveEval.Sample sample) {
        return ProfileContextBuilder.buildEvaluation(inputs(List.of(
                new Statement("SELF", sample.text()),
                new Statement("PREFERENCE", sample.text()))));
    }

    static boolean privacyLane(ProfileLiveEval.Sample sample) {
        return "private-boundary".equals(sample.category());
    }

    static SampleRecord one(ProfileLiveEval.Sample sample, LlmGateway gateway) {
        return one(sample, gateway, new CallTrace(), "");
    }

    static SampleRecord one(ProfileLiveEval.Sample sample, LlmGateway gateway, CallTrace trace, String secret) {
        ProfileContext production = production(sample);
        boolean leaked = production.exportCanonical().contains(sample.text());
        String expected = sample.expectedKey() == null ? "" : sample.expectedKey();
        if (privacyLane(sample)) {
            return record(sample, "privacy-policy", false, "not-sent", null, false,
                    List.of(), List.of(), List.of(), List.of(), leaked, 0, null, null, "not-called",
                    "", List.of(), 0, 0, 0, 0, 0, "", "8");
        }
        ProfileContext synthetic = synthetic(sample);
        LlmResult result = gateway.call(new LlmRequest(ProfileExtractionPrompts.CALL_POINT,
                ProfileExtractionPrompts.VERSION, ProfileExtractionPrompts.system(),
                ProfileExtractionPrompts.userPayload(synthetic.exportCanonical()),
                1200, 0.0, true, true));
        String model = result.model();
        if (result.degraded() || model == null || model.startsWith("mock")) {
            String status = result.degraded() ? "degraded" : "refused";
            String error = redact(trace.error, secret);
            return record(sample, "semantic", true, status, model, false,
                    List.of(), List.of(), List.of(), List.of(), leaked,
                    result.latencyMs(), result.inputTokens(), result.outputTokens(),
                    result.degraded() ? "degraded" : "mock-or-empty",
                    redact(truncate(result.content()), secret), List.of(), 0, 0, 0, 0, 0, error,
                    classifyFailure(status, false, expected, List.of(), List.of(), List.of(), 0, 0, error));
        }
        ProfileExtractionParser.Parse parsed = ProfileExtractionParser.parse(result.content());
        List<ExtractedFeature> features = parsed.structured() ? parsed.features() : List.of();
        ProfileExtractionValidator.Result checked = ProfileExtractionValidator.check(synthetic, features, REGISTRY);
        List<String> rawKeys = keys(features);
        List<String> accepted = acceptedKeys(checked);
        int ungrounded = ungrounded(synthetic, features);
        int hardAccepted = hardAccepted(features, accepted);
        int ungroundedAccepted = ungroundedAccepted(synthetic, features, accepted);
        String failure = classifyFailure("called", parsed.structured(), expected, rawKeys, accepted,
                reasons(checked), ungrounded, checked.invalidRefs(), "");
        return record(sample, "semantic", true, "called", model, parsed.structured(),
                rawKeys, reasons(checked), pending(checked), supported(checked), leaked,
                result.latencyMs(), result.inputTokens(), result.outputTokens(), "primary",
                redact(truncate(result.content()), secret), accepted, checked.hardRequests(), hardAccepted,
                ungrounded, ungroundedAccepted, checked.invalidRefs(), "", failure);
    }

    /**
     * A ratio with a zero denominator stays empty. It is not reported as 1.
     * This rule is fixed before any live run.
     */
    public static Double ratio(int numerator, int denominator) {
        if (denominator == 0) return null;
        return (double) numerator / denominator;
    }

    static int closedPhraseCeiling() {
        int supported = 0;
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) {
            if (sample.expectedKey() == null || sample.expectedKey().isBlank()) continue;
            if (supportsGold(sample)) supported++;
        }
        return supported;
    }

    static int positiveLabels() {
        int n = 0;
        for (ProfileLiveEval.Sample sample : ProfileLiveEval.samples()) {
            if (sample.expectedKey() != null && !sample.expectedKey().isBlank()) n++;
        }
        return n;
    }

    static boolean supportsGold(ProfileLiveEval.Sample sample) {
        String[] parts = sample.expectedKey().split("\\|", 3);
        if (parts.length < 3) return false;
        ProfileContext context = synthetic(sample);
        String kind = "PREFERENCE".equals(parts[0]) ? "PREFERENCE_STATEMENT" : "SELF_STATEMENT";
        String token = context.items().stream()
                .filter(item -> kind.equals(item.kind()) && sample.text().equals(item.text()))
                .map(ProfileContext.Item::token).findFirst().orElse("ev-missing");
        ExtractedFeature feature = new ExtractedFeature(parts[0], parts[1], parts[2], "EQ", "NONE",
                List.of(token), 0.6, "STATED", "", false);
        ProfileExtractionValidator.Result result = ProfileExtractionValidator.check(context, List.of(feature), REGISTRY);
        return result.verdicts().stream().anyMatch(item -> item.support() == SemanticSupport.SEMANTIC_SUPPORTED)
                && !result.accepted().isEmpty();
    }

    static String classifyFailure(String status, boolean parsed, String expected, List<String> rawKeys,
                                  List<String> accepted, List<String> reasons, int ungrounded, int invalidRefs,
                                  String error) {
        if ("8".equals(status) || "not-sent".equals(status)) return "8";
        if ("degraded".equals(status) || "refused".equals(status)) {
            String kind = error == null ? "" : error;
            if (kind.contains("VALIDATION") || kind.contains("BAD_RESPONSE") || kind.contains("EMPTY_CONTENT")) return "9";
            return "10";
        }
        if (!parsed) return "9";
        boolean gold = expected != null && !expected.isBlank();
        boolean rawHit = gold && rawKeys.contains(expected);
        boolean acceptedHit = gold && accepted.contains(expected);
        if (gold && rawHit && !acceptedHit) {
            if (containsUnknown(reasons)) return "2";
            if (ungrounded > 0 || invalidRefs > 0 || containsRef(reasons)) return "4";
            return "5";
        }
        if (containsUnknown(reasons) && gold && !rawHit) return "2";
        if ((ungrounded > 0 || invalidRefs > 0) && gold && !rawHit && !rawKeys.isEmpty()) return "4";
        if (gold && !rawHit) return "1";
        if (!gold && !rawKeys.isEmpty()) return "1";
        if (gold && rawKeys.stream().anyMatch(key -> !key.equals(expected))) return "1";
        return "ok";
    }

    public static GroupScore scoreGroup(List<SampleRecord> records, boolean accepted) {
        int predicted = 0;
        int hits = 0;
        int positives = 0;
        int recalled = 0;
        int confusion = 0;
        int confusionBase = 0;
        int ungrounded = 0;
        int rawCount = 0;
        int invalidRefs = 0;
        int hardRequests = 0;
        int hardAccepted = 0;
        int pending = 0;
        int rejected = 0;
        int supported = 0;
        for (SampleRecord record : records) {
            if (!record.modelCalled()) continue;
            List<String> keys = accepted ? record.acceptedKeys() : record.rawKeys();
            String expected = record.expectedKey() == null ? "" : record.expectedKey();
            boolean gold = !expected.isBlank();
            if (gold) positives++;
            if (gold && keys.contains(expected)) recalled++;
            for (String key : keys) {
                predicted++;
                if (gold && key.equals(expected)) hits++;
                if (gold) {
                    confusionBase++;
                    if (confused(expected, key)) confusion++;
                }
            }
            if (!accepted) {
                ungrounded += record.ungroundedRaw();
                rawCount += record.rawKeys().size();
                invalidRefs += record.invalidRefs();
                hardRequests += record.hardRequests();
            } else {
                pending += record.pending().size();
                rejected += record.rejectionReasons().size();
                supported += record.acceptedKeys().size();
                hardAccepted += record.hardAccepted();
                ungrounded += record.ungroundedAccepted();
                rawCount += record.acceptedKeys().size();
            }
        }
        Double precision = ratio(hits, predicted);
        Double recall = ratio(recalled, positives);
        return new GroupScore(predicted, hits, precision, positives, recalled, recall, f1(precision, recall),
                confusion, ratio(confusion, confusionBase), ungrounded, ratio(ungrounded, rawCount),
                invalidRefs, ratio(invalidRefs, rawCount), hardRequests, hardAccepted, pending, rejected, supported);
    }

    static Double f1(Double precision, Double recall) {
        if (precision == null || recall == null) return null;
        if (precision + recall == 0) return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        if (index < 0) index = 0;
        if (index >= sorted.size()) index = sorted.size() - 1;
        return sorted.get(index);
    }

    static String redact(String text, String secret) {
        if (text == null) return "";
        String out = text;
        if (secret != null && secret.length() >= 8) out = out.replace(secret, "[redacted]");
        out = out.replaceAll("(?i)bearer\\s+\\S+", "Bearer [redacted]");
        out = out.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "[redacted]");
        return out;
    }

    private static SampleRecord record(ProfileLiveEval.Sample sample, String lane, boolean modelCalled, String status,
                                       String model, boolean parsed, List<String> rawKeys, List<String> rejectionReasons,
                                       List<String> pending, List<String> supported, boolean productionLeak,
                                       long latencyMs, Integer inputTokens, Integer outputTokens, String fallbackStatus,
                                       String rawContent, List<String> acceptedKeys, int hardRequests, int hardAccepted,
                                       int ungroundedRaw, int ungroundedAccepted, int invalidRefs, String error,
                                       String failureClass) {
        return new SampleRecord(sample.id(), lane, modelCalled, status, model, parsed, rawKeys, rejectionReasons,
                pending, supported, productionLeak, latencyMs, inputTokens, outputTokens, fallbackStatus,
                sample.category(), sample.expectedKey() == null ? "" : sample.expectedKey(), rawContent, acceptedKeys,
                hardRequests, hardAccepted, ungroundedRaw, ungroundedAccepted, invalidRefs, error, failureClass);
    }

    private static void printPreflight(Map<String, String> env, PrintStream out) {
        out.println("READY");
        out.println("mode=live");
        out.println("provider=openai-compatible");
        out.println("baseHost=" + hostOf(value(env, "LLM_PRIMARY_BASE_URL")));
        out.println("apiKey=present");
        out.println("model=" + value(env, "LLM_PRIMARY_MODEL"));
        out.println("fallback=disabled");
        out.println("dataset=" + DATASET_HASH);
        out.println("prompt=" + PROMPT_HASH);
        out.println("database=not-connected");
        out.println("samplesSent=0");
    }

    private static void writeReport(Map<String, String> env, String mode, List<SampleRecord> records,
                                    String secret, PrintStream out) {
        String dir = value(env, "LIVE_EVAL_OUT");
        if (dir.isBlank()) return;
        try {
            Path root = Path.of(dir);
            Files.createDirectories(root);
            GroupScore raw = scoreGroup(records, false);
            GroupScore checked = scoreGroup(records, true);
            int calls = 0;
            int structured = 0;
            int degraded = 0;
            int notSent = 0;
            int leaks = 0;
            long input = 0;
            long outputTokens = 0;
            int missingTokens = 0;
            List<Long> latency = new ArrayList<>();
            for (SampleRecord record : records) {
                if (record.productionLeak()) leaks++;
                if (!record.modelCalled()) {
                    notSent++;
                    continue;
                }
                calls++;
                if (record.parsed() && "called".equals(record.status())) structured++;
                if ("degraded".equals(record.status()) || "refused".equals(record.status())) degraded++;
                latency.add(record.latencyMs());
                if (record.inputTokens() == null || record.outputTokens() == null) missingTokens++;
                else {
                    input += record.inputTokens();
                    outputTokens += record.outputTokens();
                }
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("mode", mode);
            summary.put("model", value(env, "LLM_PRIMARY_MODEL"));
            summary.put("dataset", DATASET_HASH);
            summary.put("prompt", PROMPT_HASH);
            summary.put("samples", records.size());
            summary.put("modelCalls", calls);
            summary.put("notSent", notSent);
            summary.put("notSentReason", "private-boundary sentences stay on the production path and are not sent");
            summary.put("structuredSuccess", structured);
            summary.put("structuredRate", ratio(structured, calls));
            summary.put("degraded", degraded);
            summary.put("degradedRate", ratio(degraded, calls));
            summary.put("latencyP50Ms", percentile(latency, 0.50));
            summary.put("latencyP95Ms", percentile(latency, 0.95));
            summary.put("inputTokens", input);
            summary.put("outputTokens", outputTokens);
            summary.put("missingTokenCalls", missingTokens);
            summary.put("cost", "adapter-did-not-provide");
            summary.put("productionLeaks", leaks);
            summary.put("closedPhraseCeiling", "4/6");
            summary.put("raw", raw.toMap());
            summary.put("validated", checked.toMap());
            String json = redact(Json.write(summary), secret);
            Files.writeString(root.resolve("summary-" + mode + ".json"), json + "\n", StandardCharsets.UTF_8);
            StringBuilder lines = new StringBuilder();
            for (SampleRecord record : records) {
                lines.append(redact(Json.write(record.toMap()), secret)).append('\n');
            }
            Files.writeString(root.resolve("samples-" + mode + ".jsonl"), lines.toString(), StandardCharsets.UTF_8);
            out.println("report=" + root.toAbsolutePath());
        } catch (IOException e) {
            out.println("REPORT_WRITE_FAILED " + e.getClass().getSimpleName());
        }
    }

    private static int countNotSent(List<SampleRecord> records) {
        int n = 0;
        for (SampleRecord record : records) if (!record.modelCalled()) n++;
        return n;
    }

    private static boolean confused(String expected, String predicted) {
        String[] left = expected.split("\\|", 3);
        String[] right = predicted.split("\\|", 3);
        return left.length >= 2 && right.length >= 2 && left[1].equals(right[1]) && !left[0].equals(right[0]);
    }

    private static boolean containsUnknown(List<String> reasons) {
        for (String reason : reasons) if (reason.contains("unknown feature")) return true;
        return false;
    }

    private static boolean containsRef(List<String> reasons) {
        for (String reason : reasons) {
            if (reason.contains("invalid-ref") || reason.contains("ungrounded")) return true;
        }
        return false;
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "unparsed" : host;
        } catch (RuntimeException e) {
            return "unparsed";
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= 6000 ? text : text.substring(0, 6000) + " [truncated]";
    }

    private static ProfileContextBuilder.Inputs inputs(List<Statement> statements) {
        return new ProfileContextBuilder.Inputs(SYNTHETIC_USER, 1, 1, true, true,
                Map.of("hobbies", List.of("reading")), statements, List.of(), 1L);
    }

    private static LlmGateway gateway(Map<String, String> env, CallTrace trace) {
        LlmProperties props = new LlmProperties("live", value(env, "LLM_PRIMARY_BASE_URL"),
                value(env, "LLM_PRIMARY_API_KEY"), value(env, "LLM_PRIMARY_MODEL"),
                "", "", "deepseek-flash", 2, longValue(env, "LLM_TIMEOUT_MS", 45_000L),
                0L, 5, value(env, "LLM_PRIMARY_EXTRA_BODY"), "");
        if (props.fallbackAvailable()) {
            throw new IllegalStateException("fallback key must stay empty for this experiment");
        }
        LlmModel primary = new OpenAiCompatModel(props.primaryBaseUrl(), props.primaryApiKey(),
                props.primaryModel(), props.timeoutMs(), props.primaryExtraBodyMap());
        LlmModel recording = new LlmModel() {
            @Override
            public String name() {
                return primary.name();
            }

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

    private static int ungrounded(ProfileContext context, List<ExtractedFeature> features) {
        int count = 0;
        for (ExtractedFeature feature : features) {
            if (feature.evidenceRefs().isEmpty()) {
                count++;
                continue;
            }
            for (String ref : feature.evidenceRefs()) {
                if (context.item(ref) == null) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static int ungroundedAccepted(ProfileContext context, List<ExtractedFeature> features, List<String> accepted) {
        int count = 0;
        for (ExtractedFeature feature : features) {
            if (!accepted.contains(feature.section() + "|" + feature.featureCode() + "|" + feature.proposedValue())) {
                continue;
            }
            if (feature.evidenceRefs().isEmpty()) {
                count++;
                continue;
            }
            for (String ref : feature.evidenceRefs()) {
                if (context.item(ref) == null) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static int hardAccepted(List<ExtractedFeature> features, List<String> accepted) {
        int count = 0;
        for (ExtractedFeature feature : features) {
            if (feature.hardConstraint() && accepted.contains(feature.section() + "|" + feature.featureCode()
                    + "|" + feature.proposedValue())) {
                count++;
            }
        }
        return count;
    }

    private static List<String> keys(List<ExtractedFeature> features) {
        List<String> keys = new ArrayList<>();
        if (features == null) return keys;
        for (ExtractedFeature feature : features) {
            keys.add(feature.section() + "|" + feature.featureCode() + "|" + feature.proposedValue());
        }
        return keys;
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

    private static List<String> reasons(ProfileExtractionValidator.Result result) {
        return result.rejected().stream().map(item -> item.featureCode() + ":" + item.reason()).toList();
    }

    private static List<String> pending(ProfileExtractionValidator.Result result) {
        return result.verdicts().stream()
                .filter(item -> item.support() == SemanticSupport.SEMANTIC_SUPPORT_PENDING)
                .map(item -> item.featureCode() + ":" + item.support().name()).toList();
    }

    private static List<String> supported(ProfileExtractionValidator.Result result) {
        return result.accepted().stream()
                .map(item -> item.section().name() + "|" + item.featureCode()).distinct().toList();
    }

    private static void printRequiredConfig(PrintStream out) {
        out.println("Required non-production environment:");
        out.println("LLM_MODE=live");
        out.println("LLM_PRIMARY_BASE_URL=<OpenAI-compatible base URL, no trailing path>");
        out.println("LLM_PRIMARY_API_KEY=<non-production key, do not commit or print it>");
        out.println("LLM_PRIMARY_MODEL=<model id; default name in LlmProperties is LongCat-2.5>");
        out.println("Leave LLM_FALLBACK_API_KEY and LLM_FALLBACK_BASE_URL empty.");
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

    public record GroupScore(int predicted, int hits, Double precision, int positives, int recalled, Double recall,
                             Double f1, int confusion, Double confusionRate, int ungrounded, Double ungroundedRate,
                             int invalidRefs, Double invalidRefRate, int hardRequests, int hardAccepted,
                             int pending, int rejected, int supported) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("predicted", predicted);
            map.put("hits", hits);
            map.put("precision", precision);
            map.put("positives", positives);
            map.put("recalled", recalled);
            map.put("recall", recall);
            map.put("f1", f1);
            map.put("confusion", confusion);
            map.put("confusionRate", confusionRate);
            map.put("ungrounded", ungrounded);
            map.put("ungroundedRate", ungroundedRate);
            map.put("invalidRefs", invalidRefs);
            map.put("invalidRefRate", invalidRefRate);
            map.put("hardRequests", hardRequests);
            map.put("hardAccepted", hardAccepted);
            map.put("pending", pending);
            map.put("rejected", rejected);
            map.put("supported", supported);
            return map;
        }
    }

    public record SampleRecord(String sampleId, String lane, boolean modelCalled, String status, String model,
                               boolean parsed, List<String> rawKeys, List<String> rejectionReasons,
                               List<String> pending, List<String> supported, boolean productionLeak,
                               long latencyMs, Integer inputTokens, Integer outputTokens, String fallbackStatus,
                               String category, String expectedKey, String rawContent, List<String> acceptedKeys,
                               int hardRequests, int hardAccepted, int ungroundedRaw, int ungroundedAccepted,
                               int invalidRefs, String error, String failureClass) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sampleId", sampleId);
            map.put("category", category);
            map.put("expectedLabel", expectedKey);
            map.put("rawOutput", rawContent);
            map.put("rawKeys", rawKeys);
            map.put("validatedOutput", acceptedKeys);
            map.put("pending", pending);
            map.put("rejectionReason", rejectionReasons);
            map.put("latencyMs", latencyMs);
            map.put("inputTokens", inputTokens);
            map.put("outputTokens", outputTokens);
            map.put("called", modelCalled);
            map.put("status", status);
            map.put("model", model);
            map.put("fallbackStatus", fallbackStatus);
            map.put("failureClass", failureClass);
            map.put("hardRequests", hardRequests);
            map.put("hardAccepted", hardAccepted);
            map.put("ungroundedRaw", ungroundedRaw);
            map.put("ungroundedAccepted", ungroundedAccepted);
            map.put("invalidRefs", invalidRefs);
            map.put("productionLeak", productionLeak);
            map.put("error", error);
            return map;
        }
    }
}
