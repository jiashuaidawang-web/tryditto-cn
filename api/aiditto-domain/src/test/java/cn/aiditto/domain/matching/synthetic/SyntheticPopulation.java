package cn.aiditto.domain.matching.synthetic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generator for {@code synthetic/users-v1.json} (技术方案 §8.6), fixed seed. Everyone is fictional:
 * names are random surname + given-name pairs, schools are invented, WeChat ids end in {@code _demo_NNNN}.
 * The first three users are the frontend's fixed partners ({@code mock.ts}), the next four are the demo
 * accounts. Answers are keyed by the question ids of {@code web/src/onboarding/questions.ts}.
 */
public final class SyntheticPopulation {
    public static final String FIXTURE = "users-v1";
    public static final long SEED = 20261118L;
    public static final int SIZE = 200;
    public static final LocalDate REFERENCE_WEEK = LocalDate.of(2026, 11, 18);
    public static final List<String> DEMO_PHONES = List.of("13800000001", "13800000002", "13800000003", "13800000004");

    record School(String code, String place, String zh, String en) {}

    static final List<School> SCHOOLS = List.of(
            new School("beichen", "pku", "北辰大学", "Beichen University"),
            new School("qinghe", "tsinghua", "清河理工大学", "Qinghe Tech University"),
            new School("hdmedia", "ruc", "海淀传媒学院", "Haidian College of Media"),
            new School("xishan", "bnu", "西山师范大学", "Xishan Normal University"),
            new School("jingbei", "wudaokou", "京北财经学院", "Jingbei College of Finance"),
            new School("jiangwan", "fudan", "江湾大学", "Jiangwan University"),
            new School("yangpu", "tongji", "杨浦理工学院", "Yangpu Institute of Technology"),
            new School("changfeng", "ecnu", "长风师范大学", "Changfeng Normal University"));

    private static final Map<String, List<String>> WORK_PLACES = Map.of(
            "bj", List.of("wudaokou", "wudaokou", "sanlitun", "pku", "tsinghua"),
            "sh", List.of("wukang", "wukang", "fudan", "ecnu"));

    private static final String[][] SURNAMES = {
            {"王", "wang"}, {"李", "li"}, {"张", "zhang"}, {"刘", "liu"}, {"陈", "chen"}, {"杨", "yang"}, {"赵", "zhao"},
            {"黄", "huang"}, {"周", "zhou"}, {"吴", "wu"}, {"徐", "xu"}, {"孙", "sun"}, {"胡", "hu"}, {"朱", "zhu"},
            {"高", "gao"}, {"林", "lin"}, {"何", "he"}, {"郭", "guo"}, {"马", "ma"}, {"罗", "luo"}, {"梁", "liang"},
            {"宋", "song"}, {"郑", "zheng"}, {"谢", "xie"}, {"韩", "han"}, {"唐", "tang"}, {"冯", "feng"}, {"许", "xu"},
            {"邓", "deng"}, {"曹", "cao"}, {"彭", "peng"}, {"曾", "zeng"}, {"田", "tian"}, {"董", "dong"}, {"潘", "pan"},
            {"袁", "yuan"}, {"蒋", "jiang"}, {"蔡", "cai"}, {"余", "yu"}, {"杜", "du"}, {"叶", "ye"}, {"程", "cheng"},
            {"魏", "wei"}, {"苏", "su"}, {"吕", "lv"}, {"丁", "ding"}, {"沈", "shen"}, {"江", "jiang"}};
    private static final String[][] GIVEN_F = {
            {"雨", "yu"}, {"欣", "xin"}, {"婷", "ting"}, {"佳", "jia"}, {"悦", "yue"}, {"涵", "han"}, {"思", "si"},
            {"琪", "qi"}, {"晴", "qing"}, {"瑶", "yao"}, {"宁", "ning"}, {"楠", "nan"}, {"菲", "fei"}, {"萱", "xuan"},
            {"可", "ke"}, {"怡", "yi"}, {"蕾", "lei"}, {"诗", "shi"}, {"曦", "xi"}, {"彤", "tong"}, {"若", "ruo"}};
    private static final String[][] GIVEN_M = {
            {"浩", "hao"}, {"宇", "yu"}, {"轩", "xuan"}, {"博", "bo"}, {"晨", "chen"}, {"然", "ran"}, {"睿", "rui"},
            {"泽", "ze"}, {"俊", "jun"}, {"凯", "kai"}, {"航", "hang"}, {"哲", "zhe"}, {"铭", "ming"}, {"远", "yuan"},
            {"毅", "yi"}, {"磊", "lei"}, {"帆", "fan"}, {"川", "chuan"}, {"景", "jing"}, {"峰", "feng"}, {"恒", "heng"}};
    private static final String[][] GIVEN_N = {
            {"清", "qing"}, {"默", "mo"}, {"舟", "zhou"}, {"言", "yan"}, {"知", "zhi"}, {"遥", "yao"}, {"秋", "qiu"},
            {"白", "bai"}, {"安", "an"}, {"一", "yi"}, {"禾", "he"}, {"溪", "xi"}, {"子", "zi"}, {"嘉", "jia"}};

