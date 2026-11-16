package cn.aiditto.ai;

import cn.aiditto.infra.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实备用模型（DeepSeek-V4.1-Flash-1017）端到端验收。
 *
 * 默认跳过，日常 mvn verify 不需要网络与密钥。显式打开才连真实端点：
 * <pre>
 * AIDITTO_LLM_E2E=1 \
 * LLM_FALLBACK_BASE_URL=https://api.deepseek.com \
 * LLM_FALLBACK_API_KEY=&lt;key&gt; \
 * LLM_FALLBACK_MODEL=deepseek-flash \
 * mvn -pl aiditto-app -am -Dtest=DeepSeekFallbackE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
 * </pre>
 * 密钥只经环境变量进入进程，不写入代码、日志或仓库文件。
 */
@EnabledIfEnvironmentVariable(named = "AIDITTO_LLM_E2E", matches = "1")
class DeepSeekFallbackE2ETest {

    private static final Predicate<String> JSON =
            s -> s != null && s.trim().startsWith("{") && s.trim().endsWith("}");

    private static String env(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) throw new IllegalStateException("missing env " + name);
        return v;
    }

    private static LlmProperties props(String primaryBaseUrl, String primaryKey) {
        return new LlmProperties("live", primaryBaseUrl, primaryKey, "LongCat-2.5",
                env("LLM_FALLBACK_BASE_URL"), env("LLM_FALLBACK_API_KEY"),
                env("LLM_FALLBACK_MODEL"), 1, 30_000L, 0L, 5, "", "");
    }

    private static OpenAiCompatModel fallbackModel(LlmProperties p) {
        return new OpenAiCompatModel(p.fallbackBaseUrl(), p.fallbackApiKey(), p.fallbackModel(),
                p.timeoutMs(), p.fallbackExtraBodyMap());
    }

    @Test
    void primaryAuthFailureSwitchesToRealDeepSeek() throws IOException {
        try (UnauthorizedStub stub = new UnauthorizedStub()) {
            LlmProperties p = props(stub.baseUrl(), "invalid-key");
            LlmCallLogger.InMemory log = new LlmCallLogger.InMemory();
            LlmGateway gateway = new LlmGateway(
                    new OpenAiCompatModel(p.primaryBaseUrl(), p.primaryApiKey(), p.primaryModel(),
                            p.timeoutMs(), p.primaryExtraBodyMap()),
                    fallbackModel(p), log, p, JSON,
                    new AtomicLong(1)::getAndIncrement, ms -> {});

            LlmResult out = gateway.call(new LlmRequest("e2e_fallback", "v1",
                    "只输出一个 JSON 对象，不要解释、不要代码围栏",
                    "输出 {\"ok\":true,\"source\":\"deepseek\"}", 256, 0.0, true, false));

            assertThat(out.ok()).isTrue();
            assertThat(out.degraded()).isFalse();
            assertThat(out.model()).isEqualTo("deepseek-flash");
            assertThat(out.attempts()).isEqualTo(2);          // 主 1 次(401) + 备用 1 次
            assertThat(Json.readMap(out.content())).containsKey("ok");
            assertThat(log.count("failed")).isEqualTo(1);     // 主模型 401 立即切备
            assertThat(log.count("ok")).isEqualTo(1);         // 真实备用成功
        }
    }

    @Test
    void realDeepSeekReturnsParseableJsonWithoutFence() {
        LlmModel.Raw raw = fallbackModel(props("", "")).call(new LlmRequest("e2e_json", "v1",
                "只输出一个 JSON 对象，不要解释、不要代码围栏",
                "输出 {\"ok\":true,\"score\":88}", 256, 0.0, true, false));

        assertThat(raw.content()).isNotBlank();
        assertThat(raw.content()).doesNotContain("```");
        Map<String, Object> parsed = Json.readMap(raw.content());
        assertThat(parsed).containsKeys("ok", "score");
    }

    /** 本地 401 stub：让主模型确定性失败，验证切备用这条边走的是真实端点。 */
    private static final class UnauthorizedStub implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile boolean running = true;

        UnauthorizedStub() throws IOException {
            server = new ServerSocket(0);
            worker = new Thread(this::serve, "e2e-401-stub");
            worker.setDaemon(true);
            worker.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/v1";
        }

        private void serve() {
            while (running) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    InputStream in = socket.getInputStream();
                    ByteArrayOutputStream head = new ByteArrayOutputStream();
                    int state = 0;
                    while (state < 4) {
                        int c = in.read();
                        if (c == -1) break;
                        head.write(c);
                        if (state == 0 && c == '\r') state = 1;
                        else if (state == 1 && c == '\n') state = 2;
                        else if (state == 2 && c == '\r') state = 3;
                        else if (state == 3 && c == '\n') state = 4;
                        else state = 0;
                    }
                    byte[] payload = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 401 Unauthorized\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(payload);
                    socket.getOutputStream().flush();
                } catch (Exception ignored) {
                    // one failed exchange must not kill the stub
                }
            }
        }

        @Override
        public void close() {
            running = false;
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }
}
