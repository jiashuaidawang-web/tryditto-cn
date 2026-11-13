package cn.aiditto.domain.questionnaire;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side twin of web/src/onboarding/validate.ts (技术方案 §6.4). Values arrive as plain JSON-shaped Java
 * objects (String, Boolean, Number, List, Map) and leave normalized in the same shape.
 */
public final class AnswerValidator {

    public static final String CUSTOM_PREFIX = "custom:";
    /** OptionList.tsx caps the custom pill input at 40 characters. */
    public static final int CUSTOM_MAX = 40;
    /** TextBar falls back to 120 when a text question sets no maxLength. */
    public static final int TEXT_MAX_DEFAULT = 120;
    public static final int LONG_TEXT_MAX_DEFAULT = 1000;
    public static final int MIN_AGE = 18;

    private static final Pattern DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern SCHOOL_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.edu(\\.cn)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Set<String> TRAVEL_MODES = Set.of("walk", "transit", "drive");
    private static final Set<String> HEIGHT_UNITS = Set.of("cm", "ft");

    /** {@code today} is the calendar date in Asia/Shanghai; {@code isArea} answers "is this a dating-area place id". */
    public record Context(LocalDate today, Predicate<String> isArea) {}

    private AnswerValidator() {}

    public static Object validate(QuestionSpec q, Object value, Context ctx) {
        String f = q.id();
        return switch (q.type()) {
            case SINGLE -> single(q, value);
            case MULTI -> multi(q, value);
            case TEXT -> text(f, value, q.maxLength() == null ? TEXT_MAX_DEFAULT : q.maxLength(), false);
            case LONG_TEXT -> text(f, value, q.maxLength() == null ? LONG_TEXT_MAX_DEFAULT : q.maxLength(), q.optional());
            case DATE -> birthday(f, value, ctx.today());
            case AGE_RANGE -> ageRange(f, value);
            case HEIGHT -> height(f, value);
            case HEIGHT_RANGE -> heightRange(f, value);
            case EMAIL -> email(f, value, "school".equals(q.emailKind()));
            case LOCATION -> location(f, value, ctx.isArea());
            case PHOTOS, INTERSTITIAL, CODE -> throw DomainException.invalid(f, "这一题不能直接写入");
        };
    }

    public static boolean isSchoolEmail(String email) {
        return email != null && SCHOOL_EMAIL.matcher(email.trim()).matches();
    }

    public static boolean isEmail(String email) {
        return email != null && EMAIL.matcher(email.trim()).matches();
    }

