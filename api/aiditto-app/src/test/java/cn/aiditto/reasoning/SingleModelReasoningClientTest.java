package cn.aiditto.reasoning;

import cn.aiditto.ai.LlmException;
import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.domain.reasoning.v4.ReasoningModel;
import cn.aiditto.domain.reasoning.v4.ReasoningPrompt;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SingleModelReasoningClientTest {
    @Test
    void aFailedCallDoesNotSubstituteAnotherModel() {
        LlmModel broken = new LlmModel() {
            @Override public String name() { return "deepseek-flash"; }
            @Override public Raw call(LlmRequest request) { throw new LlmException(LlmException.Kind.TIMEOUT, "timeout"); }
        };
        ReasoningModel.ReasoningCompletion result = new SingleModelReasoningClient(broken).complete(
                new ReasoningPrompt.Rendered("reasoning-prompt-1", "hash", "system", "DIRECTION AB", "digest", null));
        assertThat(result.failed()).isTrue();
        assertThat(result.content()).isNull();
        assertThat(result.modelName()).isEqualTo("deepseek-flash");
    }

    @Test
    void aMissingExperimentCredentialStaysNotRun() {
        assertThat(ReasoningLiveRunner.run(new String[]{"preflight"}, Map.of())).isEqualTo(2);
        assertThat(ReasoningLiveRunner.run(new String[]{"preflight"}, Map.of("LLM_FALLBACK_API_KEY", "present"))).isEqualTo(2);
    }
}
