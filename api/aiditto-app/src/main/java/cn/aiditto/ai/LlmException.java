package cn.aiditto.ai;

/** 模型调用失败分类，决定重试 / 切备用 / 熔断策略（技术方案 §8.3）。 */
public final class LlmException extends RuntimeException {
    public enum Kind {
        TIMEOUT, RATE_LIMIT, SERVER_ERROR, AUTH, EMPTY_CONTENT, BAD_RESPONSE, VALIDATION
    }

    private final Kind kind;
    private final String retryAfter;

    public LlmException(Kind kind, String message) {
        this(kind, message, null);
    }

    public LlmException(Kind kind, String message, String retryAfter) {
        super(message);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public String retryAfter() {
        return retryAfter;
    }

    /** 400/401/403 不重试，直接切备用。 */
    public boolean switchProviderImmediately() {
        return kind == Kind.AUTH;
    }
}
