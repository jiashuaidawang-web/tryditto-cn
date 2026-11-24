package cn.aiditto.voice;

import cn.aiditto.domain.voice.VoiceModels.Scenario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** LLM 出题输出的严格解析闸门：预测排序必须是排列、PII 一律判废、格式容错。 */
class ScenarioPromptsTest {

    private static final String GOOD = """
            {"scenario":"你和刚认识的人在咖啡馆，对方迟到十分钟。你会怎么做？",
             "options":[
               {"key":"A","text":"发消息开玩笑说罚一杯","trait":"humor","predict_rank":1},
               {"key":"B","text":"先安静等对方","trait":"cautious","predict_rank":2},
               {"key":"C","text":"直接打电话问在哪","trait":"direct","predict_rank":3}]}
            """;

    @Test
    void parsesValidQuestionWithPredictRanking() {
        Scenario s = ScenarioPrompts.parse(GOOD);
        assertThat(s).isNotNull();
        assertThat(s.valid()).isTrue();
        assertThat(s.options()).hasSize(3);
        assertThat(s.options().get(0).predictRank()).isEqualTo(1);
        assertThat(s.options().get(0).key()).isEqualTo("A");
        // 前端视角不含 predict_rank 字段——解析出的 Option 仍保留它供内部校准用
    }

    @Test
    void stripsCodeFence() {
        Scenario s = ScenarioPrompts.parse("```json\n" + GOOD + "\n```");
        assertThat(s).isNotNull();
    }

    @Test
    void rejectsBadPredictRankPermutation() {
        // 两个 1、缺 3 → 破坏校准，判废
        String bad = GOOD.replace("\"predict_rank\":2", "\"predict_rank\":1")
                         .replace("\"predict_rank\":3", "\"predict_rank\":2");
        assertThat(ScenarioPrompts.parse(bad)).isNull();
    }

    @Test
    void rejectsPiiInOptions() {
        String pii = GOOD.replace("罚一杯", "打我手机 13800138000");
        assertThat(ScenarioPrompts.parse(pii)).isNull();
    }

    @Test
    void rejectsMissingFields() {
        assertThat(ScenarioPrompts.parse("{\"scenario\":\"x\"}")).isNull();
        assertThat(ScenarioPrompts.parse("not json")).isNull();
        assertThat(ScenarioPrompts.parse("")).isNull();
    }

    @Test
    void promptTellsModelToRankByPrediction() {
        assertThat(ScenarioPrompts.system()).contains("predict_rank").contains("最可能");
        assertThat(ScenarioPrompts.user(java.util.List.of())).contains("第一题");
        assertThat(ScenarioPrompts.user(java.util.List.of("情景：x；用户选了 A"))).contains("修正");
    }
}
