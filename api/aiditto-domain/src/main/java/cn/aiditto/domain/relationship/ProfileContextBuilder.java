package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the internal profile context and the smaller document a model is allowed to see.
 * The model document has no user id and no account identifier.
 */
public final class ProfileContextBuilder {
    private ProfileContextBuilder() {}

    public record Statement(String kind, String text, boolean retracted, Long expiresAtMillis) {
        public Statement(String kind, String text, boolean retracted) {
            this(kind, text, retracted, null);
        }

        public Statement(String kind, String text) {
            this(kind, text, false, null);
        }
    }

    /**
     * A scenario can support a profile only when the server already knows an approved meaning.
     * The choice letter alone is not that meaning.
     */
    public record ScenarioChoice(String scenarioId, String scenarioVersion, String choiceKey,
                                 String approvedMeaning, String sourceReference) {
        public ScenarioChoice(String choiceKey) {
            this(null, null, choiceKey, null, null);
        }
    }

    public record Inputs(
            long userId,
            int sourceProfileVersion,
            long generationEpoch,
            boolean processingGranted,
            boolean modelExportGranted,
            Map<String, Object> answers,
            List<Statement> statements,
            List<ScenarioChoice> scenarios,
            long modelExportEpoch) {

        public Inputs {
            answers = answers == null ? Map.of() : Map.copyOf(answers);
            statements = statements == null ? List.of() : List.copyOf(statements);
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
            if (modelExportEpoch < 0) throw new IllegalArgumentException("modelExportEpoch");
        }

        public Inputs(long userId, int sourceProfileVersion, long generationEpoch, boolean processingGranted,
                      boolean modelExportGranted, Map<String, Object> answers, List<Statement> statements,
                      List<ScenarioChoice> scenarios) {
            this(userId, sourceProfileVersion, generationEpoch, processingGranted, modelExportGranted,
                    answers, statements, scenarios, 0L);
        }
    }

    /** Production context. Caller-supplied statements are ignored. */
    public static ProfileContext build(Inputs inputs) {
        return assemble(inputs, false);
    }

    /** Synthetic evaluation only. Production extraction does not call this. */
    public static ProfileContext buildEvaluation(Inputs inputs) {
        return assemble(inputs, true);
    }

