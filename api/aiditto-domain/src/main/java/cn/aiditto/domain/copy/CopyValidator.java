package cn.aiditto.domain.copy;

import cn.aiditto.domain.copy.MatchCopy.Reason;
import cn.aiditto.domain.copy.MatchCopy.ViewerCopy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Program-side validation of match copy (技术方案 §8.4, §8.5). The model's output is only trusted after
 * {@link #extractJson} → JSON parsing (in the app) → {@link #validate}. Any issue fails the attempt.
 */
public final class CopyValidator {
    public static final int HEADLINE_MAX = 30;
    public static final int REASON_MAX = 60;
    public static final int OPENER_MAX = 60;
    public static final int BLURB_MAX = 120;
    public static final int REASONS_MIN = 2;
    public static final int REASONS_MAX = 3;
    public static final int OPENERS = 2;
    public static final int BLURBS = 2;

    public static final String PARTNER = "{{partner}}";
    public static final String ME = "{{me}}";
    /** Placeholders count as a three-character name when measuring length. */
    static final String NAME_STAND_IN = "某某某";

    public enum Rule { STRUCTURE, LENGTH, CITES, VENUE, BANNED_WORD, CONTACT, NAME, PLACEHOLDER, UNKNOWN_QUOTE }

    public record Issue(String path, Rule rule, String detail) {
        @Override
        public String toString() {
            return path + ": " + detail;
        }
    }

    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*\\s*\\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern BAD_PLACEHOLDER = Pattern.compile("\\{\\{(?!partner\\}\\}|me\\}\\})[^}]*\\}\\}");
    private static final Pattern QUOTED = Pattern.compile("《([^》]+)》|「([^」]+)」|『([^』]+)』|“([^”]+)”|\"([^\"]+)\"");
    private static final List<Pattern> CONTACT = List.of(
            Pattern.compile("(?<!\\d)1[3-9]\\d(?:[\\s-]?\\d){8}(?!\\d)"),
            Pattern.compile("(?i)微信|威信|薇信|weixin|wechat|v信|(?<![a-z])vx(?![a-z])|(?<![a-z])wx(?![a-z])|wxid_"),
            Pattern.compile("(?i)(qq|扣扣)\\s*[:：号]?\\s*\\d{5,11}"),
            Pattern.compile("(?i)https?://|www\\.|(?<![a-z0-9])[a-z0-9-]+\\.(com|cn|net|org|io|me|top|xyz)(?![a-z])"),
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+"));

    private CopyValidator() {}

    /**
     * Strips what models wrap around JSON: trims; if there is a ``` fence, keeps the first fenced block;
     * then cuts from the first {@code {} to the last {@code }}. Returns the input trimmed when no braces exist.
     */
    public static String extractJson(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        Matcher m = FENCE.matcher(s);
        if (m.find()) s = m.group(1).strip();
        int open = s.indexOf('{');
        int close = s.lastIndexOf('}');
        return open >= 0 && close > open ? s.substring(open, close + 1) : s;
    }

    public static List<Issue> validate(MatchCopy copy, CopyContext ctx) {
        List<Issue> out = new ArrayList<>();
        if (copy == null) {
            out.add(new Issue("$", Rule.STRUCTURE, "missing copy"));
            return out;
        }
        viewer("low", copy.forLow(), ctx, out);
        viewer("high", copy.forHigh(), ctx, out);
        if (copy.venueId() == null || !ctx.venueIds().contains(copy.venueId())) {
            out.add(new Issue("venueId", Rule.VENUE, "venue " + copy.venueId() + " is not one of " + ctx.venueIds()));
        }
        text("activity", copy.activity(), BLURB_MAX, ctx, out, false);
        text("extra", copy.extra(), BLURB_MAX, ctx, out, false);
        return out;
    }

    public static boolean isValid(MatchCopy copy, CopyContext ctx) {
        return validate(copy, ctx).isEmpty();
    }

    /** One line for the retry prompt: "上次输出的问题：…" (技术方案 §8.3). */
    public static String summary(List<Issue> issues) {
        return issues.stream().map(Issue::toString).collect(Collectors.joining("；"));
    }

    /** Text-level checks only (banned words, contact info, names, placeholders, quotes, length). */
    public static List<Issue> checkText(String path, String s, int max, CopyContext ctx) {
        List<Issue> out = new ArrayList<>();
        text(path, s, max, ctx, out, true);
        return out;
    }

    private static void viewer(String who, ViewerCopy v, CopyContext ctx, List<Issue> out) {
        if (v == null) {
            out.add(new Issue(who, Rule.STRUCTURE, "missing viewer copy"));
            return;
        }
        text(who + ".headline", v.headline(), HEADLINE_MAX, ctx, out, true);
        count(who + ".blurb", v.blurb(), BLURBS, BLURBS, out);
        if (v.blurb() != null) {
            for (int i = 0; i < v.blurb().size(); i++) text(who + ".blurb[" + i + "]", v.blurb().get(i), BLURB_MAX, ctx, out, true);
        }
        count(who + ".openers", v.openers(), OPENERS, OPENERS, out);
        if (v.openers() != null) {
            for (int i = 0; i < v.openers().size(); i++) text(who + ".openers[" + i + "]", v.openers().get(i), OPENER_MAX, ctx, out, true);
        }
        count(who + ".reasons", v.reasons(), REASONS_MIN, REASONS_MAX, out);
        if (v.reasons() != null) {
            for (int i = 0; i < v.reasons().size(); i++) reason(who + ".reasons[" + i + "]", v.reasons().get(i), ctx, out);
        }
    }

    private static void count(String path, List<?> list, int min, int max, List<Issue> out) {
        int n = list == null ? 0 : list.size();
        if (n < min || n > max) {
            out.add(new Issue(path, Rule.STRUCTURE, "expected " + (min == max ? min : min + "–" + max) + " items, got " + n));
        }
    }

    private static void reason(String path, Reason r, CopyContext ctx, List<Issue> out) {
        if (r == null) {
            out.add(new Issue(path, Rule.STRUCTURE, "missing reason"));
            return;
        }
        text(path + ".text", r.text(), REASON_MAX, ctx, out, true);
        text(path + ".basis", r.basis(), REASON_MAX, ctx, out, true);
        if (r.cites() == null || r.cites().isEmpty()) {
            out.add(new Issue(path + ".cites", Rule.CITES, "no cited fields"));
            return;
        }
        boolean anyShared = false;
        for (String f : r.cites()) {
            if (!ctx.lowFields().contains(f) || !ctx.highFields().contains(f)) {
                out.add(new Issue(path + ".cites", Rule.CITES, "cites " + f + ", which is not filled in by both people"));
            }
            anyShared |= ctx.sharedFields().contains(f);
        }
        if (!anyShared) {
            out.add(new Issue(path + ".cites", Rule.CITES, "none of " + r.cites() + " is something the two share"));
        }
    }

    private static void text(String path, String s, int max, CopyContext ctx, List<Issue> out, boolean required) {
        if (s == null || s.isBlank()) {
            if (required) out.add(new Issue(path, Rule.STRUCTURE, "empty"));
            return;
        }
        String measured = s.replace(PARTNER, NAME_STAND_IN).replace(ME, NAME_STAND_IN);
        int len = measured.codePointCount(0, measured.length());
        if (len > max) out.add(new Issue(path, Rule.LENGTH, len + " characters, max " + max));

        Optional<BannedWords.Hit> hit = BannedWords.find(s);
        hit.ifPresent(h -> out.add(new Issue(path, Rule.BANNED_WORD, h.category() + " term \"" + h.term() + "\"")));
        for (Pattern p : CONTACT) {
            if (p.matcher(s).find()) {
                out.add(new Issue(path, Rule.CONTACT, "looks like contact info"));
                break;
            }
        }
        if (BAD_PLACEHOLDER.matcher(s).find()) out.add(new Issue(path, Rule.PLACEHOLDER, "unknown placeholder"));
        for (String name : ctx.forbiddenNames()) {
            if (!name.isEmpty() && s.contains(name)) {
                out.add(new Issue(path, Rule.NAME, "contains a real name; use " + PARTNER));
                break;
            }
        }
        Matcher q = QUOTED.matcher(s);
        while (q.find()) {
            String term = firstGroup(q).strip();
            if (term.isEmpty() || term.equals(PARTNER) || term.equals(ME)) continue;
            if (ctx.knownTerms().stream().noneMatch(k -> k.contains(term))) {
                out.add(new Issue(path, Rule.UNKNOWN_QUOTE, "quoted \"" + term + "\" does not appear in the input"));
            }
        }
    }

    private static String firstGroup(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) if (m.group(i) != null) return m.group(i);
        return "";
    }
}
