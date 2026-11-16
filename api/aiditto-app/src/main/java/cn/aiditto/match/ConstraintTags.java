package cn.aiditto.match;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 结构化红线标签白名单（Step 8）。
 *
 * §8.1 红线：dealbreaker **原文**绝不进提示词。这里只从 {@code profile.ai_constraints}
 * 里读取固定闭枚举的标签 id（如 {@code smoking}），模型看到的也只是标签 id；
 * 服务端用同一集合过滤裁判自报的 conflicts，模型编的"爱好不同/场景差异"一律忽略。
 */
public final class ConstraintTags {

    public static final Set<String> ALLOWED = Set.of(
            "smoking", "drinking", "long_distance", "marriage_kids", "pets",
            "religion_conflict", "diet", "night_owl", "height", "education");

    private ConstraintTags() {}

    /** 只认白名单标签；任何自由文本、数值条件、未知字段都被丢弃。 */
    public static Set<String> from(String aiConstraintsJson) {
        if (aiConstraintsJson == null || aiConstraintsJson.isBlank()) return Set.of();
        try {
            JsonNode root = Json.readTree(aiConstraintsJson);
            if (root == null || !root.isObject()) return Set.of();
            Set<String> out = new LinkedHashSet<>();
            for (String field : new String[]{"tags", "dealbreakers"}) {
                JsonNode arr = root.get(field);
                if (arr == null || !arr.isArray()) continue;
                for (JsonNode n : arr) {
                    if (!n.isTextual()) continue;
                    String tag = n.asString().strip();
                    if (ALLOWED.contains(tag)) out.add(tag);
                }
            }
            return Set.copyOf(out);
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    /** 给裁判提示词的标签清单：只有 id，没有用户原文。 */
    public static String describe(Set<String> tags) {
        return tags == null || tags.isEmpty() ? "无" : String.join(",", new java.util.TreeSet<>(tags));
    }
}
