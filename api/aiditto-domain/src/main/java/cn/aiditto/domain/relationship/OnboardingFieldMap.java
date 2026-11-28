package cn.aiditto.domain.relationship;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct map from the questionnaire the product already stores to {@link FeatureRegistry} v1.
 * A missing answer stays missing. Refusal is not a dislike. Self traits are not copied into partner preferences.
 * Question ids outside {@link RelationshipProfileProjector#STORED_QUESTIONS} stay {@code UNMAPPED}.
 */
public final class OnboardingFieldMap {
    public record Row(
            String questionCode,
            String kind,
            String profileSection,
            String featureCode,
            String conversion,
            String operator,
            String importance,
            String evidenceStatus,
            boolean matchUse,
            boolean hardConstraint,
            boolean sensitive,
            boolean extraConfirm,
            String support) {}

    private static final Map<String, Row> MAPPED = mapped();

    private OnboardingFieldMap() {}

    public static Row row(String questionCode) {
        Row mapped = MAPPED.get(questionCode);
        if (mapped != null) return mapped;
        return unmapped(questionCode, false);
    }

    public static List<Row> cover(QuestionCatalog catalog) {
        List<Row> rows = new ArrayList<>();
        if (catalog == null) {
            rows.addAll(MAPPED.values());
            return List.copyOf(rows);
        }
        for (QuestionSpec spec : catalog.all()) {
            Row mapped = MAPPED.get(spec.id());
            rows.add(mapped != null ? mapped : unmapped(spec.id(), catalog.isSensitive(spec.id())));
        }
        return List.copyOf(rows);
    }

    public static int mappedCount() {
        return MAPPED.size();
    }

    private static Row unmapped(String questionCode, boolean sensitive) {
        return new Row(questionCode, "UNMAPPED", "", "",
                "没有合法映射，保持 UNMAPPED。缺失不补，拒答不当成不喜欢。",
                "", "", "", false, false, sensitive, false, "UNMAPPED");
    }

    private static Map<String, Row> mapped() {
        Map<String, Row> rows = new LinkedHashMap<>();
        put(rows, self("hobbies", "lifestyle.hobbies", "只保留题库中的八个爱好。自定义项和缺失项不进入。"));
        put(rows, self("cookingStyle", "lifestyle.cooking", "仅当爱好包含 cooking 时保留合法选项。"));
        put(rows, self("musicSound", "lifestyle.music", "仅当爱好包含 music 时保留合法选项。"));
        put(rows, self("travelStyle", "lifestyle.travel", "仅当爱好包含 travel 时保留合法选项。"));
        put(rows, self("readingGenres", "lifestyle.reading", "仅当爱好包含 reading 时保留合法选项。"));
        put(rows, self("gymTrainingStyle", "lifestyle.gym", "仅当爱好包含 gym 时保留合法选项。"));
        put(rows, self("gamingGenres", "lifestyle.gaming", "仅当爱好包含 gaming 时保留合法选项。"));
        put(rows, self("moviesContent", "lifestyle.movies", "仅当爱好包含 movies 时保留合法选项。"));
        put(rows, self("artMediums", "lifestyle.art", "仅当爱好包含 art 时保留合法选项。"));
        put(rows, self("relationshipLooking", "relationship.goal", "只保留注册表里的关系目标。"));
        put(rows, self("matchingStyle", "matching.pace", "只保留注册表里的筛选节奏。"));
        put(rows, self("isStudent", "life.student", "yes 或 no 才进入。其他值不猜。"));
        put(rows, self("schoolYear", "education.year", "只保留注册表里的年级。"));
        put(rows, new Row("educationLevel", "SELF_FACT", "SELF", "education.level",
                "合法学历是 CONFIRMED。preferNot 是 WITHHELD。缺失不存在，不当成低学历。",
                "", "", "CONFIRMED 或 WITHHELD", true, false, false, false, "MAPPED"));
        put(rows, new Row("firstDateBudget", "SELF_FACT", "SELF", "spending.first_date",
                "合法档位是 CONFIRMED。noPref 是 UNBOUNDED。缺失不存在。",
                "", "", "CONFIRMED 或 UNBOUNDED", true, false, false, false, "MAPPED"));
        put(rows, self("datingHelp", "support.help_wanted", "只保留注册表里的帮助项。"));
        put(rows, new Row("occupation", "SELF_FACT", "SELF", "occupation.text",
                "原文不进快照，只保留 onboarding_answer.occupation 引用。",
                "", "", "CONFIRMED", true, false, true, false, "MAPPED"));
        put(rows, new Row("firstMessage", "BEHAVIOR_FACT", "BEHAVIOR", "opening.message",
                "有原文才进入行为事实，类型是 SELF_REPORTED。原文不进快照。",
                "", "", "CONFIRMED", true, false, true, false, "MAPPED"));
        put(rows, new Row("expectedAgeRange", "PARTNER_PREFERENCE", "PREFERENCE", "partner.age",
                "两个整数边界才进入。缺失不补年龄偏好。",
                "BETWEEN", "SUPER", "CONFIRMED", true, true, false, false, "MAPPED"));
        put(rows, new Row("heightRange", "PARTNER_PREFERENCE", "PREFERENCE", "partner.height",
                "any 是不设限。同时给出上下限才是硬约束。缺失不补。",
                "ANY 或 BETWEEN", "NONE 或 SUPER", "CONFIRMED", true, false, false, false, "MAPPED"));
        put(rows, new Row("educationImportance", "PARTNER_PREFERENCE", "PREFERENCE", "partner.education",
                "none 是不设限。kinda 是软偏好。super 是硬约束。缺失不补。",
                "ANY 或 AT_LEAST", "NONE、KINDA 或 SUPER", "CONFIRMED", true, false, false, false, "MAPPED"));
        put(rows, new Row("expectedGender", "PARTNER_PREFERENCE", "PREFERENCE", "partner.gender",
                "everyone 是不设限。其他选择是硬约束，值只保留 profile.expected_genders_enc 引用。",
                "ANY 或 CONTROLLED_REF", "NONE 或 SUPER", "CONFIRMED", true, false, true, false, "MAPPED"));
        if (!rows.keySet().equals(RelationshipProfileProjector.STORED_QUESTIONS)) {
            throw new IllegalStateException("field map drifted from the projector");
        }
        return Map.copyOf(rows);
    }

    private static void put(Map<String, Row> rows, Row row) {
        rows.put(row.questionCode(), row);
    }

    private static Row self(String questionCode, String featureCode, String conversion) {
        return new Row(questionCode, "SELF_FACT", "SELF", featureCode, conversion,
                "", "", "CONFIRMED", true, false, false, false, "MAPPED");
    }
}
