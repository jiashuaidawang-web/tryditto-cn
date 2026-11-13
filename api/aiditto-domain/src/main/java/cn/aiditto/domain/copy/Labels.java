package cn.aiditto.domain.copy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display labels for the option ids that template copy may mention, copied from
 * {@code web/src/onboarding/questions.ts}. Keyed by {@code questionId/optionId} because option ids repeat
 * across questions ({@code indie} is music, games and film).
 */
public final class Labels {

    /** Hobby branches in bank order: hobby → [multi-choice question, free-text question]. */
    public static final Map<String, List<String>> BRANCHES = Map.of(
            "cooking", List.of("cookingStyle", "cookingSignatureDish"),
            "music", List.of("musicSound", "musicNowPlaying"),
            "travel", List.of("travelStyle", "travelNext"),
            "reading", List.of("readingGenres", "readingLastBook"),
            "gym", List.of("gymTrainingStyle", "gymTrainingFor"),
            "gaming", List.of("gamingGenres", "gamingNowPlaying"),
            "movies", List.of("moviesContent", "moviesLastWatch"),
            "art", List.of("artMediums", "artMake"));

    public static final List<String> HOBBY_ORDER =
            List.of("cooking", "music", "travel", "reading", "gym", "gaming", "movies", "art");

    private static final Map<String, String[]> LABELS = new HashMap<>();