    private static final String[] HOBBIES = {"cooking", "music", "travel", "reading", "gym", "gaming", "movies", "art"};
    private static final int[] HOBBY_WEIGHTS = {14, 22, 20, 14, 10, 10, 20, 8};
    private static final String[] CUSTOM_HOBBIES = {"飞盘", "桌游", "观鸟", "滑板", "插花", "攀岩", "做咖啡", "拼乐高"};

    static final Map<String, String> MULTI_BRANCH = strings(
            "cooking", "cookingStyle", "music", "musicSound", "travel", "travelStyle", "reading", "readingGenres",
            "gym", "gymTrainingStyle", "gaming", "gamingGenres", "movies", "moviesContent", "art", "artMediums");
    private static final Map<String, String> TEXT_BRANCH = strings(
            "cooking", "cookingSignatureDish", "music", "musicNowPlaying", "travel", "travelNext", "reading", "readingLastBook",
            "gym", "gymTrainingFor", "gaming", "gamingNowPlaying", "movies", "moviesLastWatch", "art", "artMake");
    private static final Map<String, String[]> BRANCH_OPTIONS = Map.of(
            "cookingStyle", new String[] {"italian", "asian", "mexican", "comfort", "baking", "grilling", "healthy", "brunch"},
            "musicSound", new String[] {"pop", "hiphop", "indie", "electronic", "rnb", "rock", "country", "jazz", "classical", "kpop", "latin"},
            "travelStyle", new String[] {"backpacking", "luxury", "foodie", "adventure", "beach", "city", "solo", "road"},
            "readingGenres", new String[] {"litfic", "fantasy", "scifi", "mystery", "romance", "memoir", "selfhelp", "poetry", "classics"},
            "gymTrainingStyle", new String[] {"lifting", "running", "yoga", "pilates", "crossfit", "boxing", "dance", "cycling", "climbing"},
            "gamingGenres", new String[] {"fps", "rpg", "sports", "moba", "strategy", "indie", "cozy", "fighting", "mmo"},
            "moviesContent", new String[] {"indie", "horror", "romcom", "action", "scifi", "documentary", "a24", "foreign", "anime", "reality"},
            "artMediums", new String[] {"drawing", "painting", "digital", "pottery", "sculpture", "collage", "crafts", "mixed"});
    private static final Map<String, String[]> PHRASES = Map.of(
            "cookingSignatureDish", new String[] {"番茄牛腩", "可乐鸡翅", "酸菜鱼", "红烧肉", "葱油拌面", "麻婆豆腐", "糖醋排骨", "蛋包饭"},
            "musicNowPlaying", new String[] {"晴天", "夜空中最亮的星", "稻香", "后来", "平凡之路", "南方姑娘", "理想三旬", "成都"},
            "travelNext", new String[] {"大理", "伊犁", "冰岛", "成都", "青海湖", "厦门", "京都", "稻城亚丁"},
            "readingLastBook", new String[] {"长安的荔枝", "活着", "三体", "百年孤独", "平凡的世界", "小王子", "围城", "人间失格"},
            "gymTrainingFor", new String[] {"跑完第一个半马", "体测拿满分", "做到 10 个引体向上", "每天睡个好觉"},
            "gamingNowPlaying", new String[] {"王者荣耀", "原神", "塞尔达传说", "星露谷物语", "英雄联盟", "动物森友会", "双人成行", "黑神话：悟空"},
            "moviesLastWatch", new String[] {"霸王别姬", "让子弹飞", "千与千寻", "爱乐之城", "请回答1988", "繁花", "漫长的季节", "瞬息全宇宙"},
            "artMake", new String[] {"给朋友画头像", "捏一个歪歪的杯子", "做拼贴手账", "画水彩明信片"});

