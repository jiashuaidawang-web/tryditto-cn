package cn.aiditto.domain.reasoning.v4;

/**
 * One completion for one direction. Implementations must not keep the other direction's output,
 * and a failure must not be replaced with another model's text.
 */
public interface ReasoningModel {
    ReasoningCompletion complete(ReasoningPrompt.Rendered prompt);

    record ReasoningCompletion(String content, Integer inputTokens, Integer outputTokens,
                               Long latencyNanos, String modelName, boolean failed, String failure) {
        public static ReasoningCompletion ok(String content, String modelName, long latencyNanos) {
            return new ReasoningCompletion(content, null, null, latencyNanos, modelName, false, null);
        }

        public static ReasoningCompletion failed(String modelName, String failure) {
            return new ReasoningCompletion(null, null, null, null, modelName, true, failure);
        }
    }
}
