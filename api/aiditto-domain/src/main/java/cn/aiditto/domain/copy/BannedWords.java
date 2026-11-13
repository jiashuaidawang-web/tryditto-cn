package cn.aiditto.domain.copy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Words that must never appear in match copy (技术方案 §8.5 rule 4): looks and body, sex, religion,
 * politics, health and psychological diagnoses, money and transfers, destiny promises, and match
 * percentages. Chinese entries match as substrings; English entries match whole words, case-insensitive.
 */
public final class BannedWords {

    public enum Category { LOOKS, SEX, RELIGION, POLITICS, HEALTH, MONEY, PROMISE, PERCENT }

    public record Hit(Category category, String term) {}

    private record Entry(Category category, String term, Pattern pattern) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        zh(Category.LOOKS, "颜值", "长相", "长得", "身材", "身高", "好看", "漂亮", "帅气", "帅哥", "美女", "胖", "瘦", "高颜", "腿长", "胸", "皮肤白");
        en(Category.LOOKS, "hot", "sexy", "beautiful", "handsome", "pretty", "gorgeous", "attractive", "body", "height", "tall", "skinny");
        zh(Category.SEX, "性感", "性爱", "做爱", "上床", "约炮", "开房", "一夜情", "床上");
        en(Category.SEX, "sex", "sexual", "hookup", "hook up", "one night stand");
        zh(Category.RELIGION, "宗教", "信仰", "基督", "天主", "佛教", "伊斯兰", "穆斯林", "上帝", "教会", "寺庙");
        en(Category.RELIGION, "religion", "religious", "church", "christian", "muslim", "buddhist", "god");
        zh(Category.POLITICS, "政治", "政党", "共产党", "民主党", "共和党", "左派", "右派", "政府", "选举");
        en(Category.POLITICS, "politics", "political", "democrat", "republican", "election");
        zh(Category.HEALTH, "抑郁", "焦虑症", "双相", "精神病", "诊断", "疾病", "心理疾病", "病史", "吃药");
        en(Category.HEALTH, "depression", "depressed", "anxiety disorder", "bipolar", "diagnosis", "diagnosed", "illness");
        zh(Category.MONEY, "转账", "借钱", "红包", "汇款", "收款", "投资", "理财", "贷款", "比特币", "打钱");
        en(Category.MONEY, "transfer money", "wire money", "loan", "invest", "crypto", "bitcoin");
        zh(Category.PROMISE, "命中注定", "灵魂伴侣", "天生一对", "天作之合", "真命天子", "真命天女", "一定会在一起", "百分百合适", "注定在一起");
        en(Category.PROMISE, "soulmate", "soul mate", "meant to be", "destined", "the one for you");
        ENTRIES.add(new Entry(Category.PERCENT, "%", Pattern.compile("\\d+(\\.\\d+)?\\s*[%％]")));
        zh(Category.PERCENT, "百分之", "匹配度", "契合度");
    }

    private BannedWords() {}

    private static void zh(Category c, String... terms) {
        for (String t : terms) ENTRIES.add(new Entry(c, t, Pattern.compile(Pattern.quote(t))));
    }

    private static void en(Category c, String... terms) {
        for (String t : terms) {
            ENTRIES.add(new Entry(c, t, Pattern.compile("(?<![A-Za-z])" + Pattern.quote(t) + "(?![A-Za-z])",
                    Pattern.CASE_INSENSITIVE)));
        }
    }

    public static Optional<Hit> find(String text) {
        if (text == null || text.isEmpty()) return Optional.empty();
        String lower = text.toLowerCase(Locale.ROOT);
        for (Entry e : ENTRIES) {
            if (e.pattern().matcher(lower).find()) return Optional.of(new Hit(e.category(), e.term()));
        }
        return Optional.empty();
    }
}
