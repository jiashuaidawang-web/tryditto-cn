package cn.aiditto.domain.taste;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;
import cn.aiditto.domain.questionnaire.QuestionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 发给画像模型的脱敏事实（技术方案 §8.1 白名单）。纯函数，无 IO、无模型调用。
 *
 * 采用**显式白名单**而不是黑名单：漏列一个字段只是少一点信息，黑名单漏列一个就是 PII 事故。
 * 永远不进 facts：照片、手机号、邮箱、验证码、微信号、真实姓名、性别 / expectedGender、
 * 精确生日、身高、经纬度、所有 catalog.isSensitive() 的敏感题、dealbreakers 原文。
 *
 * 输出稳定有序（TreeMap）并计算 inputHash = SHA-256(promptVersion + canonical(values))，
 * 用于同一输入复用画像版本，避免重复调用模型、避免版本膨胀。
 */
public final class TasteFacts {

    /** 允许发给模型的答案字段（本人 taste_extract）。 */
    private static final Set<String> ALLOWED = Set.of(
            "hobbies", "cookingStyle", "cookingSignatureDish", "musicSound", "musicNowPlaying",
            "travelStyle", "travelNext", "readingGenres", "readingLastBook",
            "gymTrainingStyle", "gymTrainingFor", "gamingGenres", "gamingNowPlaying",
            "moviesContent", "moviesLastWatch", "artMediums", "artMake",
            "relationshipLooking", "matchingStyle", "educationLevel", "educationImportance",
            "schoolYear", "occupation", "firstDateBudget", "firstMessage", "datingHelp",
            "expectedAgeRange", "isStudent");

    private static final Set<QuestionType> FORBIDDEN_TYPES = EnumSet.of(
            QuestionType.EMAIL, QuestionType.CODE, QuestionType.PHOTOS, QuestionType.DATE,
            QuestionType.HEIGHT, QuestionType.HEIGHT_RANGE, QuestionType.LOCATION,
            QuestionType.INTERSTITIAL);

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");
    private static final int MAX_TEXT = 60;

    private final long userId;
    private final Map<String, Object> values;
    private final String canonical;
    private final String inputHash;

    private TasteFacts(long userId, Map<String, Object> values, String promptVersion) {
        this.userId = userId;
        this.values = Collections.unmodifiableMap(new TreeMap<>(values));
        this.canonical = canonical(this.values);
        this.inputHash = sha256(promptVersion + "\n" + this.canonical);
    }

    /** @param answers AnswerStore.load() 的原始答案；本方法复制并脱敏，不修改入参。 */
    public static TasteFacts from(long userId, Map<String, Object> answers,
                                  String promptVersion, QuestionCatalog catalog) {
        TreeMap<String, Object> v = new TreeMap<>();
        if (answers != null) {
            for (String key : new TreeSet<>(answers.keySet())) {
                if (!ALLOWED.contains(key)) continue;
                if (catalog != null) {
                    if (catalog.isSensitive(key)) continue;
                    Optional<QuestionSpec> spec = catalog.find(key);
                    if (spec.isPresent() && FORBIDDEN_TYPES.contains(spec.get().type())) continue;
                }
                Object normalized = normalize(answers.get(key));
                if (normalized != null) v.put(key, normalized);
            }
            // 通勤口径：只发交通方式与分钟数，不发 placeId / 经纬度 / 具体地址。
            if (answers.get("location") instanceof Map<?, ?> loc) {
                TreeMap<String, Object> commute = new TreeMap<>();
                if (loc.get("mode") instanceof String mode && !mode.isBlank()) commute.put("mode", mode.strip());
                if (loc.get("minutes") instanceof Number minutes) commute.put("minutes", minutes.intValue());
                if (!commute.isEmpty()) v.put("commute", commute);
            }
        }
        return new TasteFacts(userId, v, promptVersion);
    }

    public long userId() {
        return userId;
    }

    /** 稳定有序的脱敏事实，可直接 Json.write() 后放进提示词。 */
    public Map<String, Object> values() {
        return values;
    }

    public String canonical() {
        return canonical;
    }

    /** 64 位小写十六进制 SHA-256。 */
    public String inputHash() {
        return inputHash;
    }

    private static Object normalize(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) {
            String cleaned = clean(s);
            return cleaned.isEmpty() ? null : cleaned;
        }
        if (raw instanceof Boolean || raw instanceof Number) return raw;
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object e : list) {
                Object n = normalize(e);
                if (n != null) out.add(n);
            }
            return out.isEmpty() ? null : List.copyOf(out);
        }
        if (raw instanceof Map<?, ?> map) {
            TreeMap<String, Object> out = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String key)) continue;
                Object n = normalize(e.getValue());
                if (n != null) out.put(key, n);
            }
            return out.isEmpty() ? null : out;
        }
        return null; // 未知类型一律丢弃
    }

    /** 自由文本兜底清洗：邮箱、手机号、URL、长数字（学号 / 证件号）一律打码并截断。 */
    private static String clean(String s) {
        String t = s.strip();
        t = EMAIL.matcher(t).replaceAll("[已隐藏]");
        t = PHONE.matcher(t).replaceAll("[已隐藏]");
        t = URL.matcher(t).replaceAll("[已隐藏]");
        t = LONG_DIGITS.matcher(t).replaceAll("[已隐藏]");
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    /** 与 JSON 库无关的确定性序列化，保证 hash 不受 Map 实现顺序影响。 */
    private static String canonical(Object o) {
        StringBuilder sb = new StringBuilder();
        append(sb, o);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() instanceof String k) sorted.put(k, e.getValue());
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, e.getKey());
                sb.append(':');
                append(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                append(sb, l.get(i));
            }
            sb.append(']');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o == null) {
            sb.append("null");
        } else {
            quote(sb, String.valueOf(o));
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