    private static final String[][] MAJORS = {
            {"中文系", "Chinese Literature"}, {"计算机", "Computer Science"}, {"经济学", "Economics"}, {"新闻学", "Journalism"},
            {"建筑学", "Architecture"}, {"数学", "Mathematics"}, {"心理学", "Psychology"}, {"法学", "Law"}, {"生物", "Biology"},
            {"历史", "History"}, {"电子工程", "Electrical Engineering"}, {"设计", "Design"}, {"外语", "Foreign Languages"},
            {"社会学", "Sociology"}, {"物理", "Physics"}, {"金融", "Finance"}};
    private static final String[][] JOBS = {
            {"产品经理", "Product manager"}, {"程序员", "Software engineer"}, {"设计师", "Designer"}, {"咖啡师", "Barista"},
            {"编辑", "Editor"}, {"摄影师", "Photographer"}, {"老师", "Teacher"}, {"运营", "Operations"}, {"会计", "Accountant"},
            {"研究助理", "Research assistant"}};
    private static final Map<String, String[]> YEAR_LABEL = Map.of(
            "freshman", new String[] {"大一", "Freshman"}, "sophomore", new String[] {"大二", "Sophomore"},
            "junior", new String[] {"大三", "Junior"}, "senior", new String[] {"大四", "Senior"},
            "master", new String[] {"硕士", "Master's"}, "phd", new String[] {"博士", "PhD"}, "other", new String[] {"其他", "Other"});
    private static final String[][] TAGS = {
            {"胶片相机", "film cameras"}, {"citywalk", "citywalk"}, {"火锅", "hotpot"}, {"猫", "cats"}, {"播客", "podcasts"},
            {"晨跑", "early runs"}, {"livehouse", "indie gigs"}, {"速写", "sketching"}, {"桌游", "board games"},
            {"手冲咖啡", "pour-over coffee"}, {"露营", "camping"}, {"飞盘", "frisbee"}, {"拼图", "jigsaw puzzles"},
            {"烘焙", "baking"}, {"骑行", "cycling"}};
    private static final String[][] TRAITS = {
            {"温柔又机灵", "warm and quick-witted"}, {"安静，但其实很好笑", "quiet but secretly hilarious"},
            {"爱笑、好奇心很重", "curious and quick to laugh"}, {"做事认真，说话直接", "earnest and straightforward"},
            {"慢热，熟了以后话很多", "slow to warm up, then full of stories"}, {"行动派，想到就去做", "a doer who acts on ideas"}};
    private static final Map<String, String[]> HOBBY_LINES = Map.of(
            "cooking", new String[] {"周末会花一下午炖一锅汤", "Spends Sunday afternoons slow-cooking soup."},
            "music", new String[] {"耳机里永远在循环新发现的歌", "Always has a newly found song on repeat."},
            "travel", new String[] {"攒假期就是为了出去走走", "Saves every holiday for a trip."},
            "reading", new String[] {"包里总揣着一本书", "Always carries a book around."},
            "gym", new String[] {"早上 7 点前就已经运动完了", "Done with a workout before 7 am."},
            "gaming", new String[] {"排位赛里特别靠谱", "The teammate you want in a ranked match."},
            "movies", new String[] {"看完电影一定要聊结局", "Can't leave a film without talking about its ending."},
            "art", new String[] {"会在本子上随手画速写", "Sketches in a notebook wherever they go."});
    private static final String[][] LIKES = {
            {"认真听你说话", "someone who really listens"}, {"有点小幽默", "a bit of dry humour"},
            {"会注意小细节", "people who notice small things"}, {"愿意一起尝试新东西", "trying new things together"},
            {"不着急、慢慢来", "taking things slow"}};
    private static final String[][] BG = {
            {"#ffb4a2", "#ffd6a5"}, {"#a0c4ff", "#bdb2ff"}, {"#caffbf", "#9bf6ff"}, {"#fdffb6", "#ffc6ff"},
            {"#ffadad", "#ffd6a5"}, {"#9bf6ff", "#a0c4ff"}, {"#bdb2ff", "#ffc6ff"}, {"#d8f3dc", "#b7e4c7"}};
    private static final String[] SKIN = {"#f6d1b5", "#eac4a4", "#f1cfb0", "#e0b48f", "#c98f6b", "#f9dcc4"};
    private static final String[] HAIR_COLOR = {"#2b1d1a", "#1b1716", "#4a2f27", "#3d2b1f", "#6b4226", "#1f1f2e"};
    private static final String[] SHIRT = {"#5b8def", "#2d3142", "#ff6b9a", "#06d6a0", "#ffd166", "#8338ec", "#ef476f", "#118ab2"};
    private static final String[] SCENES = {"coffee", "books", "film", "mountain", "cat", "guitar", "bike", "sunset", "noodles", "camera", "gamepad", "plant"};
    private static final Map<String, String> HOBBY_SCENE = Map.of(
            "cooking", "noodles", "music", "guitar", "travel", "mountain", "reading", "books",
            "gym", "bike", "gaming", "gamepad", "movies", "film", "art", "camera");
    private static final String[] HEAR_STUDENT = {"friend", "poster", "tabling", "rednote", "douyin", "wechat", "bilibili", "weibo"};
    private static final String[] HEAR_WORKER = {"friend", "rednote", "douyin", "wechat", "bilibili", "weibo"};
    private static final String[] DATING_HELP = {"approaching", "convo", "intoMe", "confident", "whatIWant", "theDate", "justDate"};

