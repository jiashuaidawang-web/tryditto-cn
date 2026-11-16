package cn.aiditto.ai;

/** 一次大模型调用的输入。字段全部来自白名单，调用方负责脱敏（技术方案 §8.1）。 */
public record LlmRequest(
        String callPoint,
        String promptVersion,
        String system,
        String user,
        int maxTokens,
        double temperature,
        boolean jsonMode,
        boolean disableThinking) {

    public LlmRequest {
        if (callPoint == null || callPoint.isBlank()) throw new IllegalArgumentException("callPoint");
        if (promptVersion == null) promptVersion = "v1";
        if (system == null) system = "";
        if (user == null) user = "";
        if (maxTokens <= 0) maxTokens = 2048;
    }

    /** 校验失败时把失败原因拼进重试提示（技术方案 §8.3）。 */
    public LlmRequest withValidationHint(String reason) {
        return new LlmRequest(callPoint, promptVersion, system,
                user + "\n\n上次输出的问题：" + reason, maxTokens, temperature, jsonMode, disableThinking);
    }

    /** content 为空且 finish_reason=length 时翻倍（上限 8192）。 */
    public LlmRequest withMaxTokens(int n) {
        int next = Math.min(8192, Math.max(1, n));
        return new LlmRequest(callPoint, promptVersion, system, user, next, temperature, jsonMode, disableThinking);
    }
}
