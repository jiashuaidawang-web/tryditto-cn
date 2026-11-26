package cn.aiditto.domain.relationship;

import java.util.ArrayList;
import java.util.List;

/** Stable JSON. Key order is alphabetical. Map iteration order is not part of the hash. */
public final class Canonical {
    private Canonical() {}

    public static String document(long userId, int schemaVersion, String featureRegistryVersion,
                                  String selfJson, String preferenceJson, String behaviorJson,
                                  String memoryJson, String evidenceJson) {
        return "{"
                + field("behavior", behaviorJson)
                + "," + field("evidence", evidenceJson)
                + "," + field("featureRegistryVersion", quote(featureRegistryVersion))
                + "," + field("memory", memoryJson)
                + "," + field("preference", preferenceJson)
                + "," + field("schemaVersion", Integer.toString(schemaVersion))
                + "," + field("self", selfJson)
                + "," + field("userId", Long.toString(userId))
                + "}";
    }

    public static String selfJson(List<ProfileFeature> features) {
        List<String> items = new ArrayList<>();
        for (ProfileFeature feature : features) {
            items.add("{"
                    + field("evidenceRefs", strings(feature.evidenceRefs()))
                    + "," + field("featureCode", quote(feature.featureCode()))
                    + "," + field("status", quote(feature.status().name()))
                    + valueField(feature.value())
                    + "}");
        }
        return array(items);
    }

    public static String preferenceJson(List<PreferenceTarget> targets) {
        List<String> items = new ArrayList<>();
        for (PreferenceTarget target : targets) {
            List<String> fields = new ArrayList<>();
            if (target.confidence() != null) fields.add(field("confidence", number(target.confidence())));
            fields.add(field("constraintType", quote(target.constraintType().name())));
            fields.add(field("evidenceRefs", strings(target.evidenceRefs())));
            fields.add(field("featureCode", quote(target.featureCode())));
            if (target.importance() != null) fields.add(field("importance", quote(target.importance().name())));
            if (target.operator() != null) fields.add(field("operator", quote(target.operator().name())));
            fields.add(field("status", quote(target.status().name())));
            if (target.targetValue() != null) fields.add(field("targetValue", value(target.targetValue())));
            items.add("{" + String.join(",", fields) + "}");
        }
        return array(items);
    }

    public static String behaviorJson(List<BehaviorRecord> records) {
        List<String> items = new ArrayList<>();
        for (BehaviorRecord record : records) {
            items.add("{"
                    + field("evidenceRefs", strings(record.evidenceRefs()))
                    + "," + field("featureCode", quote(record.featureCode()))
                    + "," + field("kind", quote(record.kind().name()))
                    + "," + field("status", quote(record.status().name()))
                    + valueField(record.value())
                    + "}");
        }
        return array(items);
    }

    public static String memoryJson(List<MemoryObservation> observations) {
        List<String> items = new ArrayList<>();
        for (MemoryObservation observation : observations) {
            items.add("{"
                    + field("confidence", number(observation.confidence()))
                    + "," + field("evidenceRefs", strings(observation.evidenceRefs()))
                    + "," + field("featureCode", quote(observation.featureCode()))
                    + "," + field("observationId", quote(observation.observationId()))
                    + "," + field("observationType", quote(observation.observationType()))
                    + "," + field("polarity", quote(observation.polarity().name()))
                    + "," + field("sourceEventRef", quote(observation.sourceEventRef()))
                    + "," + field("status", quote(observation.status().name()))
                    + "}");
        }
        return array(items);
    }

    public static String evidenceJson(List<ProfileEvidence> evidence) {
        List<String> items = new ArrayList<>();
        for (ProfileEvidence item : evidence) {
            List<String> fields = new ArrayList<>();
            if (item.behaviorKind() != null) fields.add(field("behaviorKind", quote(item.behaviorKind().name())));
            fields.add(field("claim", value(item.claim())));
            fields.add(field("confidence", number(item.confidence())));
            fields.add(field("consentScope", quote(item.consentScope())));
            if (item.constraintType() != null) fields.add(field("constraintType", quote(item.constraintType().name())));
            fields.add(field("evidenceId", quote(item.evidenceId())));
            fields.add(field("featureCode", quote(item.featureCode())));
            if (item.importance() != null) fields.add(field("importance", quote(item.importance().name())));
            if (item.operator() != null) fields.add(field("operator", quote(item.operator().name())));
            if (item.polarity() != null) fields.add(field("polarity", quote(item.polarity().name())));
            fields.add(field("section", quote(item.section().name())));
            fields.add(field("sourceRef", quote(item.sourceRef())));
            fields.add(field("sourceType", quote(item.sourceType().name())));
            fields.add(field("sourceVersion", quote(item.sourceVersion())));
            fields.add(field("status", quote(item.status().name())));
            fields.add(field("subjectUserId", Long.toString(item.subjectUserId())));
            fields.add(field("visibility", quote(item.visibility().name())));
            items.add("{" + String.join(",", fields) + "}");
        }
        return array(items);
    }

    private static String valueField(FeatureValue value) {
        if (value == null) return "";
        return "," + field("value", value(value));
    }

    public static String value(FeatureValue value) {
        if (value instanceof FeatureValue.Bool bool) {
            return "{" + field("type", quote("BOOLEAN")) + "," + field("value", bool.value() ? "true" : "false") + "}";
        }
        if (value instanceof FeatureValue.EnumOne one) {
            return "{" + field("type", quote("ENUM")) + "," + field("value", quote(one.value())) + "}";
        }
        if (value instanceof FeatureValue.EnumMany many) {
            return "{" + field("type", quote("ENUM_SET")) + "," + field("values", strings(many.values())) + "}";
        }
        if (value instanceof FeatureValue.Num num) {
            return "{" + field("type", quote("NUMBER")) + "," + field("value", number(num.value())) + "}";
        }
        if (value instanceof FeatureValue.Span span) {
            return "{" + field("max", number(span.max())) + "," + field("min", number(span.min())) + "," + field("type", quote("RANGE")) + "}";
        }
        if (value instanceof FeatureValue.Text text) {
            return "{" + field("type", quote("TEXT")) + "," + field("value", quote(text.value())) + "}";
        }
        if (value instanceof FeatureValue.Unbounded) return "{" + field("type", quote("UNBOUNDED")) + "}";
        if (value instanceof FeatureValue.Withheld) return "{" + field("type", quote("WITHHELD")) + "}";
        if (value instanceof FeatureValue.ControlledRef ref) {
            return "{" + field("ref", quote(ref.ref())) + "," + field("type", quote("CONTROLLED_REF")) + "}";
        }
        throw new IllegalArgumentException("value");
    }

    private static String field(String name, String rawJson) {
        return quote(name) + ":" + rawJson;
    }

    private static String array(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    private static String strings(List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) quoted.add(quote(value));
        return array(quoted);
    }

    static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("number");
        if (value == 0.0d) return "0";
        String text = Double.toString(value);
        if (text.endsWith(".0")) return text.substring(0, text.length() - 2);
        return text;
    }

    static String quote(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}