    private final Random r = new Random(SEED);
    private final Set<String> usedNames = new HashSet<>(Set.of("林夏", "周屿", "许沐"));
    private final Set<String> usedHandles = new HashSet<>(Set.of("linxia", "zhouyu", "xumu"));

    private SyntheticPopulation() {}

    /** The whole fixture document. Deterministic. */
    public static Map<String, Object> document() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("fixture", FIXTURE);
        doc.put("seed", SEED);
        doc.put("referenceWeek", REFERENCE_WEEK.toString());
        doc.put("generator", "api/aiditto-domain/src/test/java/cn/aiditto/domain/matching/synthetic/SyntheticPopulation.java");
        doc.put("note", "All people, schools and WeChat ids are fictional. No real photos. Do not edit by hand: regenerate.");
        doc.put("users", new SyntheticPopulation().users());
        return doc;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> users(Map<String, Object> doc) {
        return (List<Map<String, Object>>) doc.get("users");
    }

    private List<Map<String, Object>> users() {
        List<Map<String, Object>> out = new ArrayList<>(FixedUsers.partners());
        out.addAll(FixedUsers.demos());
        int n = SIZE - out.size();

        List<String> genders = quota(n, "man", 90, "woman", 89, "nonbinary", 10, "other", 4);
        List<String> pools = quota(n, "bj", 124, "sh", 69);
        List<Boolean> students = quota(n, Boolean.TRUE, 154, Boolean.FALSE, 39);
        List<String> binaryPrefs = quota(179, "opposite", 140, "same", 14, "everyone", 21, "mixed", 4);
        List<String> otherPrefs = quota(14, "everyone", 8, "both", 2, "nonbinary", 2, "womenNb", 2);
        List<Boolean> dealbreakers = quota(n, Boolean.TRUE, 58, Boolean.FALSE, n - 58);

        int bi = 0;
        int oi = 0;
        for (int k = 0; k < n; k++) {
            String g = genders.get(k);
            boolean binary = g.equals("man") || g.equals("woman");
            String pref = binary ? binaryPrefs.get(bi++) : otherPrefs.get(oi++);
            out.add(user(out.size(), g, pref, pools.get(k), students.get(k), dealbreakers.get(k)));
        }
        return out;
    }

