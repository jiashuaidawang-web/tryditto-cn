package cn.aiditto.ai;

/**
 * 网关返回。degraded=true 表示主备全部用尽，调用方必须走模板/粗排兜底，
 * 大模型失败不能变成用户可见错误（技术方案 §6.8 / §8.2）。
 */
public record LlmResult(
        String content,
        String model,
        boolean degraded,
        int attempts,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens) {

    public static LlmResult fallback(int attempts, long latencyMs) {
        return new LlmResult(null, null, true, attempts, latencyMs, null, null);
    }

    public boolean ok() {
        return !degraded && content != null && !content.isBlank();
    }
}
