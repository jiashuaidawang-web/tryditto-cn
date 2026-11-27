package cn.aiditto.domain.recall.v4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Separate self and preference texts. Free text and ungated values are not accepted here. */
public final class RecallVectorText {
    private RecallVectorText() {}

    public static String self(RecallSubject subject) {
        StringBuilder out = new StringBuilder();
        out.append("hobbies=");
        for (String hobby : new TreeSet<>(subject.hobbies())) {
            out.append(hobby).append(',');
        }
        out.append('\n');
        append(out, subject.selfEnums());
        return out.toString();
    }

    public static String preference(RecallSubject subject) {
        StringBuilder out = new StringBuilder();
        append(out, subject.preferenceEnums());
        return out.toString();
    }

    public static String digest(String text) {
        try {
            byte[] raw = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void append(StringBuilder out, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().startsWith("occupation.") || entry.getKey().startsWith("opening.")) {
                throw new IllegalArgumentException("free text is not a recall vector input");
            }
            out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
    }

    public static boolean mentions(String text, Set<String> forbidden) {
        for (String word : forbidden) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
