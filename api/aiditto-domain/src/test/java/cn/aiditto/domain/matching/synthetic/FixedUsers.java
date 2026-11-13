package cn.aiditto.domain.matching.synthetic;

import static cn.aiditto.domain.matching.synthetic.SyntheticPopulation.linked;
import static cn.aiditto.domain.matching.synthetic.SyntheticPopulation.t;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The three fixed partners (copied from {@code web/src/api/mock.ts}) and the four demo accounts. */
final class FixedUsers {
    private FixedUsers() {}

    static List<Map<String, Object>> partners() {
        return List.of(linxia(), zhouyu(), xumu());
    }

    static List<Map<String, Object>> demos() {
        return List.of(
                demo("demo1", "13800000001", "陈默", "Chen Mo", "man", List.of("women"), "2005-03-15", true, "junior", "beichen", "pku",
                        178, t("北辰大学", "Beichen University"), t("计算机", "Computer Science"), t("大三", "Junior"),
                        avatar("#a0c4ff", "#bdb2ff", "#eac4a4", "short", "#1b1716", "#2d3142", true, false),
                        List.of("guitar", "film", "mountain", "coffee")),
                demo("demo2", "13800000002", "苏晴", "Su Qing", "woman", List.of("men"), "2005-08-20", true, "junior", "qinghe", "tsinghua",
                        165, t("清河理工大学", "Qinghe Tech University"), t("建筑学", "Architecture"), t("大三", "Junior"),
                        avatar("#ffb4a2", "#ffd6a5", "#f6d1b5", "long", "#2b1d1a", "#ff6b9a", false, true),
                        List.of("film", "sunset", "guitar", "books")),
                demo("demo3", "13800000003", "江遥", "Jiang Yao", "nonbinary", List.of("everyone"), "2002-06-01", false, null, null, "wudaokou",
                        170, t("已工作", "Working"), t("设计师", "Designer"), t("已工作", "Working"),
                        avatar("#caffbf", "#9bf6ff", "#f1cfb0", "curly", "#4a2f27", "#06d6a0", false, false),
                        List.of("camera", "gamepad", "film", "plant")),
                demo("demo4", "13800000004", "叶可", "Ye Ke", "woman", List.of("men"), "2006-02-10", true, "sophomore", "jiangwan", "fudan",
                        162, t("江湾大学", "Jiangwan University"), t("心理学", "Psychology"), t("大二", "Sophomore"),
                        avatar("#fdffb6", "#ffc6ff", "#f9dcc4", "bob", "#3d2b1f", "#5b8def", false, false),
                        List.of("mountain", "sunset", "film", "coffee")));
    }

    /** Broad, easy-to-match answers: popular hobbies, wide ranges, no budget preference, "faster". */
    private static Map<String, Object> demo(String handle, String phone, String zh, String en, String gender, List<String> wants,
                                            String birthday, boolean student, String year, String schoolCode, String place, int height,
                                            Map<String, Object> school, Map<String, Object> major, Map<String, Object> yearLabel,
                                            Map<String, Object> avatar, List<String> scenes) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("isStudent", student ? "yes" : "no");
        a.put("name", zh);
        a.put("gender", gender);
        a.put("expectedGender", wants);
        a.put("birthday", birthday);
        a.put("hobbies", List.of("music", "travel", "movies"));
        a.put("musicSound", List.of("pop", "indie"));
        a.put("musicNowPlaying", "晴天");
        a.put("travelStyle", List.of("foodie", "city"));
        a.put("travelNext", "大理");
        a.put("moviesContent", List.of("romcom", "anime"));
        a.put("moviesLastWatch", "千与千寻");
        a.put("expectedAgeRange", List.of(18, 27));
        a.put("height", linked("cm", height, "unit", "cm"));
        a.put("heightRange", linked("any", true, "min", 150, "max", 200));
        if (student) a.put("schoolEmail", handle + "@demo." + schoolCode + ".edu.cn");
        else a.put("personalEmail", handle + "@example.com");
        a.put("location", linked("placeId", place, "mode", "transit", "minutes", 60));
        a.put("relationshipLooking", List.of("serious", "notSure"));
        a.put("matchingStyle", "faster");
        if (student) a.put("schoolYear", year);
        a.put("hearAbout", "friend");
        if (!student) a.put("occupation", "设计师");
        a.put("firstDateBudget", "noPref");
        a.put("dealbreakers", "");
        a.put("educationLevel", "college");
        a.put("educationImportance", "none");
        a.put("firstMessage", "最近在循环「晴天」，想听听你的歌单。");
        a.put("datingHelp", List.of("justDate"));

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", t(zh, en));
        c.put("age", Period.between(LocalDate.parse(birthday), SyntheticPopulation.REFERENCE_WEEK).getYears());
        c.put("school", school);
        c.put("major", major);
        c.put("year", yearLabel);
        c.put("extraTags", List.of(t("citywalk", "citywalk"), t("livehouse", "indie gigs"), t("火锅", "hotpot")));
        c.put("avatar", avatar);
        c.put("scenes", scenes);
        c.put("headline", t(zh + "就是你一直在等的那个约会对象！", en + " is the perfect date you’re looking for!"));
        c.put("blurb", List.of(
                t("爱笑、好奇心很重，耳机里永远在循环新发现的歌。", en + " is curious and quick to laugh. Always has a newly found song on repeat."),
                t("如果你喜欢愿意一起尝试新东西的人，" + zh + "就是你的菜。", "If you’re into trying new things together, " + en + "’s your kind of vibe.")));
        c.put("wechat", handle + "_demo_" + phone.substring(phone.length() - 4));

