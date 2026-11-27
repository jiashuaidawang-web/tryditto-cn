package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.CitedFeature;
import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Turns model JSON into candidates. Invalid JSON is a failed parse, not a profile. */
public final class ProfileExtractionParser {
    private ProfileExtractionParser() {}

    public record TaskParse(boolean structured, List<CitedFeature> cited) {
        public static TaskParse failed() {
            return new TaskParse(false, List.of());
        }
    }

    public record Parse(boolean structured, List<ExtractedFeature> features) {
        public static Parse failed() {
            return new Parse(false, List.of());
        }
    }

    public static Parse parse(String content) {
        if (content == null || content.isBlank()) return Parse.failed();
        try {
            String text = content.strip();
            if (text.startsWith("```")) {
                int newline = text.indexOf('\n');
                if (newline < 0) return Parse.failed();
                text = text.substring(newline + 1).strip();
                if (text.endsWith("```")) text = text.substring(0, text.length() - 3).strip();
            }
            JsonNode root = Json.readTree(text);
            if (root == null || !root.isObject() || !root.path("features").isArray()) return Parse.failed();
            List<ExtractedFeature> features = new ArrayList<>();
            for (JsonNode node : root.path("features")) {
                if (!node.isObject()) return Parse.failed();
                features.add(new ExtractedFeature(
                        text(node, "section"), text(node, "featureCode"), text(node, "proposedValue"),
                        text(node, "targetOperator"), text(node, "importance"), refs(node.path("evidenceRefs")),
                        node.path("confidence").isNumber() ? node.path("confidence").asDouble() : Double.NaN,
                        text(node, "reasonCode"), text(node, "behaviorKind"), node.path("hardConstraint").asBoolean(false)));
            }
            return new Parse(true, features);
        } catch (RuntimeException e) {
            return Parse.failed();
        }
    }

    /** Reads the task shape. Missing codes stay empty and are not rewritten to a nearby code. */
    public static TaskParse parseTask(String content) {
        if (content == null || content.isBlank()) return TaskParse.failed();
        try {
            String text = stripFence(content);
            JsonNode root = Json.readTree(text);
            if (root == null || !root.isObject() || !root.path("features").isArray()) return TaskParse.failed();
            List<CitedFeature> cited = new ArrayList<>();
            for (JsonNode node : root.path("features")) {
                if (!node.isObject()) return TaskParse.failed();
                String code = firstText(node, "proposedFeatureCode", "featureCode");
                String section = firstText(node, "proposedSection", "section");
                cited.add(new CitedFeature(new ExtractedFeature(
                        section, code, text(node, "proposedValue"),
                        text(node, "targetOperator"), text(node, "importance"),
                        refsOf(node), 0.0, "STATED", text(node, "behaviorKind"),
                        node.path("hardConstraint").asBoolean(false)),
                        text(node, "evidenceSpan")));
            }
            return new TaskParse(true, cited);
        } catch (RuntimeException e) {
            return TaskParse.failed();
        }
    }

    public static String verdict(String content) {
        if (content == null || content.isBlank()) return "";
        try {
            JsonNode root = Json.readTree(stripFence(content));
            if (root == null || !root.isObject()) return "";
            String verdict = text(root, "verdict");
            return switch (verdict) {
                case "SUPPORTED", "CONTRADICTED", "INSUFFICIENT", "AMBIGUOUS" -> verdict;
                default -> "";
            };
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String stripFence(String content) {
        String text = content.strip();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            if (newline < 0) return text;
            text = text.substring(newline + 1).strip();
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3).strip();
        }
        return text;
    }

    private static String firstText(JsonNode node, String preferred, String fallback) {
        String value = text(node, preferred);
        return value.isBlank() ? text(node, fallback) : value;
    }

    private static List<String> refsOf(JsonNode node) {
        String single = text(node, "evidenceRef");
        if (!single.isBlank()) return List.of(single);
        return refs(node.path("evidenceRefs"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : "";
    }

    private static List<String> refs(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> refs = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isString()) refs.add(item.asString());
        }
        return refs;
    }
}
