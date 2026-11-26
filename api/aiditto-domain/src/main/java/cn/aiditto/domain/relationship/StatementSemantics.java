package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.Section;

import java.util.Map;

/**
 * Closed phrases and contradiction cues. This is not a general language model.
 * A phrase is supported only when the whole text is one of the known explicit sentences
 * and the proposed section, feature, and value are the ones that sentence states.
 */
public final class StatementSemantics {
    private record Expectation(Section section, String featureCode, String value) {}

    private static final Map<String, Expectation> EXPLICIT = explicit();

    private StatementSemantics() {}

    public static SemanticSupport judge(String text, Section section, String featureCode, String value, String importance) {
        if (text == null || text.isBlank()) return SemanticSupport.SEMANTIC_SUPPORT_PENDING;
        String normalized = text.strip();
        Expectation expectation = EXPLICIT.get(normalized);
        if (expectation != null) {
            if (expectation.section != section || !expectation.featureCode.equals(featureCode) || !expectation.value.equals(value)) {
                return SemanticSupport.REJECTED;
            }
            if ("SUPER".equals(importance) && !statesStrongIntensity(normalized)) return SemanticSupport.REJECTED;
            return SemanticSupport.SEMANTIC_SUPPORTED;
        }
        if (contradicts(normalized)) return SemanticSupport.REJECTED;
        return SemanticSupport.SEMANTIC_SUPPORT_PENDING;
    }

    private static boolean statesStrongIntensity(String text) {
        return text.contains("非常") || text.contains("超级") || text.contains("一定");
    }

    private static boolean contradicts(String text) {
        return text.contains("如果") || text.contains("假如") || text.contains("要是") || text.contains("万一")
                || text.contains("朋友") || text.contains("同事") || text.contains("别人") || text.contains("同学")
                || text.contains("他说") || text.contains("她说")
                || text.contains("不确定") || text.contains("也许") || text.contains("可能") || text.contains("大概")
                || text.contains("说不好")
                || text.contains("不喜欢") || text.contains("不想") || text.contains("不要") || text.contains("讨厌");
    }

    private static Map<String, Expectation> explicit() {
        Map<String, Expectation> map = new java.util.HashMap<>();
        preference(map, "我希望对方更主动", "我希望对方主动一点", "我希望对方很主动", "我希望对方主动一些",
                "我希望对方更加主动", "我希望对方保持主动", "我希望对方能够主动", "我希望对方表现主动",
                "我希望对方经常主动", "我希望对方尽量主动");
        self(map, "self.pace", "slow", "我这个人比较慢热", "我做事节奏偏慢", "我习惯慢慢来", "我相处节奏偏慢",
                "我通常不着急", "我省事的时候偏慢", "我做事不爱赶", "我更喜欢慢一点", "我节奏一直偏慢", "我习惯把节奏放慢");
        self(map, "self.communication", "quiet", "我说话比较少", "我话比较少", "我通常说话很少", "我平时话不多",
                "我不爱抢着说话", "我说话偏少", "我话少一些", "我一般说话很少", "我说话不多", "我较少主动开口");
        self(map, "self.communication", "frequent", "我说话很多", "我话很多", "我通常说话很多", "我平时话很多",
                "我喜欢一直聊天", "我说话偏多", "我话多一些", "我一般说话很多", "我说话非常多", "我经常主动开口");
        return Map.copyOf(map);
    }

    private static void preference(Map<String, Expectation> map, String... texts) {
        for (String text : texts) map.put(text, new Expectation(Section.PREFERENCE, "partner.social.initiative", "high"));
    }

    private static void self(Map<String, Expectation> map, String code, String value, String... texts) {
        for (String text : texts) map.put(text, new Expectation(Section.SELF, code, value));
    }
}
