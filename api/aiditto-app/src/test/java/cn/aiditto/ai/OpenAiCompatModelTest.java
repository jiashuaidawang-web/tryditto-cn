package cn.aiditto.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 HTTP 路径的 8 类故障注入。
 * 用 JDK ServerSocket 做本地 stub（等价 WireMock 的场景覆盖，零新增依赖、可离线跑）。
 */
class OpenAiCompatModelTest {

    private static final String OK_BODY = """
            {"choices":[{"message":{"content":"{\\"headline\\":\\"hi\\"}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":12,"completion_tokens":7}}
            """;

    private Stub stub;
    private OpenAiCompatModel model;

    @BeforeEach
    void setUp() throws IOException {
        stub = new Stub();
        model = new OpenAiCompatModel(stub.baseUrl(), "test-key", "stub-model", 800L);
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static LlmRequest req(boolean jsonMode, boolean disableThinking) {
        return new LlmRequest("unit_test", "v1", "sys", "usr", 1024, 0.2, jsonMode, disableThinking);
    }

    @Test
    void normalResponseParsesContentAndTokens() {
        stub.enqueue(new Reply(200, OK_BODY, 0, null));
        LlmModel.Raw raw = model.call(req(true, true));
        assertThat(raw.content()).contains("headline");
        assertThat(raw.inputTokens()).isEqualTo(12);
        assertThat(raw.outputTokens()).isEqualTo(7);
        assertThat(raw.finishReason()).isEqualTo("stop");
        // 白名单之外不发送；jsonMode / thinking 必须进请求体
        assertThat(stub.lastBody).contains("response_format").contains("thinking");
        assertThat(stub.lastBody).doesNotContain("test-key");
    }

    @Test
    void http429MapsToRateLimitWithRetryAfter() {
        stub.enqueue(new Reply(429, "{\"error\":\"rate\"}", 0, "0"));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> {
                    LlmException le = (LlmException) e;
                    assertThat(le.kind()).isEqualTo(LlmException.Kind.RATE_LIMIT);
                    assertThat(le.retryAfter()).isEqualTo("0");
                });
    }

    @Test
    void http500MapsToServerError() {
        stub.enqueue(new Reply(503, "{}", 0, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.SERVER_ERROR));
    }

    @Test
    void http401MapsToAuthAndSwitchesImmediately() {
        stub.enqueue(new Reply(401, "{}", 0, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> {
                    assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.AUTH);
                    assertThat(((LlmException) e).switchProviderImmediately()).isTrue();
                });
    }

    @Test
    void http400MapsToAuth() {
        stub.enqueue(new Reply(400, "{}", 0, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.AUTH));
    }

    @Test
    void emptyContentWithFinishReasonLengthMapsToEmptyContent() {
        stub.enqueue(new Reply(200, "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}", 0, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.EMPTY_CONTENT));
    }

    @Test
    void malformedJsonMapsToBadResponse() {
        stub.enqueue(new Reply(200, "not-json", 0, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.BAD_RESPONSE));
    }

    @Test
    void slowResponseMapsToTimeout() {
        stub.enqueue(new Reply(200, OK_BODY, 1500, null));
        assertThatThrownBy(() -> model.call(req(true, false)))
                .isInstanceOf(LlmException.class)
                .satisfies(e -> assertThat(((LlmException) e).kind()).isEqualTo(LlmException.Kind.TIMEOUT));
    }

    @Test
    void fencedJsonIsReturnedVerbatimForUpperLayerParser() {
        stub.enqueue(new Reply(200,
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\\\\\"a\\\\\\\":1}\\n```\"},\"finish_reason\":\"stop\"}]}", 0, null));
        assertThat(model.call(req(true, false)).content()).contains("```json");
    }

    @Test
    void modelLevelExtraBodyIsAppliedEvenWhenRequestDoesNotDisableThinking() {
        OpenAiCompatModel deepSeek = new OpenAiCompatModel(stub.baseUrl(), "test-key", "deepseek-flash", 800L,
                java.util.Map.of("thinking", java.util.Map.of("type", "disabled")));
        stub.enqueue(new Reply(200, OK_BODY, 0, null));
        deepSeek.call(req(true, false)); // 请求级 disableThinking=false，模型级参数必须兜底
        assertThat(stub.lastBody).contains("\"thinking\"").contains("disabled");
        assertThat(stub.lastBody).doesNotContain("test-key");
    }

    private record Reply(int status, String body, long delayMs, String retryAfter) {}

    /** 极简 HTTP/1.1 stub：按队列返回预设响应，记录最后一次请求体。 */
    private static final class Stub implements AutoCloseable {
        private static final Pattern LENGTH = Pattern.compile("(?im)^Content-Length:\\s*(\\d+)");
        private final ServerSocket server;
        private final Thread worker;
        private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
        private volatile boolean running = true;
        private volatile String lastBody = "";

        Stub() throws IOException {
            server = new ServerSocket(0);
            worker = new Thread(this::serve, "llm-stub");
            worker.setDaemon(true);
            worker.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/v1";
        }

        void enqueue(Reply... rs) {
            for (Reply r : rs) replies.add(r);
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
                    Matcher m = LENGTH.matcher(head.toString(StandardCharsets.UTF_8));
                    int bodyLen = m.find() ? Integer.parseInt(m.group(1)) : 0;
                    byte[] body = bodyLen > 0 ? in.readNBytes(bodyLen) : new byte[0];
                    lastBody = new String(body, StandardCharsets.UTF_8);

                    Reply r = replies.poll();
                    if (r == null) r = new Reply(200, OK_BODY, 0, null);
                    if (r.delayMs() > 0) Thread.sleep(r.delayMs());
                    byte[] payload = r.body().getBytes(StandardCharsets.UTF_8);
                    String reason = switch (r.status()) {
                        case 200 -> "OK";
                        case 400 -> "Bad Request";
                        case 401 -> "Unauthorized";
                        case 429 -> "Too Many Requests";
                        default -> "Server Error";
                    };
                    String headers = "HTTP/1.1 " + r.status() + " " + reason + "\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + payload.length + "\r\n"
                            + (r.retryAfter() == null ? "" : "Retry-After: " + r.retryAfter() + "\r\n")
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