    private Map<String, Object> user(int index, String gender, String pref, String pool, boolean student, boolean dealbreaker) {
        String[] name = name(gender);
        String handle = handle(name[1]);
        int age;
        String year = null;
        School school = null;
        String place;
        if (student) {
            year = weighted(new String[] {"freshman", "sophomore", "junior", "senior", "master", "phd", "other"}, 12, 18, 22, 20, 20, 6, 2);
            age = switch (year) {
                case "freshman" -> 18 + r.nextInt(2);
                case "sophomore" -> 19 + r.nextInt(2);
                case "junior" -> 20 + r.nextInt(2);
                case "senior" -> 21 + r.nextInt(2);
                case "master" -> 22 + r.nextInt(4);
                case "phd" -> 24 + r.nextInt(3);
                default -> 19 + r.nextInt(6);
            };
            List<School> local = SCHOOLS.stream().filter(s -> poolOf(s.place()).equals(pool)).toList();
            school = local.get(r.nextInt(local.size()));
            place = r.nextInt(5) == 0 ? (pool.equals("bj") ? "wudaokou" : "fudan") : school.place();
        } else {
            age = 22 + r.nextInt(5);
            List<String> places = WORK_PLACES.get(pool);
            place = places.get(r.nextInt(places.size()));
        }
        LocalDate birthday = REFERENCE_WEEK.minusYears(age).minusDays(r.nextInt(365));

        Map<String, Object> a = new LinkedHashMap<>();
        a.put("isStudent", student ? "yes" : "no");
        a.put("name", name[0]);
        a.put("gender", gender);
        a.put("expectedGender", wants(gender, pref));
        a.put("birthday", birthday.toString());
        List<String> hobbies = hobbies();
        a.put("hobbies", hobbies);
        for (String h : hobbies) {
            if (!MULTI_BRANCH.containsKey(h)) continue;
            String multi = MULTI_BRANCH.get(h);
            a.put(multi, some(BRANCH_OPTIONS.get(multi), 1, 3));
            if (r.nextInt(4) != 0) {
                String text = TEXT_BRANCH.get(h);
                a.put(text, pick(PHRASES.get(text)));
            }
        }
        int lo = Math.max(18, age - 1 - r.nextInt(3));
        int hi = Math.min(30, age + 2 + r.nextInt(4));
        a.put("expectedAgeRange", List.of(lo, hi));
        int height = height(gender);
        a.put("height", linked("cm", height, "unit", "cm"));
        a.put("heightRange", heightRange(gender, pref));
        if (student) a.put("schoolEmail", handle + "@demo." + school.code() + ".edu.cn");
        else a.put("personalEmail", handle + "@example.com");
        String mode = weighted(new String[] {"transit", "walk", "drive"}, 65, 20, 15);
        int minutes = switch (mode) {
            case "walk" -> pick(new Integer[] {20, 30});
            case "drive" -> pick(new Integer[] {30, 45});
            default -> pick(new Integer[] {30, 45, 45, 60});
        };
        a.put("location", linked("placeId", place, "mode", mode, "minutes", minutes));
        a.put("relationshipLooking", looking());
        a.put("matchingStyle", weighted(new String[] {"faster", "steady", "fewer", "theOne"}, 20, 45, 25, 10));
        if (student) a.put("schoolYear", year);
        a.put("hearAbout", pick(student ? HEAR_STUDENT : HEAR_WORKER));
        String[] job = student ? null : JOBS[r.nextInt(JOBS.length)];
        if (job != null) a.put("occupation", job[0]);
        a.put("firstDateBudget", weighted(new String[] {"under20", "20to50", "50to100", "100plus", "noPref"}, 20, 35, 25, 5, 15));
        Map<String, Object> ai = null;
        if (dealbreaker) {
            Object[] d = dealbreaker(age, student);
            a.put("dealbreakers", d[0]);
            ai = castMap(d[1]);
        } else {
            a.put("dealbreakers", "");
        }
        String edu = student
                ? (year.equals("master") || year.equals("phd") ? "college" : weighted(new String[] {"highSchool", "college"}, 70, 30))
                : weighted(new String[] {"highSchool", "college", "graduate", "preferNot"}, 5, 65, 25, 5);
        a.put("educationLevel", edu);
        a.put("educationImportance", weighted(new String[] {"none", "kinda", "super"}, 70, 22, 8));
        a.put("firstMessage", firstMessage(a, hobbies));
        a.put("datingHelp", some(DATING_HELP, 1, 2));

        Map<String, Object> u = new LinkedHashMap<>();
        u.put("handle", handle);
        u.put("kind", "synthetic");
        u.put("pool", pool);
        u.put("answers", a);
        if (ai != null) u.put("aiConstraints", ai);
        u.put("card", card(index, name, gender, age, school, year, job, hobbies, handle));
        return u;
    }

