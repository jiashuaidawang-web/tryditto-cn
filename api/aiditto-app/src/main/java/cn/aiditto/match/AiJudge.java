package cn.aiditto.match;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 6 Agent 精排的裁判 rubric 与严格输出校验（纯逻辑，不依赖网络）。
 *
 * 对应 research/见一面-匹配系统-Prompt模板.md 模板 1/2。
 * 服务端校验是 C 端红线：模型输出必须可解释、可举证、不可自相矛盾，
 * 绝不能把未经校验的分数直接喂给配对算法。
 */
public final class AiJudge {

    public static final String PROMPT_VERSION = "judge-chemistry-v1";
    public static final String SIM_PROMPT_VERSION = "simulate-date-v1";
    private static final double[] DIM_WEIGHTS = {0.30, 0.30, 0.20, 0.10}; // values/chemistry/vibe/feasible

    private AiJudge() {}

    public record Verdict(int total, Map<String, Integer> dims, List<String> conflicts,
                          String reasonText, List<String> topics, double confidence,
                          boolean degraded) {
        public boolean valid() {
            return total >= 0 && total <= 100 && conflicts != null && topics != null && dims != null;
        }
    }

    /** 分身模拟：一次生成完整 3 轮对话（不是 3 次调用，省成本）。画像输入必须已脱敏。 */
    public static String simulateSystem() {
        return """
                你是一个对话模拟器。任务：根据两份真人画像，模拟两人在校园咖啡馆初次见面的对话。
                你同时扮演 A 和 B，但必须严格区分两人的说话方式、关注点和信息边界。
                铁律：
                1. A 只知道 A 画像里的信息，B 只知道 B 画像里的信息，禁止任何一方说出对方画像里才有的私密信息。
                2. 不要旁白、不要心理描写、不要总结、不要评价、不要出现"匹配/评分/算法"等词。
                3. 对话要自然、有来有回、允许短暂冷场或话题转折——冷场本身是重要信号，不要替他们圆场。
                4. 恰好 3 轮（每轮 A 一句 + B 一句），共 6 句，每句不超过 45 字。
                5. 输出严格 JSON，不要 markdown 代码块：
                {"turns":[{"a":"...","b":"..."},{"a":"...","b":"..."},{"a":"...","b":"..."}]}
                """;
    }

    public static String simulateUser(String personaA, String personaB, String schoolName) {
        return "【A 的画像】\n" + safe(personaA) + "\n\n【B 的画像】\n" + safe(personaB)
                + "\n\n【共同场景】\n" + safe(schoolName) + " 校内咖啡馆，周三晚上，第一次见面，各有一杯饮料的时间。";
    }

    /** 裁判 rubric：跨表面共鸣优先，严禁把"条件都满足"当成化学反应。 */
    public static String judgeSystem() {
        return """
                你是约会兼容性裁判。依据两份画像和他们的模拟对话，预测这两人线下见面是否会产生化学反应。
                评分 rubric（总分 0-100，各维度先打 0-100 再按权重加权）：
                - values 价值观/生活目标共鸣        权重 30%
                - chemistry 对话化学反应            权重 30%（是否接得住、有延展、有来有回；冷场多则低分）
                - vibe 人格互补/气质共振            权重 20%（跨表面共鸣：如冒险型 × 表达欲，不要只看爱好重不重叠）
                - feasible 现实可执行性              权重 10%（等时圈可达、时间槽、场地）
                - 硬冲突：仅当命中【A/B 的硬性不接受清单】中明确列出的项时才可写入 conflicts，
                  一旦写入 total 直接 0，不得给情面分。
                判断原则：
                0. **爱好不同、兴趣场景不同、专业不同、作息不同都不是冲突**，绝不能写进 conflicts。
                   例如"攀岩 × 街舞"表面零交集但内核同为冒险型，属于典型的跨表面共鸣，应给高分。
                   conflicts 只用于吸烟、异地、婚育观相斥等一方明确声明"不接受"的红线。
                1. 爱好列表重合 ≠ 合拍。要看爱好背后的人格特质。
                2. 严禁把"条件都满足"当成"会有化学反应"，条件项只体现在 feasible。
                3. reason_text 必须说清"为什么是这两个人"，≤40 字，中文，不许写"很般配""条件不错"这类空话。
                4. topics 给 3 个具体可聊的破冰话题，要能从画像里找到落点，不要"聊聊爱好"这种泛话题。
                5. 信息不足或对话过于平淡时，如实降低 confidence，不要硬凑高分。
                输出严格 JSON（不要 markdown 代码块）：
                {"total":0,"dims":{"values":0,"chemistry":0,"vibe":0,"feasible":0},
                 "conflicts":[],"reason_text":"","topics":["","",""],"confidence":0.0}
                """;
    }

    public static String judgeUser(String personaA, String personaB, String dealbreakersA,
                                   String dealbreakersB, String transcript,
                                   boolean isoReachable, boolean slotsOverlap) {
        return "【A 画像】" + safe(personaA) + "\n【B 画像】" + safe(personaB)
                + "\n【A 的硬性不接受清单】" + safe(dealbreakersA)
                + "\n【B 的硬性不接受清单】" + safe(dealbreakersB)
                + "\n【模拟对话】" + safe(transcript)
                + "\n【等时圈可达】" + isoReachable + "  【时间槽重合】" + slotsOverlap;
    }

