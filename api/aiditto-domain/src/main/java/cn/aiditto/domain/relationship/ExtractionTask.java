package cn.aiditto.domain.relationship;

import java.util.List;

/**
 * Separates deterministic background from the statements a model may analyze.
 * Known facts are omitted unless the caller explicitly asks to send them.
 */
public final class ExtractionTask {
    private ExtractionTask() {}

    public static String payload(ProfileContext context, List<FeatureContract> contracts, boolean sendKnownFacts) {
        StringBuilder out = new StringBuilder();
        out.append("{\"knownFacts\":[");
        boolean known = false;
        if (sendKnownFacts) {
            for (ProfileContext.Item item : context.items()) {
                if (!"QUESTIONNAIRE".equals(item.kind())) continue;
                if (known) out.append(',');
                known = true;
                out.append("{\"ref\":").append(json(item.token()));
                out.append(",\"featureCode\":").append(json(item.featureCode()));
                out.append(",\"value\":").append(json(item.value()));
                out.append('}');
            }
        }
        out.append("],\"extractionTargets\":[");
        for (int i = 0; i < contracts.size(); i++) {
            if (i > 0) out.append(',');
            out.append(json(contracts.get(i).featureCode()));
        }
        out.append("],\"sourceStatements\":[");
        boolean statement = false;
        for (ProfileContext.Item item : context.items()) {
            if (item.text() == null || item.text().isBlank()) continue;
            if ("QUESTIONNAIRE".equals(item.kind())) continue;
            if (statement) out.append(',');
            statement = true;
            out.append("{\"ref\":").append(json(item.token()));
            out.append(",\"kind\":").append(json(item.kind()));
            out.append(",\"text\":").append(json(item.text()));
            out.append('}');
        }
        out.append("]}");
        return out.toString();
    }

    private static String json(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
