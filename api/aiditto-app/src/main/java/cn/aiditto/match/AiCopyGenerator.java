package cn.aiditto.match;

import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.Labels;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.MatchCopy.Reason;
import cn.aiditto.domain.copy.MatchCopy.ViewerCopy;
import cn.aiditto.domain.copy.ProfileFacts;
import cn.aiditto.domain.copy.VenueOption;
import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Step 7 AI 文案的提示词与严格输出校验（纯逻辑，不依赖网络 / Spring）。
 *
 * 设计红线（面对 C 端学生用户）：
 * 1. 输入只有 {@link ProfileFacts} 的显式白名单字段：爱好、分支选项、自由文本、关系期待、
 *    节奏、预算、开场白、是否在校、年级。**昵称、性别、身高、生日、照片、联系方式、
 *    dealbreaker 原文一律不进提示词**（技术方案 §8.1）。
 * 2. 地点由程序决定，模型不输出 venueId / 地点 / 评分，避免"大模型选地点"越界与无效引用。
 * 3. 输出必须经过 {@link CopyValidator#validate}：禁用词、联系方式、真实姓名、未知占位符、
 *    编造引用、长度、cites 共同项，零容忍；不合法就带问题重try，绝不带病落库。
 * 4. 任何异常都只产生 issues，不抛给调用方；调用方据此决定重试或模板兜底。
 */
public final class AiCopyGenerator {

    public static final String DEFAULT_PROMPT_VERSION = "match-copy-v1";

    private AiCopyGenerator() {}

    public record Result(MatchCopy copy, List<CopyValidator.Issue> issues) {
        public boolean ok() {
            return copy != null && issues.isEmpty();
        }
    }

    // ── prompt ────────────────────────────────────────────────────────────────────────────────

    public static String system() {
        return """
                你是「见一面」的匹配卡片文案编辑，为一对已经通过全部硬约束的学生写周三见面卡片。
                风格：克制、真诚、有校园感；不用感叹号堆砌，不做外貌评价，不承诺关系结果，不制造焦虑。
                铁律（违反任何一条，输出会被程序判为作废并重试）：
                1. 只使用输入里确实存在的事实，严禁编造爱好、经历、性格、地点、名字、对话。
                2. 用 {{partner}} 指代对方、{{me}} 指代自己；禁止出现任何真实姓名或昵称。
                3. 禁止手机号、微信/QQ、邮箱、网址、二维码、任何联系方式与导流话术。
                4. 禁止外貌/身材/身高、性暗示、宗教、政治、健康诊断、金钱、命中注定/灵魂伴侣、
                   匹配度/契合度/百分比一类词。
                5. 使用「」或《》等引号时，引号内的文字必须逐字来自输入的【可用词表】，不得自造引号内容。
                6. reasons 的 cites 只能从【共同项清单】里选，必须是数组，每条至少 1 个。
                7. 不评价、不比较、不给关系建议，不用"一定""肯定""最适合"这类断言。
                8. 地点由程序决定：不要输出 venueId、地点、评分、性别、年龄等字段。
                结构（严格 JSON，不要 markdown 代码块）：
                {"low":{"headline":"≤24字","blurb":["≤110字","≤110字"],
                        "openers":["≤55字","≤55字"],
                        "reasons":[{"text":"≤55字","basis":"≤55字","cites":["共同项字段"]}]},
                 "high":{"headline":"≤24字","blurb":["≤110字","≤110字"],
                         "openers":["≤55字","≤55字"],
                         "reasons":[{"text":"≤55字","basis":"≤55字","cites":["共同项字段"]}]},
                 "activity":"≤110字，首次见面的活动安排，可留空",
                 "extra":"≤110字，一句补充提醒，可留空"}
                low/high 的 reasons 各 2–3 条、blurb 恰好 2 条、openers 恰好 2 条。
                low 是 A 看到的关于 B 的卡片，high 是 B 看到的关于 A 的卡片；
                两边不得互相复制，必须各自贴合"看这张卡的人"的视角。
                """;
    }

    /** 构造脱敏后的用户输入；ctx 只用于取共同项与词表，绝不把 forbiddenNames 送进模型。 */
    public static String user(ProfileFacts low, ProfileFacts high, CopyContext ctx, List<VenueOption> venues) {
        return "【A 的白名单资料】\n" + safe(Json.write(facts(low)))
                + "\n\n【B 的白名单资料】\n" + safe(Json.write(facts(high)))
                + "\n\n【共同项清单（cites 只能从这里选）】\n" + safe(new TreeSet<>(ctx.sharedFields()).toString())
                + "\n\n【可用词表（引号内容必须逐字来自这里）】\n" + safe(terms(ctx, venues).toString())
                + "\n\n【约会地点类型】校内咖啡馆（具体地点由程序选定，不要写进文案）"
                + "\n\n请输出严格 JSON。A 对应 low，B 对应 high。";
    }

    /** 显式白名单：字段逐个列出，未来新增敏感字段不会被"顺手"带进提示词。 */
    private static Map<String, Object> facts(ProfileFacts p) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("在校学生", p.student());
        if (p.schoolYear() != null && !p.schoolYear().isBlank()) f.put("年级", label("schoolYear", p.schoolYear()));
        List<String> hobbies = new ArrayList<>();
        for (String h : p.hobbies()) {
            String l = Labels.hobby(h, Lang.ZH);
            hobbies.add(l == null ? h : l);
        }
        f.put("爱好", hobbies);
        Map<String, Object> options = new LinkedHashMap<>();
        p.hobbyOptions().forEach((q, opts) -> {
            List<String> ls = new ArrayList<>();
            for (String o : new TreeSet<>(opts)) {
                String l = Labels.of(q, o, Lang.ZH);
                ls.add(l == null ? o : l);
            }
            if (!ls.isEmpty()) options.put(q, ls);
        });
        f.put("分支选项", options);
        Map<String, String> texts = new LinkedHashMap<>();
        p.hobbyTexts().forEach((q, t) -> texts.put(q, clip(safe(t), 60)));
        f.put("自由文本", texts);
        if (!p.relationshipLooking().isEmpty()) {
            List<String> ls = new ArrayList<>();
            for (String o : new TreeSet<>(p.relationshipLooking())) {
                String l = Labels.of("relationshipLooking", o, Lang.ZH);
                ls.add(l == null ? o : l);
            }
            f.put("想找的关系", ls);
        }
        if (p.matchingStyle() != null) f.put("节奏", label("matchingStyle", p.matchingStyle()));
        if (p.budget() != null) f.put("首约预算", label("firstDateBudget", p.budget()));
        if (p.firstMessage() != null) f.put("开场白", clip(safe(p.firstMessage()), 60));
        return f;
    }

    private static String label(String q, String o) {
        String l = Labels.of(q, o, Lang.ZH);
        return l == null ? o : l;
    }

    /** knownTerms 与 CopyValidator 的 UNKNOWN_QUOTE 判定同源，避免模型"合理引用"却被服务端拒。 */
    private static List<String> terms(CopyContext ctx, List<VenueOption> venues) {
        Set<String> out = new LinkedHashSet<>();
        for (VenueOption v : venues) {
            out.add(v.name());
            if (!v.defaultActivity().isBlank()) out.add(v.defaultActivity());
            if (!v.defaultExtra().isBlank()) out.add(v.defaultExtra());
        }
        for (String t : ctx.knownTerms()) {
            if (t != null && !t.isBlank()) out.add(clip(t, 40));
        }
        List<String> list = new ArrayList<>(out);
        return list.size() > 80 ? list.subList(0, 80) : list;
    }

    // ── parse + validate ──────────────────────────────────────────────────────────────────────

    /**
     * 把模型输出解析成 {@link MatchCopy} 并跑完整程序校验。
     * venueId 由服务端强制填入（模型无权决定地点），activity/extra 允许模型建议但同样受校验。
     */
    public static Result parse(String raw, CopyContext ctx, String venueId,
                               String defaultActivity, String defaultExtra) {
        List<CopyValidator.Issue> issues = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            issues.add(new CopyValidator.Issue("$", CopyValidator.Rule.STRUCTURE, "模型输出为空"));
            return new Result(null, issues);
        }
        try {
            JsonNode root = Json.readTree(CopyValidator.extractJson(raw));
            if (root == null || !root.isObject()) {
                issues.add(new CopyValidator.Issue("$", CopyValidator.Rule.STRUCTURE, "顶层必须是 JSON 对象"));
                return new Result(null, issues);
            }
            ViewerCopy low = viewer(root.get("low"), "low", issues);
            ViewerCopy high = viewer(root.get("high"), "high", issues);
            String activity = str(root.get("activity"));
            String extra = str(root.get("extra"));
            if (activity.isBlank()) activity = defaultActivity == null ? "" : defaultActivity;
            if (extra.isBlank()) extra = defaultExtra == null ? "" : defaultExtra;
            MatchCopy copy = new MatchCopy(low, high, venueId, activity, extra, MatchCopy.Source.AI);
            issues.addAll(CopyValidator.validate(copy, ctx));
            return new Result(issues.isEmpty() ? copy : null, issues);
        } catch (RuntimeException e) {
            issues.add(new CopyValidator.Issue("$", CopyValidator.Rule.STRUCTURE, "JSON 解析失败：" + e.getClass().getSimpleName()));
            return new Result(null, issues);
        }
    }

    private static ViewerCopy viewer(JsonNode n, String path, List<CopyValidator.Issue> issues) {
        if (n == null || !n.isObject()) {
            issues.add(new CopyValidator.Issue(path, CopyValidator.Rule.STRUCTURE, "缺少或不是对象"));
            return null;
        }
        List<String> blurb = strings(n.get("blurb"), path + ".blurb", issues);
        List<String> openers = strings(n.get("openers"), path + ".openers", issues);
        List<Reason> reasons = new ArrayList<>();
        JsonNode rs = n.get("reasons");
        if (rs == null || !rs.isArray()) {
            issues.add(new CopyValidator.Issue(path + ".reasons", CopyValidator.Rule.STRUCTURE, "必须是数组"));
        } else {
            for (JsonNode r : rs) {
                if (r == null || !r.isObject()) {
                    issues.add(new CopyValidator.Issue(path + ".reasons[]", CopyValidator.Rule.STRUCTURE, "元素必须是对象"));
                    continue;
                }
                reasons.add(new Reason(str(r.get("text")), str(r.get("basis")),
                        strings(r.get("cites"), path + ".reasons[].cites", issues)));
            }
        }
        return new ViewerCopy(str(n.get("headline")), blurb, reasons, openers);
    }

    private static List<String> strings(JsonNode n, String path, List<CopyValidator.Issue> issues) {
        if (n == null || !n.isArray()) {
            issues.add(new CopyValidator.Issue(path, CopyValidator.Rule.STRUCTURE, "必须是字符串数组"));
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : n) out.add(str(x));
        return List.copyOf(out);
    }

    private static String str(JsonNode n) {
        return n == null || !n.isTextual() ? "" : n.asString();
    }

    /**
     * 提示词构造的最后一道脱敏闸（纵深防御）：即使上游白名单漏了，也不让
     * 邮箱 / 手机号 / 密钥 / 链接进入任何模型请求。技术方案 §8.1 红线。
     */
    private static String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\w.+-]+@[\\w.-]+", "[邮箱]")
                .replaceAll("1[3-9]\\d{9}", "[手机号]")
                .replaceAll("sk-[A-Za-z0-9]{16,}", "[密钥]")
                .replaceAll("https?://\\S+", "[链接]");
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