    static {
        put("hobbies", "cooking", "下厨", "Cooking");
        put("hobbies", "music", "音乐", "Music");
        put("hobbies", "travel", "旅行", "Travel");
        put("hobbies", "reading", "阅读", "Reading");
        put("hobbies", "gym", "健身", "Gym");
        put("hobbies", "gaming", "游戏", "Gaming");
        put("hobbies", "movies", "影视", "Movies/TV");
        put("hobbies", "art", "艺术", "Art");

        put("cookingStyle", "italian", "意面西餐", "Italian");
        put("cookingStyle", "asian", "中式家常", "Asian");
        put("cookingStyle", "mexican", "异国料理", "Mexican");
        put("cookingStyle", "comfort", "火锅炖煮", "Comfort food");
        put("cookingStyle", "baking", "烘焙甜点", "Baking");
        put("cookingStyle", "grilling", "烧烤撸串", "Grilling");
        put("cookingStyle", "healthy", "减脂轻食", "Healthy bowls");
        put("cookingStyle", "brunch", "早午餐", "Brunch");

        put("musicSound", "pop", "流行", "Pop");
        put("musicSound", "hiphop", "说唱", "Hip-hop");
        put("musicSound", "indie", "独立", "Indie");
        put("musicSound", "electronic", "电子", "Electronic");
        put("musicSound", "rnb", "R&B", "R&B");
        put("musicSound", "rock", "摇滚", "Rock");
        put("musicSound", "country", "民谣", "Country");
        put("musicSound", "jazz", "爵士", "Jazz");
        put("musicSound", "classical", "古典", "Classical");
        put("musicSound", "kpop", "K-pop", "K-pop");
        put("musicSound", "latin", "国风", "Latin");

        put("travelStyle", "backpacking", "穷游背包", "Backpacking");
        put("travelStyle", "luxury", "精致度假", "Luxury");
        put("travelStyle", "foodie", "美食探店", "Foodie tours");
        put("travelStyle", "adventure", "户外徒步", "Adventure");
        put("travelStyle", "beach", "海边躺平", "Beach + sun");
        put("travelStyle", "city", "citywalk", "City-hopping");
        put("travelStyle", "solo", "一个人旅行", "Solo travel");
        put("travelStyle", "road", "自驾", "Road trips");

        put("readingGenres", "litfic", "严肃文学", "Lit fic");
        put("readingGenres", "fantasy", "奇幻", "Fantasy");
        put("readingGenres", "scifi", "科幻", "Sci-fi");
        put("readingGenres", "mystery", "推理悬疑", "Mystery");
        put("readingGenres", "romance", "言情", "Romance");
        put("readingGenres", "memoir", "传记回忆录", "Memoir");
        put("readingGenres", "selfhelp", "自我成长", "Self-help");
        put("readingGenres", "poetry", "诗歌", "Poetry");
        put("readingGenres", "classics", "经典名著", "Classics");
        put("readingGenres", "smut", "网文", "Web novels");

        put("gymTrainingStyle", "lifting", "撸铁", "Lifting");
        put("gymTrainingStyle", "running", "跑步", "Running");
        put("gymTrainingStyle", "yoga", "瑜伽", "Yoga");
        put("gymTrainingStyle", "pilates", "普拉提", "Pilates");
        put("gymTrainingStyle", "crossfit", "CrossFit", "Crossfit");
        put("gymTrainingStyle", "boxing", "拳击", "Boxing");
        put("gymTrainingStyle", "dance", "跳舞", "Dance");
        put("gymTrainingStyle", "cycling", "骑行", "Cycling");
        put("gymTrainingStyle", "climbing", "攀岩", "Climbing");

        put("gamingGenres", "fps", "射击", "FPS");
        put("gamingGenres", "rpg", "角色扮演", "RPG");
        put("gamingGenres", "sports", "体育竞技", "Sports games");
        put("gamingGenres", "moba", "MOBA", "MOBA");
        put("gamingGenres", "strategy", "策略", "Strategy");
        put("gamingGenres", "indie", "独立游戏", "Indie");
        put("gamingGenres", "cozy", "模拟经营", "Sims/cozy");
        put("gamingGenres", "fighting", "格斗", "Fighting");
        put("gamingGenres", "mmo", "网游", "MMO");

        put("moviesContent", "indie", "文艺片", "Indie");
        put("moviesContent", "horror", "恐怖", "Horror");
        put("moviesContent", "romcom", "爱情喜剧", "Rom-com");
        put("moviesContent", "action", "动作", "Action");
        put("moviesContent", "scifi", "科幻", "Sci-fi");
        put("moviesContent", "documentary", "纪录片", "Documentary");
        put("moviesContent", "a24", "A24 那挂", "A24 stuff");
        put("moviesContent", "foreign", "外语片", "Foreign");
        put("moviesContent", "anime", "动漫", "Anime");
        put("moviesContent", "reality", "综艺", "Reality TV");

        put("artMediums", "drawing", "素描", "Drawing");
        put("artMediums", "painting", "绘画", "Painting");
        put("artMediums", "digital", "板绘数字", "Digital");
        put("artMediums", "pottery", "陶艺", "Pottery");
        put("artMediums", "sculpture", "雕塑", "Sculpture");
        put("artMediums", "collage", "拼贴", "Collage");
        put("artMediums", "crafts", "手工", "Crafts");
        put("artMediums", "mixed", "综合材料", "Mixed media");

        put("relationshipLooking", "lifePartner", "人生伴侣", "Life partner");
        put("relationshipLooking", "serious", "认真谈恋爱", "Serious relationship");
        put("relationshipLooking", "casual", "轻松约会", "Casual dates");
        put("relationshipLooking", "friends", "交新朋友", "New friends");
        put("relationshipLooking", "notSure", "还没想好", "Not sure yet");

        put("matchingStyle", "faster", "多约几次，快一点", "More dates, faster");
        put("matchingStyle", "steady", "稳稳的节奏", "A steady mix");
        put("matchingStyle", "fewer", "少而精", "Fewer, better matches");
        put("matchingStyle", "theOne", "等那个对的人", "Wait for the one");

        put("schoolYear", "freshman", "大一", "Freshman");
        put("schoolYear", "sophomore", "大二", "Sophomore");
        put("schoolYear", "junior", "大三", "Junior");
        put("schoolYear", "senior", "大四", "Senior");
        put("schoolYear", "master", "硕士", "Master");
        put("schoolYear", "phd", "博士", "PhD");
        put("schoolYear", "other", "在读", "Student");

        put("firstDateBudget", "under20", "100 元以内", "under $20");
        put("firstDateBudget", "20to50", "100–200 元", "$20–50");
        put("firstDateBudget", "50to100", "200–400 元", "$50–100");
        put("firstDateBudget", "100plus", "400 元以上", "$100+");
        put("firstDateBudget", "noPref", "都可以", "no preference");
    }

    private Labels() {}

    private static void put(String question, String option, String zh, String en) {
        LABELS.put(question + "/" + option, new String[] {zh, en});
    }

    /** Label of an option, or {@code null} when unknown. Custom answers ({@code custom:文字}) return their text. */
    public static String of(String question, String option, Lang lang) {
        if (option == null) return null;
        if (option.startsWith("custom:")) return option.substring("custom:".length()).strip();
        String[] l = LABELS.get(question + "/" + option);
        return l == null ? null : l[lang == Lang.EN ? 1 : 0];
    }

    public static String hobby(String id, Lang lang) {
        return of("hobbies", id, lang);
    }

    /** Hobby whose branch contains {@code questionId}, or {@code null}. */
    public static String hobbyOfBranch(String questionId) {
        for (Map.Entry<String, List<String>> e : BRANCHES.entrySet()) {
            if (e.getValue().contains(questionId)) return e.getKey();
        }
        return null;
    }
}