    /** 校验模拟对话：恰好 3 轮、每轮 a/b、每句 ≤45 字、无旁白敏感词。 */
    public static String validateTranscript(String content) {
        if (content == null || content.isBlank()) return "模拟对话为空";
        try {
            JsonNode turns = Json.readTree(content).get("turns");
            if (turns == null || turns.size() != 3) return "模拟对话必须恰好 3 轮";
            for (JsonNode t : turns) {
                String a = t.path("a").asString(""), b = t.path("b").asString("");
                if (a.isBlank() || b.isBlank()) return "每轮必须有 A/B 两句";
                if (a.length() > 45 || b.length() > 45) return "单句不得超过 45 字";
                String both = a + b;
                for (String w : List.of("匹配", "评分", "算法", "系统")) {
                    if (both.contains(w)) return "对话里不得出现元信息词：" + w;
                }
            }
            return null;
        } catch (RuntimeException e) {
            return "模拟对话 JSON 非法";
        }
    }

    /**
     * 兼容入口：不传声明红线集合。此时**忽略模型自报的 conflicts**，
     * 因为 §8.1 规定 dealbreaker 原文不得进提示词，模型没有依据判定硬冲突；
     * 若照单全收会把"爱好不同"误判成红线、误杀跨表面共鸣（实测 deepseek 会这样）。
     */
    public static Verdict parse(String content) {
        return parse(content, java.util.Set.of());
    }

    /**
     * 严格解析裁判输出；返回 null 表示不可用（调用方必须降级，不得猜分）。
     *
     * @param declaredDealbreakers 服务端持有的**结构化红线标签**（不是原文），
     *                             只有命中这些标签才允许强制 total=0。
     */
    public static Verdict parse(String content, java.util.Set<String> declaredDealbreakers) {
        java.util.Set<String> declared = declaredDealbreakers == null
                ? java.util.Set.of() : declaredDealbreakers;
        if (content == null || content.isBlank()) return null;
        try {
            JsonNode root = Json.readTree(stripFence(content));
            Map<String, Integer> dims = new LinkedHashMap<>();
            String[] keys = {"values", "chemistry", "vibe", "feasible"};
            JsonNode d = root.get("dims");
            if (d == null) return null;
            for (String k : keys) {
                JsonNode n = d.get(k);
                if (n == null || !n.isNumber()) return null;
                int v = n.asInt();
                if (v < 0 || v > 100) return null;
                dims.put(k, v);
            }
            // 只保留服务端声明过的结构化红线标签；模型自己编的"差异/场景不同"一律忽略，
            // 避免把跨表面共鸣误杀。这是 C 端"不误杀"与 §8.1 不送原文的双重要求。
            List<String> conflicts = new ArrayList<>();
            JsonNode c = root.get("conflicts");
            if (c != null && c.isArray()) {
                for (JsonNode x : c) {
                    String tag = x.asString("");
                    if (!tag.isBlank() && declared.contains(tag)) conflicts.add(tag);
                }
            }
            List<String> topics = new ArrayList<>();
            JsonNode t = root.get("topics");
            if (t != null && t.isArray()) for (JsonNode x : t) topics.add(x.asString(""));
            if (topics.size() != 3) return null;
            String reason = root.path("reason_text").asString("");
            if (reason.length() > 40) return null;
            double conf = root.path("confidence").asDouble(0.0);
            if (conf < 0 || conf > 1) return null;

            // 服务端复算加权分，不接受模型自报 total（防止自相矛盾/凑分）
            int recomputed = 0;
            for (int i = 0; i < keys.length; i++) recomputed += Math.round(dims.get(keys[i]) * DIM_WEIGHTS[i]);
            int total = Math.max(0, Math.min(100, recomputed));
            boolean hardConflict = !conflicts.isEmpty();
            if (hardConflict) total = 0; // 硬冲突强制 0，不得给情面分
            int reported = root.path("total").asInt(-1);
            // 有硬冲突时模型理应自报 0；若自报高分但已列出冲突，以冲突为准（服务端兜底），
            // 绝不能因为"自报与复算不一致"把最重要的硬否决信号丢弃成降级。
            if (!hardConflict && reported >= 0 && Math.abs(reported - total) > 25) {
                return null;
            }
            return new Verdict(total, dims, conflicts, reason, topics, conf,
                    conf < 0.5 || hardConflict);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 低置信度降权：不淘汰，按比例压向中性，避免单个模型判断决定学生的一次见面。 */
    public static int applyConfidence(int total, double confidence) {
        if (confidence >= 0.5) return total;
        return (int) Math.round(total * 0.6 + 50 * 0.4);
    }

    /** 可直接落 recall_candidate.sim_report 的结构化留痕（不含任何 PII）。 */
    public static Map<String, Object> report(Verdict v, String transcript) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("prompt_version", PROMPT_VERSION);
        out.put("total", v.total());
        out.put("dims", v.dims());
        out.put("conflicts", v.conflicts());
        out.put("reason_text", v.reasonText());
        out.put("topics", v.topics());
        out.put("confidence", v.confidence());
        out.put("degraded", v.degraded());
        out.put("transcript", transcript == null ? "" : clip(transcript, 600));
        return out;
    }

    private static String stripFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int i = t.indexOf('\n');
            if (i > 0) t = t.substring(i + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    /**
     * 提示词构造的最后一道脱敏闸（纵深防御）：即使上游忘了脱敏，也不让
     * 邮箱/手机号/密钥/链接进入任何模型请求。技术方案 §8.1 红线。
     */
    private static String safe(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\w.+-]+@[\\w.-]+", "[邮箱]")
                .replaceAll("1[3-9]\\d{9}", "[手机号]")
                .replaceAll("sk-[A-Za-z0-9]{16,}", "[密钥]")
                .replaceAll("https?://\\S+", "[链接]");
        return clip(t, 800);
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
