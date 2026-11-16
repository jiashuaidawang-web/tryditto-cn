package cn.aiditto.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** 网关编排：主备切换 / 重试 / 熔断 / 兜底 / 日志。纯 Java，不连数据库与网络。 */
class LlmGatewayTest {

    private static final Predicate<String> JSON = s -> s != null && s.trim().startsWith("{") && s.trim().endsWith("}");

    private static LlmProperties props(int attempts, int circuit, boolean fallbackAvailable) {
        return new LlmProperties("mock", "http://primary", "key", "LongCat-2.5",
                fallbackAvailable ? "http://fallback" : "", fallbackAvailable ? "key2" : "", "deepseek-flash",
                attempts, 1000L, 0L, circuit, "", "");
    }

    private record Rig(MockLlmModel primary, MockLlmModel fallback, LlmCallLogger.InMemory log, LlmGateway gateway) {}

    private static Rig rig(LlmProperties p, Predicate<String> validator) {
        MockLlmModel primary = new MockLlmModel("mock-primary");
        MockLlmModel fallback = new MockLlmModel("mock-fallback");
        LlmCallLogger.InMemory log = new LlmCallLogger.InMemory();
        AtomicLong ids = new AtomicLong(1);
        return new Rig(primary, fallback, log,
                new LlmGateway(primary, fallback, log, p, validator, ids::getAndIncrement, ms -> {}));
    }

    private static LlmRequest req() {
        return new LlmRequest("unit_test", "v1", "system", "user", 1024, 0.2, true, false);
    }

    @Test
    void happyPathCallsPrimaryOnceAndLogsOk() {
        Rig r = rig(props(3, 5, true), JSON);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isTrue();
        assertThat(out.model()).isEqualTo("mock-primary");
        assertThat(r.primary().calls()).hasSize(1);
        assertThat(r.fallback().calls()).isEmpty();
        assertThat(r.log().count("ok")).isEqualTo(1);
        assertThat(r.log().entries().get(0).callPoint()).isEqualTo("unit_test");
    }

    @Test
    void timeoutThenSuccessRetriesAndKeepsDegradedFalse() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.TIMEOUT);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isTrue();
        assertThat(out.attempts()).isEqualTo(2);
        assertThat(r.primary().calls()).hasSize(2);
        assertThat(r.log().count("retry")).isEqualTo(1);
    }

    @Test
    void primaryExhaustedSwitchesToFallback() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.SERVER_ERROR, MockLlmModel.Fault.TIMEOUT,
                MockLlmModel.Fault.SERVER_ERROR);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isTrue();
        assertThat(out.model()).isEqualTo("mock-fallback");
        assertThat(r.fallback().calls()).hasSize(1);
    }

    @Test
    void authSwitchesImmediatelyWithoutRetry() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.AUTH);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isTrue();
        assertThat(out.model()).isEqualTo("mock-fallback");
        assertThat(r.primary().calls()).hasSize(1);
        assertThat(r.log().count("failed")).isEqualTo(1);
    }

    @Test
    void rateLimitDoesNotOpenCircuit() {
        Rig r = rig(props(3, 2, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.RATE_LIMIT, MockLlmModel.Fault.RATE_LIMIT);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isTrue();
        assertThat(out.model()).isEqualTo("mock-primary");
        assertThat(r.primary().calls()).hasSize(3);
    }

    @Test
    void emptyContentDoublesMaxTokens() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.EMPTY_CONTENT);
        assertThat(r.gateway().call(req()).ok()).isTrue();
        assertThat(r.primary().calls().get(0).maxTokens()).isEqualTo(1024);
        assertThat(r.primary().calls().get(1).maxTokens()).isEqualTo(2048);
    }

    @Test
    void validationFailureAppendsHintToRetryPrompt() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.BAD_JSON);
        assertThat(r.gateway().call(req()).ok()).isTrue();
        assertThat(r.primary().calls().get(1).user()).contains("上次输出的问题");
    }

    @Test
    void exhaustedReturnsDegradedAndCallerMustFallback() {
        Rig r = rig(props(3, 5, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.SERVER_ERROR, MockLlmModel.Fault.SERVER_ERROR,
                MockLlmModel.Fault.SERVER_ERROR);
        r.fallback().enqueue(MockLlmModel.Fault.SERVER_ERROR);
        LlmResult out = r.gateway().call(req());
        assertThat(out.ok()).isFalse();
        assertThat(out.degraded()).isTrue();
        assertThat(r.log().count("degraded")).isEqualTo(1);
    }

    @Test
    void fallbackDisabledWhenNoKey() {
        Rig r = rig(props(3, 5, false), JSON);
        r.primary().enqueue(MockLlmModel.Fault.SERVER_ERROR, MockLlmModel.Fault.SERVER_ERROR,
                MockLlmModel.Fault.SERVER_ERROR);
        assertThat(r.gateway().call(req()).degraded()).isTrue();
        assertThat(r.fallback().calls()).isEmpty();
    }

    @Test
    void circuitBreakerSkipsPrimaryAfterConsecutiveFailures() {
        Rig r = rig(props(1, 2, true), JSON);
        r.primary().enqueue(MockLlmModel.Fault.SERVER_ERROR, MockLlmModel.Fault.SERVER_ERROR);
        r.fallback().enqueue(MockLlmModel.Fault.SERVER_ERROR, MockLlmModel.Fault.SERVER_ERROR);
        r.gateway().call(req());
        r.gateway().call(req());
        int primaryCallsBefore = r.primary().calls().size();
        r.gateway().call(req());
        assertThat(r.primary().calls()).hasSize(primaryCallsBefore); // 熔断后主模型直接跳过
        assertThat(r.log().count("degraded")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mockIsDeterministic() {
        Rig r = rig(props(3, 5, true), JSON);
        assertThat(r.gateway().call(req()).content()).isEqualTo(r.gateway().call(req()).content());
    }

    @Test
    void gateMetrics_retryPassRateAndTemplateFallbackRate() {
        Rig r = rig(props(3, 100, true), JSON);
        int total = 100;
        int ok = 0;
        for (int i = 0; i < total; i++) {
            // 前两次故障、第三次成功：验证「重试后通过率」
            r.primary().enqueue(MockLlmModel.Fault.TIMEOUT, MockLlmModel.Fault.SERVER_ERROR);
            if (r.gateway().call(req()).ok()) ok++;
        }
        assertThat((double) ok / total).isGreaterThanOrEqualTo(0.95);

        // 主密钥错误（AUTH）整批仍由备用完成
        int authTotal = 20;
        int authOk = 0;
        for (int i = 0; i < authTotal; i++) {
            r.primary().enqueue(MockLlmModel.Fault.AUTH);
            if (r.gateway().call(req()).ok()) authOk++;
        }
        assertThat(authOk).isEqualTo(authTotal);

        Rig bad = rig(props(1, 100, false), JSON);
        int degraded = 0;
        for (int i = 0; i < 100; i++) {
            bad.primary().enqueue(MockLlmModel.Fault.SERVER_ERROR);
            if (bad.gateway().call(req()).degraded()) degraded++;
        }
        // 全故障极端场景兜底率 100%；正常批次由上层模板兜底率闸门单独验收
        assertThat(degraded).isEqualTo(100);
    }
}
