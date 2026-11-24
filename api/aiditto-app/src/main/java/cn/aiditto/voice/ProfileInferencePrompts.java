package cn.aiditto.voice;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 会话结束时：基于全部情景作答（含用户自定义原话）推断人格 + 语气风格（纯函数）。
 * 产物是「资产」——供 persona_text 与分身 Agent 使用，让分身用接近本人的语气说话。
 *
 * 合规：输入是用户本人自愿的情景作答；自定义原话在此只用于生成风格描述，
 * 输出同样禁止携带手机号/邮箱/真实姓名/聊天记录原文。
 */
public final class ProfileInferencePrompts {
    private ProfileInferencePrompts() {}

    public static final String PROMPT_VERSION = "voice-infer-v1";

    public static String system() {
        return """
                你是一个性格与说话风格分析助手。根据一个人在情景题里的选择与原话，推断他是怎样的人、平时怎么说话。
                铁律：
                1. 只依据给定作答，不脑补性别/年龄/职业/外貌/宗教/政治/健康等敏感属性。
                2. 不复述任何手机号、邮箱、真实姓名、网址、聊天记录原文。
                3. traits 给 3-5 个简短人格标签（中文，如"外向健谈""谨慎观察""幽默自嘲""直球坦诚""共情力强"），
                   每个附 0-1 置信度；只给有证据支持的。
                4. voice_style 用 2-3 句描述这个人的说话方式（句长、是否爱用梗、是否直接、语气冷热），
                   这是给分身 Agent 模仿的语气锚点，要具体可执行，不要空泛形容词堆砌。
                5. 输出严格 JSON，不要 markdown 代码块：
                   {"traits":[{"label":"外向健谈","confidence":0.8}],"voice_style":"...","summary":"≤80字人格小结"}
                """;
    }

    public static String user(List<String> answerLines) {
        StringBuilder sb = new StringBuilder("用户的情景作答记录：\n");
        if (answerLines == null || answerLines.isEmpty()) {
            sb.append("（用户没有作答，请如实给出低置信度结论，不要编造。）\n");
        } else {
            for (String line : answerLines) sb.append("- ").append(line).append('\n');
        }
        return sb.toString();
    }

    public record Inference(java.util.List<Trait> traits, String voiceStyle, String summary) {
        public record Trait(String label, double confidence) {}
        public boolean valid() {
            return traits != null && !traits.isEmpty()
                    && voiceStyle != null && !voiceStyle.isBlank() && !leaks(voiceStyle)
                    && summary != null && !leaks(summary);
        }
    }

    public static Inference parse(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            String text = content.strip();
            if (text.startsWith("```")) {
                int nl = text.indexOf('\n');
                if (nl < 0) return null;
                text = text.substring(nl + 1).strip();
                if (text.endsWith("```")) text = text.substring(0, text.length() - 3).strip();
            }
            JsonNode root = Json.readTree(text);
            if (root == null || !root.isObject()) return null;
            List<Inference.Trait> traits = new java.util.ArrayList<>();
            JsonNode ta = root.path("traits");
            if (!ta.isArray() || ta.isEmpty()) return null;
            for (JsonNode t : ta) {
                String label = t.path("label").asString("");
                double conf = t.path("confidence").isNumber() ? t.path("confidence").asDouble() : 0.0;
                if (label.isBlank() || label.length() > 20 || leaks(label)) return null;
                traits.add(new Inference.Trait(label, Math.max(0, Math.min(1, conf))));
            }
            String voice = clip(root.path("voice_style").asString(""), 600);
            String summary = clip(root.path("summary").asString(""), 120);
            Inference inf = new Inference(traits, voice, summary);
            return inf.valid() ? inf : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean leaks(String s) {
        if (s == null) return true;
        return s.matches(".*1[3-9]\\d{9}.*")
                || s.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")
                || s.contains("微信") || s.contains("http");
    }

    private static String clip(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    public static Object toJson(Inference inf) {
        if (inf == null) return Map.of();
        java.util.List<Map<String, Object>> ts = new java.util.ArrayList<>();
        for (Inference.Trait t : inf.traits()) ts.add(Map.of("label", t.label(), "confidence", t.confidence()));
        return Map.of("traits", ts, "voice_style", inf.voiceStyle(), "summary", inf.summary());
    }
}
