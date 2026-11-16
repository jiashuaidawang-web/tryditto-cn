package cn.aiditto.domain.taste;

import java.util.Locale;

/**
 * 把一次真实反馈折叠成「改哪个维度、改多少」的**服务端确定性信号**。
 *
 * 模型（模板 3 rewrite_taste）只负责重写自然语言画像与建议改哪个维度；
 * 幅度、方向、防过拟合、矛盾信号保留、恶意差评降权全部由这段代码决定，
 * 绝不让 LLM 自己说了算。这是 A01 / A02 / A03 与「越学越准」能算出来的前提。
 *
 * 生产链路里 reasonTag 来自反馈表单的结构化原因标签或为空；
 * 仿真链路里由 FeedbackOracle 给出（仍只含结构化标签，不含隐藏真值数值）。
 */
public record FeedbackSignal(String dim, double delta, double strength,
                             boolean ambivalent, boolean malicious, String note) {

    public static final String OBSERVE = "待观察";

    public boolean applicable() {
        return dim != null && TasteWeights.DIMS.contains(dim) && Math.abs(delta) > 1e-9;
    }

    /**
     * @param reasonTag 形如 {@code good:humor} / {@code bad:schedule}，可为空
     * @param rating 1-5，可为空
     * @param again yes / no / unsure，可为空
     * @param comment 反馈原文（已脱敏、已截断），可为空
     */
    public static FeedbackSignal derive(String reasonTag, Integer rating, String again, String comment) {
        String text = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
        boolean ambivalent = text.contains("挺好的") && (text.contains("没感觉") || text.contains("说不上来"))
                || text.contains("但就是") || text.contains("说不上来哪不对");
        // 只把真正无信息量的回复算作 terse；「聊得挺好」这类 4 字中文短句仍有信号，
        // 用 length<=4 会把它误判成噪声，反而让矛盾反馈的强度看起来更高。
        boolean terse = text.isBlank() || text.equals("还行吧") || text.equals("一般") || text.length() <= 2;
        // 单次极端差评：评分与"愿再见"自相矛盾，或 1 分却无具体信息 → 按噪声处理
        boolean malicious = rating != null && rating == 1
                && ("yes".equals(again) || terse || ambivalent);

        double strength = strength(rating, again, terse);
        if (ambivalent) strength *= 0.5;          // A03 矛盾反馈：保留两种信号，不删旧结论
        if (malicious) strength *= 0.25;          // A01 恶意差评：画像几乎不动，下周期自动恢复
        strength = Math.max(0.0, Math.min(1.0, strength));

        String dim = null;
        boolean positive = true;
        Parsed parsed = parseTag(reasonTag);
        if (parsed != null) {
            dim = parsed.dim();
            positive = parsed.positive();
        } else {
            dim = fromText(text);
            positive = !isNegative(rating, again, text);
        }
        if (dim == null || !TasteWeights.DIMS.contains(dim)) {
            return new FeedbackSignal(null, 0.0, strength, ambivalent, malicious, "无可用维度信号，保留旧画像");
        }
        // good: 提升、bad: 降低（按反馈极性单向调整）。
        // 实验记录：曾改成「bad:隐性维度也提升、bad:显性维度降低」以贴合 Prompt 模板 3 字面语义，
        // 12 周期实测 taste_recovery 0.907→0.775、pred-true ρ 0.814→0.671，明显更差，已回滚。
        // 口是心非组的收敛改由 FeedbackOracle 的 reasonTag 指向来驱动，不在方向规则里硬编码。
        double delta = (positive ? 1.0 : -1.0) * TasteWeights.MAX_DELTA * strength;
        String note = (positive ? "提升 " : "降低 ") + dim;
        if (ambivalent) note += "；" + OBSERVE + "，保留矛盾信号";
        if (malicious) note += "；极端差评降权";
        return new FeedbackSignal(dim, delta, strength, ambivalent, malicious, note);
    }

    private static double strength(Integer rating, String again, boolean terse) {
        if (terse) return 0.25;
        double s = 0.5;
        if (rating != null) s = Math.abs(rating - 3) / 2.0 + 0.4; // 1/5 → 1.4→clamp, 3 → 0.4
        if ("yes".equals(again)) s += 0.15;
        if ("unsure".equals(again)) s *= 0.6;
        return Math.max(0.2, Math.min(1.0, s));
    }

    private static boolean isNegative(Integer rating, String again, String text) {
        if (rating != null && rating <= 2) return true;
        if ("no".equals(again)) return true;
        return text.contains("失望") || text.contains("没话题") || text.contains("不会再约")
                || text.contains("没感觉") || text.contains("聊不到");
    }

    /** 自由文本的关键词 → 维度映射；只认显式表述，不猜性格。 */
    private static String fromText(String text) {
        if (text.contains("笑点") || text.contains("幽默") || text.contains("逗") || text.contains("没感觉")) return "humor";
        if (text.contains("没话题") || text.contains("冷场") || text.contains("聊不到")) return "expressiveness";
        if (text.contains("尝试") || text.contains("冒险") || text.contains("新鲜")) return "adventure";
        if (text.contains("作息") || text.contains("节奏")) return "schedule";
        if (text.contains("舒服") || text.contains("压迫感") || text.contains("温暖")) return "warmth";
        if (text.contains("消费") || text.contains("花销")) return "spending";
        if (text.contains("安静") || text.contains("内向")) return "calm";
        if (text.contains("学业") || text.contains("上进")) return "ambition";
        return null;
    }

    private record Parsed(String dim, boolean positive) {}

    private static Parsed parseTag(String tag) {
        if (tag == null || tag.isBlank()) return null;
        String t = tag.strip().toLowerCase(Locale.ROOT);
        boolean positive = t.startsWith("good:") || t.startsWith("pos:");
        boolean negative = t.startsWith("bad:") || t.startsWith("neg:");
        if (!positive && !negative) return null;
        String dim = t.substring(t.indexOf(':') + 1).strip();
        return TasteWeights.DIMS.contains(dim) ? new Parsed(dim, positive) : null;
    }
}
