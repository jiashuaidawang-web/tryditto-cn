package cn.aiditto.domain.copy;

import cn.aiditto.domain.copy.MatchCopy.Reason;
import cn.aiditto.domain.copy.MatchCopy.ViewerCopy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CopyFixtures {
    private CopyFixtures() {}

    static final ProfileFacts LOW = new ProfileFacts(1, "小林", true, "junior",
            Set.of("music", "reading"),
            Map.of("musicSound", Set.of("indie", "rock")),
            Map.of("musicNowPlaying", "晴天", "readingLastBook", "活着"),
            Set.of("serious"), "steady", "20to50", "饭桌上能把我逗笑的人最加分。");

    static final ProfileFacts HIGH = new ProfileFacts(2, "阿周", true, "junior",
            Set.of("music", "gym"),
            Map.of("musicSound", Set.of("indie")),
            Map.of("gymTrainingFor", "跑完第一个半马"),
            Set.of("serious", "casual"), "steady", "noPref", null);

    static final List<VenueOption> VENUES = List.of(
            new VenueOption("v1", "树洞咖啡", "一杯饮品，约 45 分钟", "聊得来的话，绕湖走一圈"),
            new VenueOption("v2", "荷塘书咖", "一杯咖啡加散步，约 1 小时", ""));

    static CopyContext ctx() {
        return CopyContext.of(LOW, HIGH, VENUES, Set.of("小王"));
    }

    /** What a well-behaved model returns for {@link #LOW} / {@link #HIGH}. */
    static MatchCopy valid() {
        ViewerCopy forLow = new ViewerCopy(
                "{{partner}}就是你一直在等的那个人！",
                List.of("ta 是大三学生，平时喜欢音乐和健身。", "你们都喜欢「独立」音乐，见面的前十分钟不愁没话聊。"),
                List.of(new Reason("你们都选了「音乐」，而且都喜欢「独立」。", "你的「音乐」+ {{partner}}的「音乐」", List.of("hobbies", "musicSound")),
                        new Reason("你们都在找「认真谈恋爱」。", "你们都选了「认真谈恋爱」", List.of("relationshipLooking"))),
                List.of("问问 ta「跑完第一个半马」这个目标练到哪一步了。", "问问 ta 周末一般怎么过。"));
        ViewerCopy forHigh = new ViewerCopy(
                "去见见{{partner}}吧",
                List.of("ta 是大三学生，平时喜欢音乐和阅读。", "你们想要的节奏一样，都是「稳稳的节奏」。"),
                List.of(new Reason("你们想要的节奏一样：「稳稳的节奏」。", "你们都选了「稳稳的节奏」", List.of("matchingStyle")),
                        new Reason("你们都在读大三，聊得到一块。", "你们都是大三", List.of("schoolYear", "isStudent")),
                        new Reason("约在你们中间的地方，谁都不用跑太远。", "你们各自能接受的路程", List.of("location"))),
                List.of("问问 ta 为什么最近一直在循环「晴天」。", "问问 ta 读完「活着」最大的感受。"));
        return new MatchCopy(forLow, forHigh, "v1", "一杯饮品，约 45 分钟", "聊得来的话，绕湖走一圈", MatchCopy.Source.AI);
    }

    static MatchCopy withLow(MatchCopy c, ViewerCopy low) {
        return new MatchCopy(low, c.forHigh(), c.venueId(), c.activity(), c.extra(), c.source());
    }

    static ViewerCopy headline(ViewerCopy v, String h) {
        return new ViewerCopy(h, v.blurb(), v.reasons(), v.openers());
    }

    static ViewerCopy reasons(ViewerCopy v, List<Reason> r) {
        return new ViewerCopy(v.headline(), v.blurb(), r, v.openers());
    }

    static ViewerCopy openers(ViewerCopy v, List<String> o) {
        return new ViewerCopy(v.headline(), v.blurb(), v.reasons(), o);
    }

    static ViewerCopy blurb(ViewerCopy v, List<String> b) {
        return new ViewerCopy(v.headline(), b, v.reasons(), v.openers());
    }

    /** Fixture answers → the whitelisted facts copy may use. */
    @SuppressWarnings("unchecked")
    static ProfileFacts facts(Map<String, Object> user, long id) {
        Map<String, Object> a = (Map<String, Object>) user.get("answers");
        Map<String, Set<String>> options = new HashMap<>();
        Map<String, String> texts = new HashMap<>();
        for (String h : (List<String>) a.get("hobbies")) {
            List<String> branch = Labels.BRANCHES.get(h);
            if (branch == null) continue;
            if (a.get(branch.get(0)) instanceof List<?> l) options.put(branch.get(0), Set.copyOf((List<String>) l));
            if (a.get(branch.get(1)) instanceof String s) texts.put(branch.get(1), s);
        }
        return new ProfileFacts(id, (String) a.get("name"), "yes".equals(a.get("isStudent")), (String) a.get("schoolYear"),
                Set.copyOf((List<String>) a.get("hobbies")), options, texts, Set.copyOf((List<String>) a.get("relationshipLooking")),
                (String) a.get("matchingStyle"), (String) a.get("firstDateBudget"), (String) a.get("firstMessage"));
    }
}
