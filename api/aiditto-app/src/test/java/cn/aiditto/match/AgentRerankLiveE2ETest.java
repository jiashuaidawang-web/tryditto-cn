package cn.aiditto.match;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6 真实模型 E2E：验证裁判 rubric 在真实 LLM 上能产出**通过服务端严格校验**的 JSON，
 * 且不会把"爱好不同但内核同为冒险型"误判成硬冲突（Ditto 核心主张）。
 *
 * 默认跳过；AIDITTO_LLM_E2E=1 + 网关环境变量开启。密钥只从环境变量读。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_LLM_E2E", matches = "1")
class AgentRerankLiveE2ETest {

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? def : v;
    }

    @Test
    void realModelProducesVerdictThatPassesStrictValidation() {
        LlmGateway gateway = new LlmGateway(
                new cn.aiditto.ai.OpenAiCompatModel(
                        env("LLM_FALLBACK_BASE_URL", "https://api.vveai.com/v1"),
                        env("LLM_E2E_API_KEY", ""),
                        env("LLM_E2E_MODEL", "deepseek-v4-flash"), 30_000L,
                        java.util.Map.of()),
                null,
                new cn.aiditto.ai.LlmCallLogger.InMemory(),
                new cn.aiditto.ai.LlmProperties("live", "", "", "", "", "", "",
                        2, 30_000L, 0L, 5, "", ""),
                s -> s != null && !s.isBlank(),
                () -> 1L, null);

        String personaA = "喜欢攀岩、徒步和户外冒险，周末总往山里跑，享受挑战和新鲜感，说话直接。";
        String personaB = "爱跳街舞和逛潮流市集，喜欢尝试没做过的事，节奏快，表达欲强。";
        LlmResult sim = gateway.call(new LlmRequest("simulate_date", AiJudge.SIM_PROMPT_VERSION,
                AiJudge.simulateSystem(), AiJudge.simulateUser(personaA, personaB, "校园"),
                700, 0.8, true, true));
        System.out.println("[STEP6-LIVE] simulate ok=" + sim.ok() + " degraded=" + sim.degraded());

        LlmResult judged = gateway.call(new LlmRequest("judge_chemistry", AiJudge.PROMPT_VERSION,
                AiJudge.judgeSystem(), AiJudge.judgeUser(personaA, personaB, "", "",
                        sim.ok() ? sim.content() : "", true, true),
                700, 0.2, true, true));
        assertThat(judged.ok()).as("真实模型应返回裁判结果").isTrue();
        System.out.println("[STEP6-LIVE] judge content=" + judged.content().substring(0,
                Math.min(260, judged.content().length())).replace("\n", " "));

        AiJudge.Verdict v = AiJudge.parse(judged.content(), Set.of());
        assertThat(v).as("真实模型输出必须通过服务端严格校验（不能靠宽容解析糊过去）").isNotNull();
        assertThat(v.valid()).isTrue();
        assertThat(v.topics()).hasSize(3);
        assertThat(v.reasonText().length()).isLessThanOrEqualTo(40);
        assertThat(v.total()).isBetween(0, 100);
        assertThat(v.conflicts()).as("模型不得把'爱好不同'当硬冲突").isEmpty();
        System.out.printf("[STEP6-LIVE] verdict total=%d dims=%s conf=%.2f reason=%s topics=%s%n",
                v.total(), v.dims(), v.confidence(), v.reasonText(), v.topics());

        // 核心主张：表面零交集、内核同为冒险型，应得到中上分而非被毙
        assertThat(v.total()).as("跨表面共鸣不得被误杀").isGreaterThanOrEqualTo(50);
    }
}