    private Map<String, Object> card(int index, String[] name, String gender, int age, School school, String year, String[] job,
                                     List<String> hobbies, String handle) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", t(name[0], name[2]));
        c.put("age", age);
        if (school != null) {
            c.put("school", t(school.zh(), school.en()));
            String[] m = MAJORS[r.nextInt(MAJORS.length)];
            c.put("major", t(m[0], m[1]));
            String[] y = YEAR_LABEL.get(year);
            c.put("year", t(y[0], y[1]));
        } else {
            c.put("school", t("已工作", "Working"));
            c.put("major", t(job[0], job[1]));
            c.put("year", t("已工作", "Working"));
        }
        List<Object> tags = new ArrayList<>();
        for (int i : distinct(TAGS.length, 3)) tags.add(t(TAGS[i][0], TAGS[i][1]));
        c.put("extraTags", tags);
        c.put("avatar", avatar(gender));
        c.put("scenes", scenes(hobbies));
        String ta = gender.equals("woman") ? "她" : gender.equals("man") ? "他" : "ta";
        c.put("headline", t(name[0] + "就是你一直在等的那个约会对象！", name[2] + " is the perfect date you’re looking for!"));
        String[] trait = TRAITS[r.nextInt(TRAITS.length)];
        String first = hobbies.stream().filter(HOBBY_LINES::containsKey).findFirst().orElse("music");
        String[] line = HOBBY_LINES.get(first);
        String[] like = LIKES[r.nextInt(LIKES.length)];
        c.put("blurb", List.of(
                t(ta + trait[0] + "，" + line[0] + "。", name[2] + " is " + trait[1] + ". " + line[1]),
                t("如果你喜欢" + like[0] + "的人，" + name[0] + "就是你的菜。", "If you’re into " + like[1] + ", " + name[2] + "’s your kind of vibe.")));
        c.put("wechat", handle.replaceAll("\\d+$", "") + "_demo_" + String.format("%04d", 1000 + (index * 7919 + r.nextInt(97)) % 9000));
        return c;
    }

    private String[] name(String gender) {
        while (true) {
            String[] s = SURNAMES[r.nextInt(SURNAMES.length)];
            String[][] pool = switch (gender) {
                case "woman" -> r.nextInt(5) == 0 ? GIVEN_N : GIVEN_F;
                case "man" -> r.nextInt(5) == 0 ? GIVEN_N : GIVEN_M;
                default -> r.nextBoolean() ? GIVEN_N : (r.nextBoolean() ? GIVEN_F : GIVEN_M);
            };
            String[] g1 = pool[r.nextInt(pool.length)];
            String zh = s[0] + g1[0];
            String py = g1[1];
            if (r.nextInt(10) < 7) {
                String[] g2 = pool[r.nextInt(pool.length)];
                if (g2[0].equals(g1[0])) continue;
                zh += g2[0];
                py += g2[1];
            }
            if (!usedNames.add(zh)) continue;
            String en = cap(s[1]) + " " + cap(py);
            return new String[] {zh, s[1] + py, en};
        }
    }

    private String handle(String base) {
        String h = base;
        for (int i = 2; !usedHandles.add(h); i++) h = base + i;
        return h;
    }

    private List<String> wants(String gender, String pref) {
        boolean man = gender.equals("man");
        return switch (pref) {
            case "opposite" -> List.of(man ? "women" : "men");
            case "same" -> List.of(man ? "men" : "women");
            case "everyone" -> List.of("everyone");
            case "mixed" -> List.of(man ? "women" : "men", "nonbinary");
            case "both" -> List.of("men", "women");
            case "nonbinary" -> List.of("nonbinary");
            case "womenNb" -> List.of("women", "nonbinary");
            default -> throw new IllegalStateException(pref);
        };
    }

    private List<String> hobbies() {
        int count = 2 + r.nextInt(4);
        Set<String> picked = new LinkedHashSet<>();
        while (picked.size() < count) picked.add(weighted(HOBBIES, HOBBY_WEIGHTS));
        List<String> out = new ArrayList<>();
        for (String h : HOBBIES) if (picked.contains(h)) out.add(h);
        if (r.nextInt(10) == 0) out.add("custom:" + pick(CUSTOM_HOBBIES));
        return out;
    }

    private int height(String gender) {
        double mean = gender.equals("man") ? 176 : gender.equals("woman") ? 163 : 169;
        double sd = gender.equals("man") ? 6 : gender.equals("woman") ? 5 : 7;
        return (int) Math.max(150, Math.min(198, Math.round(mean + r.nextGaussian() * sd)));
    }

    private Map<String, Object> heightRange(String gender, String pref) {
        if (r.nextInt(100) < 65) return linked("any", true, "min", 150, "max", 200);
        boolean wantsMen = (gender.equals("woman") && pref.equals("opposite")) || (gender.equals("man") && pref.equals("same"));
        if (wantsMen) return linked("any", false, "min", 168 + r.nextInt(8), "max", 195);
        return linked("any", false, "min", 150, "max", 172 + r.nextInt(10));
    }

    private List<String> looking() {
        String first = weighted(new String[] {"serious", "notSure", "friends", "casual", "lifePartner"}, 40, 20, 15, 10, 15);
        if (r.nextInt(10) >= 3) return List.of(first);
        String second = weighted(new String[] {"serious", "notSure", "friends", "casual", "lifePartner"}, 40, 20, 15, 10, 15);
        if (second.equals(first)) return List.of(first);
        List<String> order = List.of("lifePartner", "serious", "casual", "friends", "notSure");
        return order.stream().filter(o -> o.equals(first) || o.equals(second)).toList();
    }

    private Object[] dealbreaker(int age, boolean student) {
        int kind = r.nextInt(student ? 4 : 3);
        return switch (kind) {
            case 0 -> {
                int h = pick(new Integer[] {160, 165, 170, 175});
                yield new Object[] {"希望对方身高 " + h + " 以上", linked("minHeight", h)};
            }
            case 1 -> {
                int max = Math.min(30, age + 3);
                yield new Object[] {"希望对方年龄不超过 " + max + " 岁", linked("maxAge", max)};
            }
            case 2 -> {
                int min = Math.max(18, age - 2);
                yield new Object[] {"希望对方至少 " + min + " 岁", linked("minAge", min)};
            }
            default -> new Object[] {"只想认识在读学生", linked("studentsOnly", true)};
        };
    }

    private String firstMessage(Map<String, Object> a, List<String> hobbies) {
        for (String h : hobbies) {
            String text = TEXT_BRANCH.get(h);
            if (text == null || !(a.get(text) instanceof String v)) continue;
            return switch (h) {
                case "cooking" -> "拿手菜是" + v + "，见面可以聊聊你最爱吃什么。";
                case "music" -> "最近在循环「" + v + "」，想听听你的歌单。";
                case "reading" -> "最近在读《" + v + "》，想找人一起聊聊。";
                case "travel" -> "下一站想去" + v + "，欢迎一起做攻略。";
                case "movies" -> "最近最爱的是《" + v + "》，结局我有话说。";
                case "gaming" -> "最近在玩" + v + "，双排随时。";
                case "gym" -> "最近的目标是" + v + "，欢迎监督。";
                case "art" -> "最近在" + v + "，见面带给你看。";
                default -> "认真回消息，见面不迟到。";
            };
        }
        return "认真回消息，见面不迟到。";
    }

    private Map<String, Object> avatar(String gender) {
        Map<String, Object> av = new LinkedHashMap<>();
        String[] bg = BG[r.nextInt(BG.length)];
        av.put("bg", List.of(bg[0], bg[1]));
        av.put("skin", pick(SKIN));
        String[] hair = switch (gender) {
            case "woman" -> new String[] {"bob", "long", "bun", "curly", "short"};
            case "man" -> new String[] {"short", "short", "curly", "bob"};
            default -> new String[] {"bob", "short", "long", "curly", "bun"};
        };
        av.put("hair", pick(hair));
        av.put("hairColor", pick(HAIR_COLOR));
        av.put("shirt", pick(SHIRT));
        if (r.nextInt(4) == 0) av.put("glasses", true);
        if (r.nextInt(5) == 0) av.put("earring", true);
        return av;
    }

    private List<String> scenes(List<String> hobbies) {
        int target = 3 + r.nextInt(3);
        Set<String> out = new LinkedHashSet<>();
        for (String h : hobbies) {
            if (out.size() >= target) break;
            if (HOBBY_SCENE.containsKey(h)) out.add(HOBBY_SCENE.get(h));
            if (h.equals("travel") && out.size() < target) out.add("sunset");
        }
        while (out.size() < target) out.add(pick(SCENES));
        return new ArrayList<>(out);
    }

    // ---- helpers

    static String poolOf(String place) {
        return switch (place) {
            case "fudan", "tongji", "ecnu", "wukang" -> "sh";
            default -> "bj";
        };
    }

    static Map<String, Object> t(String zh, String en) {
        return linked("en", en, "zh", zh);
    }

    static Map<String, Object> linked(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, String> strings(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private <T> List<T> quota(int n, Object... valueCount) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < valueCount.length; i += 2) {
            @SuppressWarnings("unchecked") T v = (T) valueCount[i];
            for (int k = 0; k < (Integer) valueCount[i + 1]; k++) out.add(v);
        }
        if (out.size() != n) throw new IllegalStateException("quota adds up to " + out.size() + ", not " + n);
        Collections.shuffle(out, r);
        return out;
    }

    private <T> T pick(T[] values) {
        return values[r.nextInt(values.length)];
    }

    private String weighted(String[] values, int... weights) {
        int total = 0;
        for (int w : weights) total += w;
        int x = r.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            x -= weights[i];
            if (x < 0) return values[i];
        }
        return values[values.length - 1];
    }

    private List<String> some(String[] options, int min, int max) {
        int k = min + r.nextInt(max - min + 1);
        List<String> out = new ArrayList<>();
        for (int i : distinct(options.length, k)) out.add(options[i]);
        return out;
    }

    private List<Integer> distinct(int n, int k) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        Collections.shuffle(idx, r);
        List<Integer> out = new ArrayList<>(idx.subList(0, Math.min(k, n)));
        Collections.sort(out);
        return out;
    }
}
