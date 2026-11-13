package cn.aiditto.domain.questionnaire;

import java.util.Map;

/** Ditto's edit-route field names → question ids (技术方案 §4.4 别名表; web/src/app/profileFields.ts). */
public final class FieldAliases {
    private static final Map<String, String> DITTO_TO_QUESTION = Map.ofEntries(
            Map.entry("hobbyRanking", "hobbies"),
            Map.entry("intention", "relationshipLooking"),
            Map.entry("matchAccuracy", "matchingStyle"),
            Map.entry("referralSource", "hearAbout"),
            Map.entry("firstImpression", "firstMessage"),
            Map.entry("redFlags", "dealbreakers"),
            Map.entry("ageRange", "expectedAgeRange"),
            Map.entry("expectedHeightRange", "heightRange"),
            Map.entry("religious", "religion"),
            Map.entry("politicalBeliefs", "politics"));

    private FieldAliases() {}

    /** A question id stays as-is; a Ditto field name becomes its question id. */
    public static String toQuestionId(String field) {
        return DITTO_TO_QUESTION.getOrDefault(field, field);
    }

    public static Map<String, String> all() {
        return DITTO_TO_QUESTION;
    }
}
