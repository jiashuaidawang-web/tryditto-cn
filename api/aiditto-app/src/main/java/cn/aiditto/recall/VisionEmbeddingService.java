package cn.aiditto.recall;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 照片 → 审美向量（tryDitto 明星脸审美的核心能力，创始人 2026-11-18 拍板放开 §8.1 后启用）。
 *
 * 与文本 embedding 分开：走视觉模型（doubao-embedding-vision-251215），图片 base64 直出 2048 维。
 * 实测（2026-11-18）：同图余弦 0.9996、红vs蓝 0.7216，能稳定区分长相/风格。
 *
 * 合规边界：
 *  - 只发国内网关（baseUrl 由运维配置），课程照片不展示、不进文本提示词、不落明文日志；
 *  - 双侧用途：需求侧 = 用户的 ideal（明星/vibe）照片；供给侧 = 用户本人的 profile 照片。
 *  - 失败直接抛，由调用方决定降级（无审美向量时召回照常跑），绝不吞异常伪装成功。
 */
public final class VisionEmbeddingService {

    public static final int DIMENSION = 2048;
    public static final String DEFAULT_VERSION = "doubao-embedding-vision-251215-d2048";

    private final String version;
    private final String url;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final long timeoutMs;

    public VisionEmbeddingService(String version, String baseUrl, String apiKey, String model, long timeoutMs) {
        this.version = version == null || version.isBlank() ? DEFAULT_VERSION : version;
        this.url = normalizeUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? "doubao-embedding-vision-251215" : model;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .build();
    }

    public String version() {
        return version;
    }

    public int dimension() {
        return DIMENSION;
    }

    /** 编码一张图片（data URL 或裸 base64）为 L2 归一化向量。 */
    public float[] embedBase64(String base64) {
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("empty image");
        String dataUrl = base64.startsWith("data:") ? base64 : "data:image/jpeg;base64," + base64;
        String body = Json.write(java.util.Map.of(
                "model", model,
                "input", List.of(java.util.Map.of(
                        "type", "image_url",
                        "image_url", java.util.Map.of("url", dataUrl)))));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException("vision embedding http " + resp.statusCode()
                        + ": " + clip(resp.body()));
            }
            JsonNode root = Json.readTree(resp.body());
            JsonNode arr = root.path("data").path(0).path("embedding");
            if (!arr.isArray() || arr.size() != DIMENSION) {
                throw new IllegalStateException("vision embedding dim mismatch: expected "
                        + DIMENSION + ", got " + (arr.isArray() ? arr.size() : "n/a"));
            }
            float[] v = new float[DIMENSION];
            double norm = 0.0;
            for (int i = 0; i < DIMENSION; i++) {
                v[i] = (float) arr.get(i).asDouble();
                norm += v[i] * v[i];
            }
            double sqrt = Math.sqrt(norm);
            if (sqrt > 0) for (int i = 0; i < DIMENSION; i++) v[i] = (float) (v[i] / sqrt);
            return v;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("vision embedding io: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("vision embedding interrupted", e);
        }
    }

    /** 把 baseUrl 收敛到 /v1/embeddings。 */
    static String normalizeUrl(String base) {
        String s = base == null ? "" : base.trim().replaceAll("/+$", "");
        if (s.endsWith("/embeddings")) return s;
        if (s.endsWith("/v1")) return s + "/embeddings";
        return s + "/v1/embeddings";
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
