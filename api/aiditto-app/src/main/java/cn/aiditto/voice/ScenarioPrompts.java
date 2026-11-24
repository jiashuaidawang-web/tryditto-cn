package cn.aiditto.voice;

import cn.aiditto.domain.voice.VoiceModels.Option;
import cn.aiditto.domain.voice.VoiceModels.Scenario;
import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 自适应情景题的 LLM 提示词与严格解析（纯函数）。
 *
 * 灵魂：题目要能「勾出用户的真实性格与语气」，不是考知识。_option 的 predict_rank 至关重要——
 * 模型必须先赌「这个人最可能选哪个」，把最可能的放 A，让用户的选择来打脸或确认，从而校准模型对人的理解。
 *
 * 合规：输入只含用户本人已答的情景题与选择（脱敏），不接聊天记录/第三方数据；
 * 输出必须严格 JSON，带联系方式/真实姓名的选项直接判废重出。
 */
public final class ScenarioPrompts {
    private ScenarioPrompts() {}

    public static final String PROMPT_VERSION = "voice-scenario-v1";

    public static String system() {
        return """
                你在为一个「了解一个人真实性格与说话风格」的互动问卷出题。每次只出一道情景题。
                铁律：
                1. 情景要日常、具体、可想象（比如第一次约会冷场、朋友放鸽子、意见不合），不要抽象心理学术语。
                2. 给 3-4 个选项，每个选项代表一种真实的人格/说话倾向（如外向健谈、谨慎观察、幽默化解、直球坦诚）。
                3. 关键：你必须在 generation 前先判断「这个人最可能选哪个」，把最可能的放 A，其次 B，依次排序。
                   predict_rank 就是这个预测顺序（1=A 最可能，2=B…）。你是在赌这个人的反应，赌错也没关系——
                   用户的真实选择会用来校准你对他的理解，所以请认真赌，不要全都排成一样。
                4. 每个 option 的 trait 用简短英文标签（如 outgoing/cautious/humor/direct/empathetic/independent）。
                5. 禁止任何真实姓名、手机号、微信、邮箱、网址；选项文字里不要出现这些。
                6. 输出严格 JSON，不要 markdown 代码块：
                   {"scenario":"≤120字的日常情景","options":[{"key":"A","text":"≤40字","trait":"outgoing","predict_rank":1},...]}
                   key 从 A 开始连续；predict_rank 从 1 开始、按你预测的可能性递增、互不相同。
                """;
    }

    /** 用户输入：已有问答历史（脱敏后的题面 + 用户选了什么）。首轮给一句引导。 */
    public static String user(List<String> historyLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是用户目前的作答记录（按时间顺序）：\n");
        if (historyLines == null || historyLines.isEmpty()) {
            sb.append("（还没有作答，这是第一题。出一道最经典的、能快速看出一个人社交风格的情景题。）\n");
        } else {
            for (String line : historyLines) sb.append("- ").append(scrub(line)).append('\n');
            sb.append("\n基于以上真实作答，出下一道能进一步摸清这个人、且和前面不重复的情景题。\n");
            sb.append("尤其注意：如果前面用户的选择和你预测的不一样，说明你之前看错了，这次要据此修正你的猜测。\n");
        }
        return sb.toString();
    }

    /** 严格解析：任何字段缺失/格式错/疑似 PII 都返回 null，调用方据此重试或降级。 */
    public static Scenario parse(String content) {
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
            String scenario = root.path("scenario").asString("");
            if (scenario.isBlank() || scenario.length() > 300 || looksLikePii(scenario)) return null;
            JsonNode arr = root.path("options");
            if (!arr.isArray() || arr.size() < 2 || arr.size() > 6) return null;
            List<Option> opts = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode o = arr.get(i);
                String key = o.path("key").asString("");
                String optText = o.path("text").asString("");
                String trait = o.path("trait").asString("");
                int rank = o.path("predict_rank").asInt(0);
                if (key.isBlank() || optText.isBlank() || rank < 1 || rank > arr.size()) return null;
                if (looksLikePii(optText)) return null;
                opts.add(new Option(key.toUpperCase(), optText, trait, rank));
            }
            // predict_rank 必须是 1..N 的一个排列（防止模型全填 1 破坏校准）
            List<Integer> ranks = new ArrayList<>();
            for (Option o : opts) ranks.add(o.predictRank());
            java.util.Collections.sort(ranks);
            for (int i = 0; i < ranks.size(); i++) if (ranks.get(i) != i + 1) return null;
            Scenario s = new Scenario(scenario, opts);
            return s.valid() ? s : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean looksLikePii(String s) {
        return s != null && (s.matches(".*1[3-9]\\d{9}.*")
                || s.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")
                || s.contains("微信") || s.contains("wechat") || s.contains("http"));
    }

    private static String scrub(String s) {
        if (s == null) return "";
        return s.replaceAll("1[3-9]\\d{9}", "[手机]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[邮箱]");
    }
}
