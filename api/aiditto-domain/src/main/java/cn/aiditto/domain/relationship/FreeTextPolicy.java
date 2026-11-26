package cn.aiditto.domain.relationship;

/**
 * Production extraction does not export free text. Letter class, length, and regular expressions
 * do not prove that a string contains no private information. The evaluation path may expose a
 * fictional sentence that passes {@link #evaluationMayExpose}; that check is not an anonymizer.
 */
public final class FreeTextPolicy {
    private FreeTextPolicy() {}

    public static boolean productionExportsFreeText() {
        return false;
    }

    public static boolean exportable(String text) {
        return evaluationMayExpose(text);
    }

    public static boolean evaluationMayExpose(String text) {
        if (text == null) return false;
        String value = text.strip();
        if (value.length() < 2 || value.length() > 40) return false;
        return value.codePoints().allMatch(FreeTextPolicy::letterOrLightPunctuation);
    }

    private static boolean letterOrLightPunctuation(int codePoint) {
        return Character.isLetter(codePoint) || codePoint == ' ' || codePoint == '，' || codePoint == '。';
    }
}
