package cn.aiditto.taste;

import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.taste.TasteFacts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 输出校验与降级：模型不可用 / 输出不合规时画像仍必须存在。 */
class TasteWriterTest {

    private static TasteFacts facts() {
        return TasteFacts.from(1L, Map.of(
                "hobbies", List.of("reading", "music"),
                "matchingStyle", "slow",
                "firstMessage", "先聊聊最近读的书"), "taste-v1", null);
    }

    private static LlmResult ok(String content) {
        return new LlmResult(content, "mock-model", false, 1, 1L, 10, 20);
    }

    @Test
    void parsesStrictJson() {
        TasteWriter.Draft d = TasteWriter.parse(
                "{\"persona\":\"安静但健谈\",\"taste\":\"想找能一起看书的人\"}");
        assertThat(d).isNotNull();
        assertThat(d.persona()).isEqualTo("安静但健谈");
        assertThat(d.taste()).isEqualTo("想找能一起看书的人");
        assertThat(d.fromModel()).isTrue();
    }

    @Test
    void stripsCodeFenceBeforeParsing() {
        TasteWriter.Draft d = TasteWriter.parse(
                "```json\n{\"persona\":\"甲\",\"taste\":\"乙\"}\n```");
        assertThat(d).isNotNull();
        assertThat(d.persona()).isEqualTo("甲");
    }

    @Test
    void rejectsMalformedMissingFieldsOversizedOrLeakingOutput() {
        assertThat(TasteWriter.parse("not-json")).isNull();
        assertThat(TasteWriter.parse("{\"persona\":\"只有一段\"}")).isNull();
        assertThat(TasteWriter.parse("{}")).isNull();
        assertThat(TasteWriter.parse("{\"persona\":\"\",\"taste\":\"乙\"}")).isNull();
        assertThat(TasteWriter.parse("{\"persona\":\"联系 a@b.com\",\"taste\":\"乙\"}")).isNull();
        assertThat(TasteWriter.parse("{\"persona\":\"打 13800138000\",\"taste\":\"乙\"}")).isNull();
        assertThat(TasteWriter.parse("{\"persona\":\"" + "x".repeat(400) + "\",\"taste\":\"乙\"}")).isNull();
    }

    @Test
    void templateIsDeterministicAndDesensitized() {
        TasteWriter.Draft a = TasteWriter.template(facts(), "degraded");
        TasteWriter.Draft b = TasteWriter.template(facts(), "degraded");
        assertThat(a.persona()).isEqualTo(b.persona());
        assertThat(a.taste()).isEqualTo(b.taste());
        assertThat(a.fromModel()).isFalse();
        assertThat(a.reason()).isEqualTo("degraded");
        assertThat(a.persona()).contains("reading", "slow", "先聊聊最近读的书");
        assertThat(a.persona() + a.taste()).doesNotContain("@", "sk-", "{{");
    }

    @Test
    void chooseFallsBackOnDegradedExceptionAndInvalidOutput() {
        TasteFacts f = facts();
        assertThat(TasteWriter.choose(LlmResult.fallback(3, 10L), f).reason()).isEqualTo("degraded");
        assertThat(TasteWriter.choose(null, f).reason()).isEqualTo("llm_exception");
        assertThat(TasteWriter.choose(ok("not-json"), f).reason()).isEqualTo("invalid_llm_output");
        assertThat(TasteWriter.choose(ok("{\"persona\":\"甲\",\"taste\":\"乙\"}"), f).fromModel()).isTrue();
        assertThat(TasteWriter.choose(LlmResult.fallback(3, 10L), f).persona()).isNotBlank();
    }

    @Test
    void requestUsesWhitelistedFactsOnlyAndDisablesThinking() {
        LlmRequest r = TasteWriter.request(facts());
        assertThat(r.callPoint()).isEqualTo("taste_extract");
        assertThat(r.promptVersion()).isEqualTo("taste-v1");
        assertThat(r.jsonMode()).isTrue();
        assertThat(r.disableThinking()).isTrue();
        assertThat(r.user()).contains("reading").doesNotContain("gender", "birthday", "@", "13800138000");
        assertThat(r.system()).contains("不推测性别");
    }
}
