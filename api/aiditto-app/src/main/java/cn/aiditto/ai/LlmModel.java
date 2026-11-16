package cn.aiditto.ai;

/** 一个模型端点。真实实现走 OpenAI 兼容 HTTP；测试实现是确定性 Mock。 */
public interface LlmModel {

    record Raw(String content, String finishReason, Integer inputTokens, Integer outputTokens) {}

    String name();

    Raw call(LlmRequest request) throws LlmException;
}