    private static ProfileContext assemble(Inputs inputs, boolean evaluation) {
        if (inputs.userId() <= 0) throw new IllegalArgumentException("userId");
        if (!inputs.processingGranted()) throw new IllegalArgumentException("processing authorization");
        FeatureRegistry registry = FeatureRegistry.v1();
        RelationshipProfileSnapshot projected = RelationshipProfileProjector.project(
                inputs.userId(), inputs.answers(), registry);
        List<Raw> raw = new ArrayList<>();
        for (ProfileFeature feature : projected.self().features()) addFact(raw, Section.SELF, feature.featureCode(), feature.value());
        for (PreferenceTarget target : projected.preference().targets()) addFact(raw, Section.PREFERENCE, target.featureCode(), target.targetValue());
        if (evaluation) addEvaluationStatements(raw, inputs.statements());
        for (ScenarioChoice scenario : inputs.scenarios()) {
            if (!approvedSignal(scenario.approvedMeaning())) continue;
            if (scenario.choiceKey() == null || !scenario.choiceKey().matches("^[A-D]$")) continue;
            raw.add(new Raw("SCENARIO", null, scenario.approvedMeaning(), null, scenario.scenarioId(),
                    scenario.scenarioVersion(), scenario.choiceKey(), scenario.approvedMeaning(), scenario.sourceReference()));
        }
        raw.sort(Comparator.comparing(Raw::kind).thenComparing(Raw::featureCode, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Raw::value, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Raw::text, Comparator.nullsFirst(String::compareTo)));
        List<ProfileContext.Item> items = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Raw item = raw.get(i);
            items.add(new ProfileContext.Item("ev-" + (i + 1), item.kind(), item.featureCode(), item.value(), item.text(),
                    item.scenarioId(), item.scenarioVersion(), item.choiceKey(), item.approvedMeaning(), item.sourceReference()));
        }
        boolean export = inputs.modelExportGranted();
        String canonical = export ? canonical(items) : "{\"export\":\"withheld\"}";
        String hash = EvidenceIds.sha256(canonical + "\n" + inputs.sourceProfileVersion() + "\n"
                + inputs.generationEpoch() + "\n" + inputs.modelExportEpoch() + "\n"
                + ProfileExtractionPrompts.VERSION + "\n" + FeatureRegistry.VERSION);
        return new ProfileContext(inputs.userId(), inputs.sourceProfileVersion(), inputs.generationEpoch(),
                true, inputs.modelExportGranted(), inputs.modelExportEpoch(), FeatureRegistry.VERSION,
                ProfileExtractionPrompts.VERSION, List.copyOf(items), canonical, hash, inputs.answers(),
                evaluation ? inputs.statements() : List.of(), inputs.scenarios());
    }

    private static void addEvaluationStatements(List<Raw> raw, List<Statement> statements) {
        long now = System.currentTimeMillis();
        for (Statement statement : statements) {
            if (statement.retracted()) continue;
            if (statement.expiresAtMillis() != null && statement.expiresAtMillis() <= now) continue;
            if (!FreeTextPolicy.evaluationMayExpose(statement.text())) continue;
            if (!"SELF".equals(statement.kind()) && !"PREFERENCE".equals(statement.kind())) continue;
            raw.add(new Raw(statement.kind() + "_STATEMENT", null, null, statement.text().strip()));
        }
    }

    private static boolean approvedSignal(String meaning) {
        return "low".equals(meaning) || "steady".equals(meaning) || "high".equals(meaning);
    }

    private static void addFact(List<Raw> raw, Section section, String code, FeatureValue value) {
        String token = valueToken(value);
        if (token == null) return;
        if ("partner.gender".equals(code)) return;
        raw.add(new Raw("QUESTIONNAIRE", code, section.name() + ":" + token, null));
    }

    static String valueToken(FeatureValue value) {
        if (value instanceof FeatureValue.EnumOne one) return one.value();
        if (value instanceof FeatureValue.EnumMany many) return String.join(",", many.values());
        if (value instanceof FeatureValue.Bool bool) return Boolean.toString(bool.value());
        if (value instanceof FeatureValue.Unbounded) return "UNBOUNDED";
        if (value instanceof FeatureValue.Withheld) return "WITHHELD";
        return null;
    }

    private static String canonical(List<ProfileContext.Item> items) {
        StringBuilder out = new StringBuilder();
        out.append("{\"promptVersion\":").append(json(ProfileExtractionPrompts.VERSION));
        out.append(",\"registry\":").append(json(FeatureRegistry.VERSION));
        out.append(",\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            ProfileContext.Item item = items.get(i);
            if (i > 0) out.append(',');
            out.append("{\"ref\":").append(json(item.token()));
            out.append(",\"kind\":").append(json(item.kind()));
            if (item.featureCode() != null) out.append(",\"featureCode\":").append(json(item.featureCode()));
            if (item.value() != null) out.append(",\"value\":").append(json(item.value()));
            if (item.choiceKey() != null) out.append(",\"choice\":").append(json(item.choiceKey()));
            if (item.approvedMeaning() != null) out.append(",\"meaning\":").append(json(item.approvedMeaning()));
            if (item.text() != null && FreeTextPolicy.evaluationMayExpose(item.text())) {
                out.append(",\"text\":").append(json(item.text()));
            }
            out.append('}');
        }
        out.append("]}");
        return out.toString();
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Raw(String kind, String featureCode, String value, String text, String scenarioId,
                       String scenarioVersion, String choiceKey, String approvedMeaning, String sourceReference) {
        private Raw(String kind, String featureCode, String value, String text) {
            this(kind, featureCode, value, text, null, null, null, null, null);
        }
    }
}
