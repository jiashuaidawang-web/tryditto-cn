package cn.aiditto.recall;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用本地 HTTP stub 验证视觉审美编码：URL 归一化、请求格式、L2 归一化、维度校验、错误透传。 */
class VisionEmbeddingServiceTest {

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

    private static String vec(int n, double v) {
        StringBuilder sb = new StringBuilder("{\"data\":[{\"index\":0,\"embedding\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(v);
        }
        sb.append("]}],\"model\":\"m\",\"usage\":{}}");
        return sb.toString();
    }

    @Test
    void urlNormalizationCoversGatewayForms() {
        assertThat(VisionEmbeddingService.normalizeUrl("https://api.vveai.com"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(VisionEmbeddingService.normalizeUrl("https://api.vveai.com/v1"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
        assertThat(VisionEmbeddingService.normalizeUrl("https://api.vveai.com/v1/embeddings"))
                .isEqualTo("https://api.vveai.com/v1/embeddings");
    }

    @Test
    void encodesImageAndL2Normalizes() {
        response = vec(VisionEmbeddingService.DIMENSION, 3.0);
        VisionEmbeddingService svc = new VisionEmbeddingService(null, url(), "k", null, 5000);
        float[] v = svc.embedBase64("data:image/jpeg;base64,AAAA");
        assertThat(v).hasSize(2048);
        double norm = 0;
        for (float x : v) norm += x * x;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(lastPath.get()).isEqualTo("/v1/embeddings");
        assertThat(lastBody.get()).contains("image_url").contains("doubao-embedding-vision-251215");
    }

    @Test
    void bareBase64GetsDataUrlPrefix() {
        response = vec(VisionEmbeddingService.DIMENSION, 1.0);
        VisionEmbeddingService svc = new VisionEmbeddingService(null, url(), "k", null, 5000);
        svc.embedBase64("AAAA");
        assertThat(lastBody.get()).contains("data:image/jpeg;base64,AAAA");
    }

    @Test
    void dimensionMismatchThrows() {
        response = vec(10, 1.0); // 维度不对
        VisionEmbeddingService svc = new VisionEmbeddingService(null, url(), "k", null, 5000);
        assertThatThrownBy(() -> svc.embedBase64("AAAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dim mismatch");
    }

    @Test
    void httpErrorPropagates() {
        status = 400;
        response = "{\"error\":\"bad image\"}";
        VisionEmbeddingService svc = new VisionEmbeddingService(null, url(), "k", null, 5000);
        assertThatThrownBy(() -> svc.embedBase64("AAAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http 400");
    }
}