        Map<String, Object> u = new LinkedHashMap<>();
        u.put("handle", handle);
        u.put("kind", "demo");
        u.put("phone", phone);
        u.put("pool", SyntheticPopulation.poolOf(place));
        u.put("answers", a);
        u.put("card", c);
        return u;
    }

    private static Map<String, Object> avatar(String bg1, String bg2, String skin, String hair, String hairColor, String shirt,
                                              boolean glasses, boolean earring) {
        Map<String, Object> av = new LinkedHashMap<>();
        av.put("bg", List.of(bg1, bg2));
        av.put("skin", skin);
        av.put("hair", hair);
        av.put("hairColor", hairColor);
        av.put("shirt", shirt);
        if (glasses) av.put("glasses", true);
        if (earring) av.put("earring", true);
        return av;
    }

    private static Map<String, Object> partner(String handle, Map<String, Object> answers, Map<String, Object> card) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("handle", handle);
        u.put("kind", "partner");
        u.put("pool", "bj");
        u.put("answers", answers);
        u.put("card", card);
        return u;
    }

    private static Map<String, Object> reason(String zhText, String enText, String zhBasis, String enBasis) {
        return linked("text", t(zhText, enText), "basis", t(zhBasis, enBasis));
    }

    private static Map<String, Object> linxia() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("isStudent", "yes");
        a.put("name", "林夏");
        a.put("gender", "woman");
        a.put("expectedGender", List.of("men"));
        a.put("birthday", "2005-04-12");
        a.put("hobbies", List.of("cooking", "travel", "reading", "movies"));
        a.put("cookingStyle", List.of("asian", "comfort"));
        a.put("cookingSignatureDish", "番茄牛腩");
        a.put("travelStyle", List.of("city", "foodie"));
        a.put("travelNext", "冰岛");
        a.put("readingGenres", List.of("litfic", "poetry"));
        a.put("readingLastBook", "长安的荔枝");
        a.put("moviesContent", List.of("indie", "foreign"));
        a.put("moviesLastWatch", "爱乐之城");
        a.put("expectedAgeRange", List.of(19, 25));
        a.put("height", linked("cm", 165, "unit", "cm"));
        a.put("heightRange", linked("any", true, "min", 150, "max", 200));
        a.put("schoolEmail", "linxia@demo.beichen.edu.cn");
        a.put("location", linked("placeId", "pku", "mode", "transit", "minutes", 45));
        a.put("relationshipLooking", List.of("serious"));
        a.put("matchingStyle", "steady");
        a.put("schoolYear", "junior");
        a.put("hearAbout", "friend");
        a.put("firstDateBudget", "20to50");
        a.put("dealbreakers", "");
        a.put("educationLevel", "highSchool");
        a.put("educationImportance", "none");
        a.put("firstMessage", "饭桌上能把我逗笑的人最加分。");
        a.put("datingHelp", List.of("theDate"));

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", t("林夏", "Lin Xia"));
        c.put("age", 21);
        c.put("school", t("北辰大学", "Beichen University"));
        c.put("major", t("中文系", "Chinese Literature"));
        c.put("year", t("大三", "Junior"));
        c.put("extraTags", List.of(t("胶片相机", "film cameras"), t("citywalk", "citywalk"), t("火锅", "hotpot")));
        c.put("avatar", linked("bg", List.of("#ffb4a2", "#ffd6a5"), "skin", "#f6d1b5", "hair", "bob", "hairColor", "#2b1d1a",
                "shirt", "#5b8def", "earring", true));
        c.put("scenes", List.of("books", "coffee", "film", "noodles", "sunset"));
        c.put("headline", t("林夏就是你一直在等的那个约会对象！", "Lin Xia is the perfect date you’re looking for!"));
        c.put("blurb", List.of(
                t("她温柔又机灵，帆布包里永远揣着一本书。一杯咖啡能被她聊成三小时，从最近让她哭的那本小说，一路聊到楼下哪家面馆最好吃。",
                        "She’s warm, quick-witted and always has a paperback in her tote — the kind of person who turns a coffee into a three-hour talk about the last book that wrecked her."),
                t("你会喜欢她的好奇心——她不只问第一个问题，还会追问第二个。如果你喜欢认真听你说话的人，林夏就是你的菜。",
                        "You’ll love how curious she is. If you’re into someone who asks the second question, not just the first, Lin Xia’s your kind of vibe.")));
        c.put("wechat", "linxia_demo_0412");
        c.put("openers", List.of(
                t("问问她：如果要在地铁上递给陌生人一本书，她会选哪本？", "Ask her which book she’d hand a stranger on the subway."),
                t("她刚从大理回来，问问那卷还没冲洗的胶卷里拍了什么。", "She just got back from Dali — ask about the film roll she hasn’t developed yet.")));
        c.put("fallbackReasons", List.of(
                reason("你们这学期都想认真认识一个人，而不是一直刷来刷去。", "You both want something real this semester, not endless swiping.",
                        "你们都选了「认真谈恋爱」", "Both picked “Serious relationship”"),
                reason("你的幽默会被她接住：她写过「饭桌上能把我逗笑的人最加分」。", "Your humor lands with her: she wrote that she falls for people who make her laugh at dinner.",
                        "ta 的「想让对方知道的第一件事」", "Her “first thing your match should know”")));
        return partner("linxia", a, c);
    }

    private static Map<String, Object> zhouyu() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("isStudent", "yes");
        a.put("name", "周屿");
        a.put("gender", "man");
        a.put("expectedGender", List.of("women"));
        a.put("birthday", "2004-09-07");
        a.put("hobbies", List.of("music", "travel", "gym", "art"));
        a.put("musicSound", List.of("indie", "rock"));
        a.put("musicNowPlaying", "理想三旬");
        a.put("travelStyle", List.of("adventure", "road"));
        a.put("gymTrainingStyle", List.of("running"));
        a.put("gymTrainingFor", "跑完第一个半马");
        a.put("artMediums", List.of("drawing"));
        a.put("artMake", "给朋友画头像");
        a.put("expectedAgeRange", List.of(19, 25));
        a.put("height", linked("cm", 181, "unit", "cm"));
        a.put("heightRange", linked("any", true, "min", 150, "max", 200));
        a.put("schoolEmail", "zhouyu@demo.qinghe.edu.cn");
        a.put("location", linked("placeId", "tsinghua", "mode", "transit", "minutes", 45));
        a.put("relationshipLooking", List.of("serious"));
        a.put("matchingStyle", "steady");
        a.put("schoolYear", "senior");
        a.put("hearAbout", "poster");
        a.put("firstDateBudget", "20to50");
        a.put("dealbreakers", "");
        a.put("educationLevel", "highSchool");
        a.put("educationImportance", "none");
        a.put("firstMessage", "喜欢会注意小细节的人。");
        a.put("datingHelp", List.of("convo"));

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", t("周屿", "Zhou Yu"));
        c.put("age", 22);
        c.put("school", t("清河理工大学", "Qinghe Tech University"));
        c.put("major", t("建筑学院", "Architecture"));
        c.put("year", t("大四", "Senior"));
        c.put("extraTags", List.of(t("速写", "sketching"), t("livehouse", "indie gigs"), t("晨跑", "early runs")));
        c.put("avatar", linked("bg", List.of("#a0c4ff", "#bdb2ff"), "skin", "#eac4a4", "hair", "short", "hairColor", "#1b1716",
                "shirt", "#2d3142", "glasses", true));
        c.put("scenes", List.of("guitar", "mountain", "camera", "bike", "coffee"));
        c.put("headline", t("周屿就是你一直在等的那个约会对象！", "Zhou Yu is the perfect date you’re looking for!"));
        c.put("blurb", List.of(
                t("他安静、有点书呆子气，但其实很好笑：会在餐巾纸上画建筑速写，早上 6 点去操场跑步，海淀每家小 livehouse 都门儿清。",
                        "He’s calm, a little nerdy and secretly hilarious — sketches buildings on napkins, runs at 6am, and knows every small livehouse in Haidian."),
                t("你会喜欢他的稳。如果你想找一个第一次约会还没结束、就开始计划第二次的人，周屿就是你的菜。",
                        "You’ll love how steady he is. If you want someone who plans the second date before the first one ends, Zhou Yu’s your kind of vibe.")));
        c.put("wechat", "zhouyu_demo_0907");
        c.put("openers", List.of(
                t("问问他：校园里哪栋楼他最想重新设计？为什么？", "Ask him which campus building he’d tear down and why."),
                t("他今年看了 40 场 livehouse，问问他你下一个该听哪支乐队。", "He’s been to 40 livehouse shows this year — ask for the one band you should hear next.")));
        c.put("fallbackReasons", List.of(
                reason("你们都选了「稳稳的节奏」——都不想太快。", "You both pick “a steady mix” — neither of you wants to rush.",
                        "你们都回答了「希望怎么帮你匹配」", "Both answered “how do u want Ditto to match u”"),
                reason("他喜欢会注意小细节的人，而你的回答里全是细节。", "He likes people who notice small things; your answers are full of them.",
                        "ta 的「雷区」+ 你的「第一句话」", "His dealbreakers + your first message")));
        return partner("zhouyu", a, c);
    }

    private static Map<String, Object> xumu() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("isStudent", "yes");
        a.put("name", "许沐");
        a.put("gender", "nonbinary");
        a.put("expectedGender", List.of("everyone"));
        a.put("birthday", "2005-11-23");
        a.put("hobbies", List.of("cooking", "music", "gaming", "movies"));
        a.put("cookingStyle", List.of("comfort"));
        a.put("cookingSignatureDish", "葱油拌面");
        a.put("musicSound", List.of("indie", "electronic"));
        a.put("gamingGenres", List.of("moba", "cozy"));
        a.put("gamingNowPlaying", "星露谷物语");
        a.put("moviesContent", List.of("a24", "anime"));
        a.put("moviesLastWatch", "瞬息全宇宙");
        a.put("expectedAgeRange", List.of(18, 24));
        a.put("height", linked("cm", 172, "unit", "cm"));
        a.put("heightRange", linked("any", true, "min", 150, "max", 200));
        a.put("schoolEmail", "xumu@demo.hdmedia.edu.cn");
        a.put("location", linked("placeId", "ruc", "mode", "transit", "minutes", 45));
        a.put("relationshipLooking", List.of("serious", "friends"));
        a.put("matchingStyle", "fewer");
        a.put("schoolYear", "sophomore");
        a.put("hearAbout", "bilibili");
        a.put("firstDateBudget", "under20");
        a.put("dealbreakers", "");
        a.put("educationLevel", "highSchool");
        a.put("educationImportance", "none");
        a.put("firstMessage", "最近在玩星露谷物语，双排随时。");
        a.put("datingHelp", List.of("convo", "intoMe"));

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", t("许沐", "Xu Mu"));
        c.put("age", 20);
        c.put("school", t("海淀传媒学院", "Haidian College of Media"));
        c.put("major", t("新闻学院", "Journalism"));
        c.put("year", t("大二", "Sophomore"));
        c.put("extraTags", List.of(t("播客", "podcasts"), t("猫", "cats"), t("深夜面馆", "late-night noodles")));
        c.put("avatar", linked("bg", List.of("#caffbf", "#9bf6ff"), "skin", "#f1cfb0", "hair", "curly", "hairColor", "#4a2f27",
                "shirt", "#ff6b9a"));
        c.put("scenes", List.of("gamepad", "cat", "noodles", "film", "plant"));
        c.put("headline", t("许沐就是你一直在等的那个约会对象！", "Xu Mu is the perfect date you’re looking for!"));
        c.put("blurb", List.of(
                t("ta 机灵又爱玩，在学校做一档小播客：既能和你争论一部电影的结局，也能在排位赛里带你上分。",
                        "They’re sharp, playful and run a tiny campus podcast — equally happy debating a film’s ending or carrying a team in a ranked match."),
                t("你会发现和 ta 聊天毫不费力。如果你喜欢有观点、又会偷偷喂流浪猫的人，许沐就是你的菜。",
                        "You’ll love how easy they are to talk to. If you’re into someone with opinions and a soft spot for stray cats, Xu Mu’s your kind of vibe.")));
        c.put("wechat", "xumu_demo_1123");
        c.put("openers", List.of(
                t("问问 ta 最近一期播客聊了什么。", "Ask what the last podcast episode they recorded was about."),
                t("ta 对电影结局很有看法，带一个你最讨厌的结局去聊。", "They have strong opinions on film endings — bring one you hated.")));
        c.put("fallbackReasons", List.of(
                reason("你们都想找一个能边吃边聊、聊得很轻松的人。", "You both said you want someone easy to talk to over food.",
                        "你们的爱好里都有「吃」", "Both chose food-related hobbies"),
                reason("你们都不着急——都选了「少而精」。", "Neither of you is in a hurry — you both picked fewer, better matches.",
                        "你们都回答了「希望怎么帮你匹配」", "Both answered “how do u want Ditto to match u”")));
        return partner("xumu", a, c);
    }
}
