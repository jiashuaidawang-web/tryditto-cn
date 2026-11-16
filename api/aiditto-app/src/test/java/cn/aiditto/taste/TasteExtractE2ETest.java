package cn.aiditto.taste;

import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmProperties;
import cn.aiditto.ai.OpenAiCompatModel;
import cn.aiditto.domain.taste.TasteFacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实模型画像抽取验收：证明 taste-v1 提示词能被线上模型正确遵循，
 * 而不是所有批次都悄悄落到模板兜底（C 端上线前必须确认 AI 能力真的生效）。
 *
 * 默认跳过；与 DeepSeekFallbackE2ETest 共用 AIDITTO_LLM_E2E=1 开关与同一组环境变量。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_LLM_E2E", matches = "1")
class TasteExtractE2ETest {

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) throw new IllegalStateException("missing env " + name);
        return v;
    }

    @Test
    void realModelReturnsParseableDesensitizedTasteDraft() {
        LlmProperties p = new LlmProperties("live", "", "", "LongCat-2.5",
                env("LLM_FALLBACK_BASE_URL"), env("LLM_FALLBACK_API_KEY"),
                env("LLM_FALLBACK_MODEL"), 1, 30_000L, 0L, 5, "", "");
        OpenAiCompatModel model = new OpenAiCompatModel(p.fallbackBaseUrl(), p.fallbackApiKey(),
                p.fallbackModel(), p.timeoutMs(), p.fallbackExtraBodyMap());

        TasteFacts facts = TasteFacts.from(1L, Map.of(
                "hobbies", List.of("reading", "music"),
                "matchingStyle", "slow",
                "relationshipLooking", List.of("serious"),
                "firstMessage", "先聊聊最近读的书"), TasteWriter.PROMPT_VERSION, null);

        LlmModel.Raw raw = model.call(TasteWriter.request(facts));
        TasteWriter.Draft draft = TasteWriter.parse(raw.content());

        assertThat(draft).isNotNull();
        assertThat(draft.fromModel()).isTrue();
        assertThat(draft.persona()).isNotBlank();
        assertThat(draft.taste()).isNotBlank();
        assertThat(draft.persona() + draft.taste()).doesNotContain("@", "13800138000", "sk-", "{{");
    }
}
