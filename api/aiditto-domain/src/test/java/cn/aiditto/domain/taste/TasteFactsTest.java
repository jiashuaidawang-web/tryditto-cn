package cn.aiditto.domain.taste;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;
import cn.aiditto.domain.questionnaire.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** 脱敏白名单与确定性指纹：C 端 PIPL 红线，任何一条挂了都不许上线。 */
class TasteFactsTest {

    private static QuestionSpec spec(String id, QuestionType type) {
        return new QuestionSpec(id, type, Set.of(), null, null, Set.of(), true, null, false, false, null, Set.of());
    }

    private static QuestionCatalog catalog() {
        List<QuestionSpec> specs = new ArrayList<>();
        specs.add(spec("hobbies", QuestionType.MULTI));
        specs.add(spec("firstMessage", QuestionType.LONG_TEXT));
        specs.add(spec("expectedGender", QuestionType.MULTI));
        specs.add(spec("schoolEmail", QuestionType.EMAIL));
        specs.add(spec("location", QuestionType.LOCATION));
        return new QuestionCatalog(specs);
    }

    @Test
    void whitelistDropsIdentityContactAndSensitiveAnswers() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("name", "张三");
        answers.put("gender", "male");
        answers.put("expectedGender", List.of("female"));
        answers.put("birthday", "2003-05-01");
        answers.put("schoolEmail", "zs@stu.pku.edu.cn");
        answers.put("personalEmail", "zs@qq.com");
        answers.put("emailVerify", "888888");
        answers.put("height", Map.of("cm", 170));
        answers.put("dealbreakers", "不接受异地");
        answers.put("politics", "x");
        answers.put("hobbies", List.of("reading", "movies"));
        answers.put("firstMessage", "你好呀");
        answers.put("location", Map.of("placeId", "p1", "lat", 39.9, "lng", 116.4,
                "mode", "transit", "minutes", 30));

        TasteFacts f = TasteFacts.from(1L, answers, "taste-v1", catalog());

        assertThat(f.values().keySet()).contains("hobbies", "firstMessage", "commute")
                .doesNotContain("name", "gender", "expectedGender", "birthday", "schoolEmail",
                        "personalEmail", "emailVerify", "height", "dealbreakers", "politics", "location");
        assertThat(f.canonical()).doesNotContain("张三", "zs@stu.pku.edu.cn", "888888",
                "39.9", "116.4", "p1", "异地", "female", "male");
        assertThat(f.values().get("commute"))
                .isEqualTo(new TreeMap<>(Map.of("mode", "transit", "minutes", 30)));
    }

    @Test
    void freeTextMasksContactsAndLongNumbers() {
        Map<String, Object> answers = Map.of("firstMessage",
                "联系我 13800138000 或 a@b.com 学号 2023123456 https://x.y/z");
        TasteFacts f = TasteFacts.from(2L, answers, "taste-v1", catalog());
        String text = String.valueOf(f.values().get("firstMessage"));
        assertThat(text).doesNotContain("13800138000", "a@b.com", "2023123456", "https://")
                .contains("[已隐藏]");
    }

    @Test
    void hashIsDeterministicAcrossMapImplementationsAndInsertionOrders() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("hobbies", List.of("music", "art"));
        a.put("matchingStyle", "slow");
        Map<String, Object> b = new HashMap<>();
        b.put("matchingStyle", "slow");
        b.put("hobbies", List.of("music", "art"));

        TasteFacts f1 = TasteFacts.from(3L, a, "taste-v1", catalog());
        TasteFacts f2 = TasteFacts.from(3L, b, "taste-v1", catalog());
        assertThat(f1.canonical()).isEqualTo(f2.canonical());
        assertThat(f1.inputHash()).isEqualTo(f2.inputHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void changedAnswersOrPromptVersionChangeHash() {
        TasteFacts f1 = TasteFacts.from(4L, Map.of("hobbies", List.of("music")), "taste-v1", catalog());
        TasteFacts f2 = TasteFacts.from(4L, Map.of("hobbies", List.of("art")), "taste-v1", catalog());
        TasteFacts f3 = TasteFacts.from(4L, Map.of("hobbies", List.of("music")), "taste-v2", catalog());
        assertThat(f2.inputHash()).isNotEqualTo(f1.inputHash());
        assertThat(f3.inputHash()).isNotEqualTo(f1.inputHash());
    }

    @Test
    void defenseInDepthDropsWhitelistedKeyWithForbiddenQuestionType() {
        QuestionCatalog c = new QuestionCatalog(List.of(spec("hobbies", QuestionType.EMAIL)));
        TasteFacts f = TasteFacts.from(5L, Map.of("hobbies", List.of("music")), "taste-v1", c);
        assertThat(f.values()).doesNotContainKey("hobbies");
    }

    @Test
    void unknownValueTypesAreDroppedRatherThanSerialized() {
        TasteFacts f = TasteFacts.from(6L, Map.of("hobbies", List.of(new Object())), "taste-v1", catalog());
        assertThat(f.values()).doesNotContainKey("hobbies");
    }
}
