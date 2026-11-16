package cn.aiditto.taste;

import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.taste.TasteFacts;
import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * taste_extract 的提示词、输出校验与确定性模板兜底。
 *
 * 失败路径是 C 端底线：模型超时 / 主备全挂 / 输出不是合法 JSON / 输出泄漏联系方式，
 * 一律用模板生成画像，绝不把「画像为空」或异常抛给用户。模板只依赖脱敏 facts，天然确定性。
 */
public final class TasteWriter {

    public static final String CALL_POINT = "taste_extract";
    public static final String PROMPT_VERSION = "taste-v1";
    /** Step 5 接 Qdrant 后替换为真实 embedding 版本；在此之前禁止跨版本混算相似度。 */
    public static final String EMBEDDING_VERSION = "v0-placeholder";

    private static final int MAX_LEN = 300;
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern SECRET = Pattern.compile("sk-[A-Za-z0-9]{8,}|\\{\\{");

    private static final String SYSTEM = """
            你是「见一面」的画像助手。只依据给定的脱敏事实，用简体中文写两段文字，严格输出一个 JSON 对象：
            {"persona":"...","taste":"..."}
            persona：这个人是什么气质、平时怎么和人相处（不超过 120 字）。
            taste：这个人真正想遇到什么样的人、什么样的约会（不超过 120 字）。
            铁律：
            1. 不推测性别、取向、宗教、政治、健康、外貌，不编造事实；
            2. 不复述输入里的联系方式、真实姓名或任何编号，直接忽略；
            3. 只输出 JSON 对象本身，不要解释、不要代码围栏、不要 Markdown。
            """;

    /** fromModel=false 表示模板兜底；reason 用于 updated_reason 与闸门统计。 */
    public record Draft(String persona, String taste, boolean fromModel, String reason) {}

    private TasteWriter() {}

    public static LlmRequest request(TasteFacts facts) {
        return new LlmRequest(CALL_POINT, PROMPT_VERSION, SYSTEM,
                "脱敏事实（JSON）：\n" + Json.write(facts.values()),
                512, 0.0, true, true);
    }

    /** 严格解析；任何不合规都返回 null，由调用方走模板兜底。 */
    public static Draft parse(String content) {
        if (content == null || content.isBlank()) return null;
        String text = content.strip();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl < 0) return null;
            text = text.substring(nl + 1).strip();
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3).strip();
        }
        try {
            JsonNode root = Json.readTree(text);
            if (root == null || !root.isObject()) return null;
            String persona = text(root, "persona");
            String taste = text(root, "taste");
            if (persona == null || taste == null) return null;
            if (persona.length() > MAX_LEN || taste.length() > MAX_LEN) return null;
            if (leaks(persona) || leaks(taste)) return null;
            return new Draft(persona, taste, true, "llm");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 确定性兜底：字段顺序固定，不依赖模型、时间、随机数。 */
    public static Draft template(TasteFacts facts, String reason) {
        Map<String, Object> v = facts.values();
        String persona = "兴趣：" + orUnknown(join(v.get("hobbies")))
                + "；相处风格：" + orUnknown(str(v.get("matchingStyle")))
                + "；身份：" + (Boolean.TRUE.equals(v.get("isStudent")) ? "在校学生" : "已工作")
                + "；年级或学历：" + orUnknown(join(v.get("schoolYear"), v.get("educationLevel")))
                + "；开场白：" + orUnknown(str(v.get("firstMessage")));
        String taste = "期待的关系：" + orUnknown(join(v.get("relationshipLooking")))
                + "；首次约会预算：" + orUnknown(str(v.get("firstDateBudget")))
                + "；学历看重程度：" + orUnknown(str(v.get("educationImportance")))
                + "；期望年龄：" + orUnknown(join(v.get("expectedAgeRange")))
                + "；通勤：" + orUnknown(commute(v.get("commute")));
        return new Draft(clip(persona), clip(taste), false, reason == null ? "template" : reason);
    }

    public static Draft choose(LlmResult result, TasteFacts facts) {
        if (result != null && result.ok()) {
            Draft parsed = parse(result.content());
            if (parsed != null) return parsed;
            return template(facts, "invalid_llm_output");
        }
        return template(facts, result == null ? "llm_exception" : "degraded");
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || !n.isTextual()) return null;
        String s = n.asString().strip();
        return s.isEmpty() ? null : s;
    }

    private static boolean leaks(String s) {
        return EMAIL.matcher(s).find() || PHONE.matcher(s).find() || SECRET.matcher(s).find();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).strip();
    }

    private static String join(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            if (p instanceof List<?> l) {
                for (Object e : l) {
                    if (sb.length() > 0) sb.append('、');
                    sb.append(e);
                }
            } else if (p != null && !String.valueOf(p).isBlank()) {
                if (sb.length() > 0) sb.append('、');
                sb.append(p);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String commute(Object o) {
        if (!(o instanceof Map<?, ?> m)) return "";
        Object mode = m.get("mode"), minutes = m.get("minutes");
        if (mode == null && minutes == null) return "";
        return str(mode) + " " + (minutes == null ? "" : minutes + " 分钟内");
    }

    private static String orUnknown(String s) {
        return s == null || s.isBlank() ? "未填写" : s;
    }

    private static String clip(String s) {
        return s.length() > MAX_LEN ? s.substring(0, MAX_LEN) : s;
    }
}
