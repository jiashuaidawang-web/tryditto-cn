package cn.aiditto.domain.relationship;

import java.util.regex.Pattern;

/**
 * A narrow backstop for obvious tokens. It does not decide that free text is safe to copy.
 * Occupation, opening lines, and custom hobbies are references or absent, not cleaned strings.
 */
final class SensitiveText {
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    private SensitiveText() {}

    static boolean containsContact(String text) {
        return EMAIL.matcher(text).find() || PHONE.matcher(text).find();
    }

    static String clean(String raw) {
        String t = raw.strip();
        t = EMAIL.matcher(t).replaceAll("");
        t = PHONE.matcher(t).replaceAll("");
        t = t.replaceAll("\\s+", " ").strip();
        return t.length() > 60 ? t.substring(0, 60).strip() : t;
    }
}
