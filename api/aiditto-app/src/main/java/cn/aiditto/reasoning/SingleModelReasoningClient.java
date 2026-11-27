package cn.aiditto.reasoning;

import cn.aiditto.ai.LlmModel;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.domain.reasoning.v4.ReasoningModel;

/**
 * One model, no fallback. A gateway that can switch providers is not used here,
 * because a fallback response would mix a second model into the experiment.
 */
public final class SingleModelReasoningClient implements ReasoningModel {
    private final LlmModel model;

    public SingleModelReasoningClient(LlmModel model) {
        this.model = model;
    }

    @Override
    public ReasoningCompletion complete(cn.aiditto.domain.reasoning.v4.ReasoningPrompt.Rendered prompt) {
        try {
            LlmModel.Raw raw = model.call(new LlmRequest("m03-reasoning", prompt.version(), prompt.system(), prompt.user(),
                    1200, 0.0, true, true));
            if (raw == null || raw.content() == null || raw.content().isBlank()) {
                return ReasoningCompletion.failed(model.name(), "EMPTY");
            }
            return new ReasoningCompletion(raw.content(), raw.inputTokens(), raw.outputTokens(), null, model.name(), false, null);
        } catch (RuntimeException ex) {
            return ReasoningCompletion.failed(model.name(), "MODEL_FAILED");
        }
    }
}
