package cn.aiditto.ai;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * 大模型网关：主备切换、重试、熔断、调用日志、兜底（技术方案 §8.2 / §8.3）。
 *
 * 规则：
 * - 主模型最多 maxAttempts 次；超时/5xx 退避重试，429 按 Retry-After 等待且不计入熔断
 * - 400/401/403 不重试，直接切备用
 * - content 空且 finish_reason=length → maxTokens 翻倍（上限 8192）
 * - 解析/校验失败 → 把失败原因追加进提示词重试
 * - 主模型连续失败达阈值 → 后续调用直接走备用；备用也达阈值 → 直接兜底
 * - 全部用尽返回 degraded=true，调用方必须走模板/粗排兜底，绝不把错误抛给用户
 */
public final class LlmGateway {

    private final LlmModel primary;
    private final LlmModel fallback;
    private final LlmCallLogger logger;
    private final LlmProperties props;
    private final Predicate<String> validator;
    private final LongSupplier ids;
    private final LongConsumer sleeper;
    private int consecutivePrimaryFailures;
    private int consecutiveFallbackFailures;

    public LlmGateway(LlmModel primary, LlmModel fallback, LlmCallLogger logger,
                      LlmProperties props, Predicate<String> validator,
                      LongSupplier ids, LongConsumer sleeper) {
        this.primary = primary;
        this.fallback = fallback;
        this.logger = logger;
        this.props = props;
        this.validator = validator == null ? (s -> s != null && !s.isBlank()) : validator;
        this.ids = ids == null ? () -> System.nanoTime() : ids;
        this.sleeper = sleeper == null ? LlmGateway::sleep : sleeper;
    }

    /** 每个批次开始时调用，清零熔断计数。 */
    public synchronized void resetCircuit() {
        consecutivePrimaryFailures = 0;
        consecutiveFallbackFailures = 0;
    }

    public synchronized LlmResult call(LlmRequest request) {
        long start = System.currentTimeMillis();
        LlmRequest req = request;
        int attempts = 0;
        boolean primaryOpen = consecutivePrimaryFailures >= props.circuitThreshold();

        if (!primaryOpen) {
            for (int i = 0; i < props.maxAttempts(); i++) {
                attempts++;
                try {
                    LlmModel.Raw raw = primary.call(req);
                    if (!validator.test(raw.content())) {
                        throw new LlmException(LlmException.Kind.VALIDATION, "输出未通过程序校验");
                    }
                    LlmResult ok = result(raw.content(), primary.name(), attempts, start, raw);
                    log(request, primary.name(), raw, "ok", null, start);
                    consecutivePrimaryFailures = 0;
                    return ok;
                } catch (LlmException e) {
                    log(request, primary.name(), null,
                            e.switchProviderImmediately() ? "failed" : "retry", e.kind().name(), start);
                    if (e.kind() != LlmException.Kind.RATE_LIMIT) consecutivePrimaryFailures++;
                    if (e.switchProviderImmediately()) break;
                    req = adjust(req, e);
                    sleeper.accept(waitMs(e, i));
                    if (consecutivePrimaryFailures >= props.circuitThreshold()) break;
                }
            }
        }

        if (fallback != null && props.fallbackAvailable()
                && consecutiveFallbackFailures < props.circuitThreshold()) {
            attempts++;
            try {
                LlmModel.Raw raw = fallback.call(req);
                if (!validator.test(raw.content())) {
                    throw new LlmException(LlmException.Kind.VALIDATION, "输出未通过程序校验");
                }
                LlmResult ok = result(raw.content(), fallback.name(), attempts, start, raw);
                log(request, fallback.name(), raw, "ok", null, start);
                consecutiveFallbackFailures = 0;
                return ok;
            } catch (LlmException e) {
                log(request, fallback.name(), null, "failed", e.kind().name(), start);
                consecutiveFallbackFailures++;
            }
        }

        log(request, "none", null, "degraded", "EXHAUSTED", start);
        return LlmResult.fallback(attempts, System.currentTimeMillis() - start);
    }

    private LlmRequest adjust(LlmRequest req, LlmException e) {
        if (e.kind() == LlmException.Kind.EMPTY_CONTENT) return req.withMaxTokens(req.maxTokens() * 2);
        if (e.kind() == LlmException.Kind.VALIDATION || e.kind() == LlmException.Kind.BAD_RESPONSE) {
            return req.withValidationHint(e.getMessage());
        }
        return req;
    }

    private long waitMs(LlmException e, int attempt) {
        if (e.kind() == LlmException.Kind.RATE_LIMIT) {
            try {
                return e.retryAfter() == null ? 10_000L : Math.max(0, Long.parseLong(e.retryAfter()) * 1000L);
            } catch (NumberFormatException ignored) {
                return 10_000L;
            }
        }
        // 退避 2 秒、6 秒（技术方案 §8.3）
        return props.retryDelayMs() * (attempt + 1);
    }

    private LlmResult result(String content, String model, int attempts, long start, LlmModel.Raw raw) {
        return new LlmResult(content, model, false, attempts,
                System.currentTimeMillis() - start, raw.inputTokens(), raw.outputTokens());
    }

    private void log(LlmRequest req, String model, LlmModel.Raw raw, String status, String errorCode, long start) {
        logger.log(new LlmCallLogger.Entry(ids.getAsLong(), null, req.callPoint(), model, req.promptVersion(),
                raw == null ? null : raw.inputTokens(), raw == null ? null : raw.outputTokens(),
                System.currentTimeMillis() - start, status, errorCode));
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
