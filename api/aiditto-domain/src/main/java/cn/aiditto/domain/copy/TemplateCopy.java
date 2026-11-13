package cn.aiditto.domain.copy;

import cn.aiditto.domain.copy.MatchCopy.Reason;
import cn.aiditto.domain.copy.MatchCopy.ViewerCopy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic fallback copy built only from facts both people entered (技术方案 §8.5), the Java port of
 * {@code reasonsFor}/{@code hobbyReason} in {@code web/src/api/mock.ts} minus the invented details.
 * Every line is passed through {@link CopyValidator#checkText} and skipped if it fails, so the result
 * always validates against {@link CopyContext#of} for the same pair.
 */
public final class TemplateCopy {

    private TemplateCopy() {}

    public static MatchCopy build(ProfileFacts low, ProfileFacts high, List<VenueOption> venues, Lang lang) {
        if (venues.isEmpty()) throw new IllegalArgumentException("at least one venue candidate is required");
        CopyContext ctx = CopyContext.of(low, high, venues, Set.of());
        VenueOption venue = venues.get(0);
        return new MatchCopy(
                viewer(low, high, ctx, lang),
                viewer(high, low, ctx, lang),
                venue.id(),
                venue.defaultActivity().isBlank() ? (lang == Lang.EN ? "One drink, about 45 min" : "一杯饮品，约 45 分钟") : venue.defaultActivity(),
                venue.defaultExtra(),
                MatchCopy.Source.TEMPLATE);
    }

    private static ViewerCopy viewer(ProfileFacts me, ProfileFacts partner, CopyContext ctx, Lang lang) {
        boolean en = lang == Lang.EN;
        String headline = en ? "Say hi to {{partner}}!" : "{{partner}} 就是你一直在等的那个约会对象！";
        return new ViewerCopy(headline, blurb(me, partner, ctx, lang), reasons(me, partner, ctx, lang), openers(me, partner, ctx, lang));
    }

    // ── reasons ─────────────────────────────────────────────────────────────────────────────────

    static List<Reason> reasons(ProfileFacts me, ProfileFacts p, CopyContext ctx, Lang lang) {
        boolean en = lang == Lang.EN;
        List<Reason> out = new ArrayList<>();
        for (String h : sharedHobbies(me, p)) {
            String label = Labels.hobby(h, lang);
            if (label == null) continue;
            String branch = Labels.BRANCHES.containsKey(h) ? Labels.BRANCHES.get(h).get(0) : null;
            String option = branch == null ? null : sharedOption(me, p, branch);
            String optLabel = option == null ? null : Labels.of(branch, option, lang);
            String text;
            List<String> cites;
            if (optLabel != null) {
                text = en ? "You both picked “" + label + "”, and both like “" + optLabel + "”."
                        : "你们都选了「" + label + "」，而且都喜欢「" + optLabel + "」。";
                cites = List.of("hobbies", branch);
            } else {
                text = en ? "You both picked “" + label + "”." : "你们都选了「" + label + "」。";
                cites = List.of("hobbies");
            }
            String basis = en ? "Your “" + label + "” + {{partner}}’s" : "你的「" + label + "」+ {{partner}}的「" + label + "」";
            add(out, new Reason(text, basis, cites), ctx);
        }
        String look = sharedFirst(me.relationshipLooking(), p.relationshipLooking(),
                List.of("serious", "lifePartner", "friends", "casual", "notSure"));
        if (look != null) {
            String l = Labels.of("relationshipLooking", look, lang);
            add(out, new Reason(en ? "You’re both looking for “" + l + "”." : "你们都在找「" + l + "」。",
                    en ? "Both picked “" + l + "”" : "你们都选了「" + l + "」", List.of("relationshipLooking")), ctx);
        }
        if (me.matchingStyle() != null && me.matchingStyle().equals(p.matchingStyle())) {
            String l = Labels.of("matchingStyle", me.matchingStyle(), lang);
            add(out, new Reason(en ? "You both want the same pace: “" + l + "”." : "你们想要的节奏一样：「" + l + "」。",
                    en ? "Both answered the matching-pace question" : "你们都选了「" + l + "」", List.of("matchingStyle")), ctx);
        }
        if (me.budget() != null && me.budget().equals(p.budget()) && !"noPref".equals(me.budget())) {
            add(out, new Reason(en ? "You picked the same first-date budget." : "第一次约会的预算，你们想到一块去了。",
                    en ? "Both answered the budget question" : "你们都选了同一档首约预算", List.of("firstDateBudget")), ctx);
        }
        if (me.student() && p.student() && me.schoolYear() != null && me.schoolYear().equals(p.schoolYear())) {
            String l = Labels.of("schoolYear", me.schoolYear(), lang);
            add(out, new Reason(en ? "You’re both in the same year: " + l + "." : "你们都在读" + l + "，聊得到一块。",
                    en ? "Both students, same year" : "你们都是" + l, List.of("schoolYear")), ctx);
        }
        add(out, new Reason(en ? "Your date areas are close, so meeting up is easy." : "你们想约会的地方离得不远，见一面很方便。",
                en ? "Both picked nearby date areas" : "你们选的约会范围有交集", List.of(CopyContext.LOCATION)), ctx);
        add(out, new Reason(en ? "Neither of you has to travel far: you meet halfway." : "约在你们中间的地方，谁都不用跑太远。",
                en ? "Your travel times" : "你们各自能接受的路程", List.of(CopyContext.LOCATION)), ctx);
        return out.subList(0, Math.min(CopyValidator.REASONS_MAX, out.size()));
    }

    private static void add(List<Reason> out, Reason r, CopyContext ctx) {
        if (CopyValidator.checkText("r", r.text(), CopyValidator.REASON_MAX, ctx).isEmpty()
                && CopyValidator.checkText("b", r.basis(), CopyValidator.REASON_MAX, ctx).isEmpty()) {
            out.add(r);
        }
    }

    // ── openers ─────────────────────────────────────────────────────────────────────────────────

    static List<String> openers(ProfileFacts me, ProfileFacts p, CopyContext ctx, Lang lang) {
        boolean en = lang == Lang.EN;
        Set<String> out = new LinkedHashSet<>();
        for (String h : Labels.HOBBY_ORDER) {
            List<String> branch = Labels.BRANCHES.get(h);
            String answer = p.hobbyTexts().get(branch.get(1));
            if (answer == null) continue;
            String line = en ? openerEn(branch.get(1), answer) : openerZh(branch.get(1), answer);
            if (line != null && CopyValidator.checkText("o", line, CopyValidator.OPENER_MAX, ctx).isEmpty()) out.add(line);
        }
        for (String h : sharedHobbies(me, p)) {
            String label = Labels.hobby(h, lang);
            if (label == null) continue;
            String line = en ? "Start with something you both love: “" + label + "”." : "从你们都喜欢的「" + label + "」聊起吧。";
            if (CopyValidator.checkText("o", line, CopyValidator.OPENER_MAX, ctx).isEmpty()) out.add(line);
        }
        out.add(en ? "Ask {{partner}} about the best part of their week." : "问问 ta 这周最开心的一件事。");
        out.add(en ? "Ask {{partner}} how they usually spend a free Saturday." : "问问 ta 周末一般怎么过。");
        return new ArrayList<>(out).subList(0, CopyValidator.OPENERS);
    }

    private static String openerZh(String question, String a) {
        return switch (question) {
            case "cookingSignatureDish" -> "问问 ta 的拿手菜「" + a + "」是怎么做的。";
            case "musicNowPlaying" -> "问问 ta 为什么最近一直在循环「" + a + "」。";
            case "travelNext" -> "问问 ta 为什么下一站想去「" + a + "」。";
            case "readingLastBook" -> "问问 ta 读完「" + stripBookMarks(a) + "」最大的感受。";
            case "gymTrainingFor" -> "问问 ta「" + a + "」这个目标练到哪一步了。";
            case "gamingNowPlaying" -> "问问 ta 最近在玩的「" + a + "」好玩在哪。";
            case "moviesLastWatch" -> "问问 ta 最近看的「" + stripBookMarks(a) + "」好在哪。";
            case "artMake" -> "问问 ta 最近在做的「" + a + "」。";
            default -> null;
        };
    }

    private static String openerEn(String question, String a) {
        return switch (question) {
            case "cookingSignatureDish" -> "Ask how they make their signature “" + a + "”.";
            case "musicNowPlaying" -> "Ask why “" + a + "” is on repeat.";
            case "travelNext" -> "Ask why “" + a + "” is next on their list.";
            case "readingLastBook" -> "Ask what stayed with them from “" + stripBookMarks(a) + "”.";
            case "gymTrainingFor" -> "Ask how training for “" + a + "” is going.";
            case "gamingNowPlaying" -> "Ask what makes “" + a + "” so good.";
            case "moviesLastWatch" -> "Ask what they loved about “" + stripBookMarks(a) + "”.";
            case "artMake" -> "Ask about the “" + a + "” they’re making.";
            default -> null;
        };
    }

    private static String stripBookMarks(String s) {
        return s.replace("《", "").replace("》", "").strip();
    }

    // ── blurb ───────────────────────────────────────────────────────────────────────────────────

    static List<String> blurb(ProfileFacts me, ProfileFacts p, CopyContext ctx, Lang lang) {
        boolean en = lang == Lang.EN;
        List<String> labels = new ArrayList<>();
        for (String h : orderedHobbies(p.hobbies())) {
            String l = Labels.hobby(h, lang);
            if (l != null && CopyValidator.checkText("h", l, 20, ctx).isEmpty()) labels.add(l);
            if (labels.size() == 3) break;
        }
        String who;
        if (p.student()) {
            String year = Labels.of("schoolYear", p.schoolYear(), lang);
            who = en ? "{{partner}} is a " + (year == null ? "student" : year.toLowerCase() + " student") : "ta 是" + (year == null ? "在读学生" : year + "学生");
        } else {
            who = en ? "{{partner}} is already working" : "ta 已经工作了";
        }
        String first = labels.isEmpty()
                ? (en ? who + "." : who + "。")
                : (en ? who + " who's into " + String.join(", ", labels) + "." : who + "，平时喜欢" + String.join("、", labels) + "。");
        List<String> shared = sharedHobbies(me, p);
        String sharedLabel = shared.isEmpty() ? null : Labels.hobby(shared.get(0), lang);
        String second = sharedLabel != null
                ? (en ? "You both love “" + sharedLabel + "”, so the first ten minutes are easy." : "你们都喜欢「" + sharedLabel + "」，见面的前十分钟不愁没话聊。")
                : (en ? "No need to prepare much: one drink is just the right length for a first date." : "第一次见面不用准备太多，一杯饮品的时间刚刚好。");
        List<String> out = new ArrayList<>();
        for (String s : List.of(first, second)) {
            boolean ok = CopyValidator.checkText("bl", s, CopyValidator.BLURB_MAX, ctx).isEmpty();
            out.add(ok ? s : (en ? "{{partner}} is looking forward to meeting you." : "ta 也在期待这次见面。"));
        }
        return out;
    }

    // ── shared facts ────────────────────────────────────────────────────────────────────────────

    static List<String> sharedHobbies(ProfileFacts a, ProfileFacts b) {
        List<String> out = new ArrayList<>();
        for (String h : orderedHobbies(a.hobbies())) if (b.hobbies().contains(h)) out.add(h);
        return out;
    }

    /** Bank order first, then custom hobbies alphabetically. */
    private static List<String> orderedHobbies(Set<String> hobbies) {
        List<String> out = new ArrayList<>();
        for (String h : Labels.HOBBY_ORDER) if (hobbies.contains(h)) out.add(h);
        List<String> custom = new ArrayList<>();
        for (String h : hobbies) if (!Labels.HOBBY_ORDER.contains(h)) custom.add(h);
        Collections.sort(custom);
        out.addAll(custom);
        return out;
    }

    private static String sharedOption(ProfileFacts a, ProfileFacts b, String question) {
        Set<String> x = a.hobbyOptions().get(question);
        Set<String> y = b.hobbyOptions().get(question);
        if (x == null || y == null) return null;
        return x.stream().filter(y::contains).sorted().findFirst().orElse(null);
    }

    private static String sharedFirst(Set<String> a, Set<String> b, List<String> order) {
        for (String o : order) if (a.contains(o) && b.contains(o)) return o;
        return null;
    }
}
