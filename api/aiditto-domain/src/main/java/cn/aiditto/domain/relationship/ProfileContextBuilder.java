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

    public record Statement(String kind, String text, boolean retracted) {
        public Statement(String kind, String text) {
            this(kind, text, false);
        }
    }

    public record ScenarioChoice(String choiceKey) {}

    public record Inputs(
            long userId,
            int sourceProfileVersion,
            long generationEpoch,
            boolean processingGranted,
            boolean modelExportGranted,
            Map<String, Object> answers,
            List<Statement> statements,
            List<ScenarioChoice> scenarios) {

        public Inputs {
            answers = answers == null ? Map.of() : Map.copyOf(answers);
            statements = statements == null ? List.of() : List.copyOf(statements);
            scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        }
    }

    public static ProfileContext build(Inputs inputs) {
        if (inputs.userId() <= 0) throw new IllegalArgumentException("userId");
        if (!inputs.processingGranted()) throw new IllegalArgumentException("processing authorization");
        FeatureRegistry registry = FeatureRegistry.v1();
        RelationshipProfileSnapshot projected = RelationshipProfileProjector.project(
                inputs.userId(), inputs.answers(), registry);
        List<Raw> raw = new ArrayList<>();
        for (ProfileFeature feature : projected.self().features()) addFact(raw, Section.SELF, feature.featureCode(), feature.value());
        for (PreferenceTarget target : projected.preference().targets()) addFact(raw, Section.PREFERENCE, target.featureCode(), target.targetValue());
        for (Statement statement : inputs.statements()) {
            if (statement.retracted() || !FreeTextPolicy.exportable(statement.text())) continue;
            if (!"SELF".equals(statement.kind()) && !"PREFERENCE".equals(statement.kind())) continue;
            raw.add(new Raw(statement.kind() + "_STATEMENT", null, null, statement.text().strip()));
        }
        for (ScenarioChoice scenario : inputs.scenarios()) {
            if (scenario.choiceKey() == null || !scenario.choiceKey().matches("^[A-D]$")) continue;
            raw.add(new Raw("SCENARIO", null, scenario.choiceKey(), null));
        }
        raw.sort(Comparator.comparing(Raw::kind).thenComparing(Raw::featureCode, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Raw::value, Comparator.nullsFirst(String::compareTo))
                .thenComparing(Raw::text, Comparator.nullsFirst(String::compareTo)));
        List<ProfileContext.Item> items = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Raw item = raw.get(i);
            items.add(new ProfileContext.Item("ev-" + (i + 1), item.kind(), item.featureCode(), item.value(), item.text()));
        }
        boolean export = inputs.modelExportGranted();
        String canonical = export ? canonical(items) : "{\"export\":\"withheld\"}";
        String hash = EvidenceIds.sha256(canonical + "\n" + inputs.sourceProfileVersion() + "\n"
                + inputs.generationEpoch() + "\n" + ProfileExtractionPrompts.VERSION + "\n" + FeatureRegistry.VERSION);
        return new ProfileContext(inputs.userId(), inputs.sourceProfileVersion(), inputs.generationEpoch(),
                true, inputs.modelExportGranted(), FeatureRegistry.VERSION, ProfileExtractionPrompts.VERSION,
                List.copyOf(items), canonical, hash, inputs.answers(), inputs.statements(), inputs.scenarios());
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
            if (item.text() != null) out.append(",\"text\":").append(json(item.text()));
            out.append('}');
        }
        out.append("]}");
        return out.toString();
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Raw(String kind, String featureCode, String value, String text) {}
}
