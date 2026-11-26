package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.ExtractedFeature;
import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Turns model JSON into candidates. Invalid JSON is a failed parse, not a profile. */
public final class ProfileExtractionParser {
    private ProfileExtractionParser() {}

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
