package cn.aiditto.recall;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 性别取值归一化。
 *
 * 工程枚举（profile / V2 CHECK）是 man / woman / nonbinary / other，
 * 但合成 fixture 与前端历史题目里 expectedGender 用的是 men / women / everyone。
 * 不做归一化会让「双向性别期望」过滤在 Qdrant 与结构化路同时 0 命中（Step 5.3 实测）。
 * 这里做成兼容层而不是改真值数据：仿真 fixture 保持原样，线上脏取值也自动对齐。
 */
public final class GenderNorms {

    public static final List<String> ALL = List.of("man", "woman", "nonbinary", "other");

    private GenderNorms() {}

    public static String norm(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "men", "male" -> "man";
            case "women", "female" -> "woman";
            case "everyone", "any", "all" -> "everyone";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }

    /** 空值 = 不限；everyone = 展开全部枚举；未知值原样保留（宁可少召回也不静默丢弃用户偏好）。 */
    public static List<String> normList(List<String> raw) {
        if (raw == null || raw.isEmpty()) return ALL;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            String n = norm(s);
            if ("everyone".equals(n)) out.addAll(ALL);
            else if (!n.isBlank()) out.add(n);
        }
        return out.isEmpty() ? ALL : List.copyOf(out);
    }
}