    /** Parses 'YYYY-MM-DD' only when it is a real calendar date. */
    public static LocalDate parseDate(Object value) {
        if (!(value instanceof String s)) return null;
        Matcher m = DATE.matcher(s);
        if (!m.matches()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    public static int ageOn(LocalDate birthday, LocalDate today) {
        return Period.between(birthday, today).getYears();
    }

    private static String single(QuestionSpec q, Object value) {
        if (!(value instanceof String s) || s.isEmpty()) throw DomainException.invalid(q.id(), "请选择一个选项");
        if (!optionOk(q, s)) throw DomainException.invalid(q.id(), "选项不存在：" + s);
        return s;
    }

    private static List<String> multi(QuestionSpec q, Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw DomainException.invalid(q.id(), "至少选一项");
        Set<String> seen = new LinkedHashSet<>();
        for (Object o : list) {
            if (!(o instanceof String s) || !optionOk(q, s)) throw DomainException.invalid(q.id(), "选项不存在：" + o);
            seen.add(s);
        }
        if (q.maxSelect() != null && seen.size() > q.maxSelect()) throw DomainException.invalid(q.id(), "最多选 " + q.maxSelect() + " 项");
        if (seen.size() > 1 && seen.stream().anyMatch(q.exclusive()::contains)) {
            throw DomainException.invalid(q.id(), "这个选项不能和其他选项同时选");
        }
        return new ArrayList<>(seen);
    }

    private static boolean optionOk(QuestionSpec q, String s) {
        if (q.options().contains(s)) return true;
        if (!q.allowCustom() || !s.startsWith(CUSTOM_PREFIX)) return false;
        String text = s.substring(CUSTOM_PREFIX.length()).strip();
        return !text.isEmpty() && text.codePointCount(0, text.length()) <= CUSTOM_MAX;
    }

    private static String text(String f, Object value, int max, boolean optional) {
        if (value == null && optional) return "";
        if (!(value instanceof String s)) throw DomainException.invalid(f, "请填写文字");
        String v = s.strip();
        if (v.isEmpty() && !optional) throw DomainException.invalid(f, "不能为空");
        if (v.codePointCount(0, v.length()) > max) throw DomainException.invalid(f, "最多 " + max + " 个字");
        return v;
    }

    private static String birthday(String f, Object value, LocalDate today) {
        LocalDate d = parseDate(value);
        if (d == null || d.getYear() < 1900 || d.isAfter(today)) throw DomainException.invalid(f, "生日格式不对");
        if (ageOn(d, today) < MIN_AGE) {
            throw new DomainException(ErrorCode.UNDER_AGE, "见一面仅面向 18 岁以上的在校大学生", List.of(f), null);
        }
        return d.toString();
    }

    private static List<Integer> ageRange(String f, Object value) {
        if (!(value instanceof List<?> l) || l.size() != 2) throw DomainException.invalid(f, "年龄范围格式不对");
        Integer min = intOf(l.get(0)), max = intOf(l.get(1));
        if (min == null || max == null || min < MIN_AGE || min > max || max > 80) throw DomainException.invalid(f, "年龄范围需在 18–80 之间");
        return List.of(min, max);
    }

    private static Map<String, Object> height(String f, Object value) {
        if (!(value instanceof Map<?, ?> m)) throw DomainException.invalid(f, "身高格式不对");
        Integer cm = intOf(m.get("cm"));
        Object unit = m.get("unit");
        if (cm == null || cm < 120 || cm > 230) throw DomainException.invalid(f, "身高需在 120–230 cm 之间");
        if (!(unit instanceof String u) || !HEIGHT_UNITS.contains(u)) throw DomainException.invalid(f, "单位不对");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cm", cm);
        out.put("unit", unit);
        return out;
    }

    private static Map<String, Object> heightRange(String f, Object value) {
        if (!(value instanceof Map<?, ?> m) || !(m.get("any") instanceof Boolean any)) throw DomainException.invalid(f, "身高范围格式不对");
        Integer min = intOf(m.get("min")), max = intOf(m.get("max"));
        if (!any && (min == null || max == null || min < 120 || min > max || max > 230)) {
            throw DomainException.invalid(f, "身高范围需在 120–230 cm 之间");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("any", any);
        out.put("min", min);
        out.put("max", max);
        return out;
    }

    private static String email(String f, Object value, boolean school) {
        if (!(value instanceof String s)) throw new DomainException(ErrorCode.INVALID_EMAIL, null, List.of(f), null);
        String v = s.strip();
        if (!(school ? isSchoolEmail(v) : isEmail(v))) {
            throw new DomainException(ErrorCode.INVALID_EMAIL, school ? "需要 edu.cn 或 .edu 学校邮箱" : "邮箱格式不对", List.of(f), null);
        }
        return v;
    }

    private static Map<String, Object> location(String f, Object value, Predicate<String> isArea) {
        if (!(value instanceof Map<?, ?> m)) throw DomainException.invalid(f, "位置格式不对");
        Object placeId = m.get("placeId");
        Object mode = m.get("mode");
        Integer minutes = intOf(m.get("minutes"));
        if (!(placeId instanceof String p) || !isArea.test(p)) throw DomainException.invalid(f, "地点不存在");
        if (!(mode instanceof String md) || !TRAVEL_MODES.contains(md)) throw DomainException.invalid(f, "出行方式不对");
        if (minutes == null || minutes < 10 || minutes > 120) throw DomainException.invalid(f, "时间需在 10–120 分钟之间");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("placeId", placeId);
        out.put("mode", mode);
        out.put("minutes", minutes);
        return out;
    }

    static Integer intOf(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return l.intValue();
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return (int) d;
        }
        return null;
    }
}
