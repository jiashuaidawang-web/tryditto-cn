package cn.aiditto.domain.relationship;

/**
 * Decides whether a free-text span may be shown to a model as untrusted data.
 * Rejection is omission. It is not a claim that the remaining text has been anonymized.
 */
public final class FreeTextPolicy {
    private FreeTextPolicy() {}

    public static boolean exportable(String text) {
        if (text == null) return false;
        String value = text.strip();
        if (value.length() < 2 || value.length() > 40) return false;
        return value.codePoints().allMatch(FreeTextPolicy::letterOrLightPunctuation);
    }

    private static boolean letterOrLightPunctuation(int codePoint) {
        return Character.isLetter(codePoint) || codePoint == ' ' || codePoint == '，' || codePoint == '。';
    }
}
