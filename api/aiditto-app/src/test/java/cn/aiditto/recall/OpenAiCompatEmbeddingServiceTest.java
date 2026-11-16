package cn.aiditto.recall;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用本地 HTTP stub 验证真实 embedding 网关的兼容细节，不依赖任何外部 key/网络。 */
class OpenAiCompatEmbeddingServiceTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final AtomicReference<String> lastPath = new AtomicReference<>("");
    private volatile int status = 200;
    private volatile String response = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().getPath());
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String vec(int n) {
        StringBuilder sb = new StringBuilder("{\"data\":[{\"index\":0,\"embedding\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(i == 0 ? "0.6" : "0.8");
        }
        sb.append("]}],\"model\":\"m\",\"usage\":{}}");
        return sb.toString();
    }

    @Test
    void urlNormalizationCoversAllCommonGatewayForms() {
        assertThat(OpenAiCompatEmbeddingService.normalizeUrl("https://api.vveai.com"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(OpenAiCompatEmbeddingService.normalizeUrl("https://api.vveai.com/"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(OpenAiCompatEmbeddingService.normalizeUrl("https://api.vveai.com/v1"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(OpenAiCompatEmbeddingService.normalizeUrl("https://api.vveai.com/v1/embeddings"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(OpenAiCompatEmbeddingService.normalizeUrl("http://127.0.0.1:8080/openai/v1"))
                .isEqualTo("http://127.0.0.1:8080/openai/v1/embeddings");
    }

    @Test
    void requestHitsV1PathAndIsL2Normalized() {
        response = vec(4);
        OpenAiCompatEmbeddingService svc = new OpenAiCompatEmbeddingService(
                "m-d4", url(), "k", "m", 4, true, 5000);
        float[] v = svc.embed("你好");
        assertThat(lastPath.get()).endsWith("/v1/embeddings");
        assertThat(lastBody.get()).contains("\"dimensions\":4", "\"model\":\"m\"");
        assertThat(v).hasSize(4);
        double norm2 = 0;
        for (float x : v) norm2 += x * x;
        assertThat(norm2).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void sendDimensionsFalseOmitsParameter() {
        response = vec(8);
        OpenAiCompatEmbeddingService svc = new OpenAiCompatEmbeddingService(
                "m-d8", url(), "k", "m", 8, false, 5000);
        svc.embed("x");
        assertThat(lastBody.get()).doesNotContain("dimensions");
    }

    @Test
    void dimensionMismatchGivesActionableMessage() {
        response = vec(768);
        OpenAiCompatEmbeddingService svc = new OpenAiCompatEmbeddingService(
                "m-d1024", url(), "k", "m", 1024, true, 5000);
        assertThatThrownBy(() -> svc.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 1024")
                .hasMessageContaining("returned 768")
                .hasMessageContaining("EMBEDDING_DIM=768");
    }

    @Test
    void expiredTokenErrorIsVisibleNotSwallowed() {
        status = 401;
        response = "{\"error\":{\"message\":\"This token has expired\",\"type\":\"authentication_error\"}}";
        OpenAiCompatEmbeddingService svc = new OpenAiCompatEmbeddingService(
                "m-d4", url(), "expired-key", "m", 4, true, 5000);
        assertThatThrownBy(() -> svc.embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("This token has expired");
    }

    @Test
    void batchKeepsIndexOrderAndNormalizes() {
        response = "{\"data\":["
                + "{\"index\":1,\"embedding\":[0,1,0,0]},"
                + "{\"index\":0,\"embedding\":[1,0,0,0]}"
                + "],\"model\":\"m\"}";
        OpenAiCompatEmbeddingService svc = new OpenAiCompatEmbeddingService(
                "m-d4", url(), "k", "m", 4, true, 5000);
        List<float[]> out = svc.embedBatch(List.of("first", "second"));
        assertThat(out).hasSize(2);
        assertThat(out.get(0)[0]).isEqualTo(1f);   // index 0 归位，未被乱序覆盖
        assertThat(out.get(1)[1]).isEqualTo(1f);
    }
}
