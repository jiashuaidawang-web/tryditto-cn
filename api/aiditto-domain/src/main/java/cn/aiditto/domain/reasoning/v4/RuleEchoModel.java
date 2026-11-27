package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fixture that only restates the structured lines in the prompt.
 * It is not a relationship model and its output is not a quality result.
 */
public final class RuleEchoModel implements ReasoningModel {
    public static final String NAME = "rule-echo-fixture";

    private final List<Long> latencies = new ArrayList<>();

    @Override
    public ReasoningCompletion complete(ReasoningPrompt.Rendered prompt) {
        long started = System.nanoTime();
        String direction = value(prompt.user(), "DIRECTION ");
        long chooser = Long.parseLong(value(prompt.user(), "CHOOSER "));
        long target = Long.parseLong(value(prompt.user(), "TARGET "));
        List<Map<String, Object>> supported = new ArrayList<>();
        List<Map<String, Object>> complements = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> contradictions = new ArrayList<>();
        for (String line : prompt.user().split("\n")) {
            if (!line.startsWith("ITEM ")) continue;
            String preference = field(line, "preference");
            String targetCode = field(line, "target");
            String satisfaction = field(line, "satisfaction");
            String preferenceRef = ref(prompt.user(), "PREFERENCE", preference);
            String selfRef = ref(prompt.user(), "SELF", targetCode);
            List<String> refs = new ArrayList<>();
            if (preferenceRef != null) refs.add(preferenceRef);
            if (selfRef != null) refs.add(selfRef);
            String statement = "偏好 " + preference + "=" + fieldValue(prompt.user(), "PREFERENCE", preference)
                    + "，对方特征 " + targetCode + "=" + fieldValue(prompt.user(), "SELF", targetCode)
                    + "，结构化结果 " + satisfaction + "。";
            Map<String, Object> claim = claim(preference, targetCode, refs, statement);
            switch (satisfaction) {
                case "SATISFIED", "PARTIALLY_SATISFIED" -> supported.add(claim);
                case "UNSATISFIED" -> conflicts.add(claim);
                case "UNKNOWN" -> missing.add(claim);
                case "CONTESTED" -> contradictions.add(claim);
                default -> { }
            }
        }
        if (has(prompt.user(), "PREFERENCE", "partner.support", "close")
                && has(prompt.user(), "SELF", "self.communication", "frequent")) {
            complements.add(claim("partner.support", "self.communication",
                    List.of(ref(prompt.user(), "PREFERENCE", "partner.support"), ref(prompt.user(), "SELF", "self.communication")),
                    "未验证假设：partner.support=close 与 self.communication=frequent 只记为潜在互补。"));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("direction", direction);
        root.put("chooserId", chooser);
        root.put("targetId", target);
        root.put("supportedMatches", supported);
        root.put("potentialComplements", complements);
        root.put("possibleConflicts", conflicts);
        root.put("missingInformation", missing);
        root.put("contradictions", contradictions);
        root.put("unsupportedClaims", List.of());
        root.put("confidence", 0.4);
        root.put("reasoningStatus", "COMPLETED");
        latencies.add(System.nanoTime() - started);
        return ReasoningCompletion.ok(ReasoningJson.write(root), NAME, latencies.get(latencies.size() - 1));
    }

    public List<Long> latencies() { return List.copyOf(latencies); }

    private static Map<String, Object> claim(String preference, String target, List<String> refs, String statement) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("preferenceCode", preference);
        claim.put("targetFeatureCode", target);
        claim.put("evidenceRefs", refs);
        claim.put("statement", statement);
        return claim;
    }

    private static String value(String prompt, String prefix) {
        for (String line : prompt.split("\n")) if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        return "";
    }

    private static String field(String line, String name) {
        String token = name + "=";
        int start = line.indexOf(token);
        if (start < 0) return "";
        int from = start + token.length();
        int end = line.indexOf(' ', from);
        return end < 0 ? line.substring(from) : line.substring(from, end);
    }

    private static String ref(String prompt, String role, String code) {
        for (String line : prompt.split("\n")) {
            if (line.startsWith(role + " ") && line.contains(" code=" + code + " ")) return field(line, "ref");
        }
        return null;
    }

    private static String fieldValue(String prompt, String role, String code) {
        for (String line : prompt.split("\n")) {
            if (line.startsWith(role + " ") && line.contains(" code=" + code + " ")) return field(line, "value");
        }
        return "";
    }

    private static boolean has(String prompt, String role, String code, String value) {
        return value.equals(fieldValue(prompt, role, code));
    }
}
