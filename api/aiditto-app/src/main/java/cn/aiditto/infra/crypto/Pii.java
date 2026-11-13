package cn.aiditto.infra.crypto;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizing and masking phone numbers and emails (技术方案 §9: logs and API only ever show masks). */
public final class Pii {
    private static final Pattern CN_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private Pii() {}

    public static boolean isCnMobile(String phone) {
        return phone != null && CN_MOBILE.matcher(phone).matches();
    }

    /** Digits only; a leading 86 / +86 country code is dropped from 13-digit numbers. */
    public static String normalizePhone(String raw) {
        if (raw == null) return "";
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 13 && d.startsWith("86")) d = d.substring(2);
        return d;
    }

    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    /** 13800000001 → 138****0001. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** alex@pku.edu.cn → a***@pku.edu.cn. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
