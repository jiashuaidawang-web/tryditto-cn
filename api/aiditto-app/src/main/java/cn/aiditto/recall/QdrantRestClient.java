package cn.aiditto.recall;

import cn.aiditto.infra.json.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 1.12.x REST 客户端（JDK HttpClient 自实现，与 LlmGateway 同样不引第三方 starter）。
 * named vectors + filterable HNSW：所有硬约束下沉到服务端 filter，不是召回后再过滤。
 */
public final class QdrantRestClient {

    /** scroll 出来的点，供 FLAT 精确对照做 ground truth。 */
    public record VectorPoint(long id, float[] taste, float[] persona, Map<String, Object> payload) {}

    private final String baseUrl;
    private final HttpClient http;
    private final long timeoutMs;

    public QdrantRestClient(String baseUrl, long timeoutMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000L)))
                .build();
    }

    public boolean collectionExists(String collection) {
        try {
            JsonNode r = request("GET", "/collections/" + collection, null);
            return r.has("result");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 建库：taste / persona 两个 named vector，HNSW m=32 / ef_construct=200。 */
    public void createCollection(String collection, int dimension) {
        createCollection(collection, dimension, 10000);
    }

    /** fullScanThreshold 传 1 可强制走 HNSW 索引，用于分桶 ANN 评测（避免小数据集自动精确扫描掩盖问题）。 */
    public void createCollection(String collection, int dimension, int fullScanThreshold) {
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("taste", Map.of("size", dimension, "distance", "Cosine"));
        vectors.put("persona", Map.of("size", dimension, "distance", "Cosine"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", vectors);
        body.put("hnsw_config", Map.of("m", 32, "ef_construct", 200,
                "full_scan_threshold", fullScanThreshold, "on_disk_payload", true));
        body.put("optimizers_config", Map.of("indexing_threshold", 10000));
        request("PUT", "/collections/" + collection, Json.write(body));
    }

    /** 过滤列必须建 payload index，否则 Qdrant 退化为全量扫描（测试集小看不出来）。 */
    public void createPayloadIndexes(String collection) {
        Map<String, String> indexes = new LinkedHashMap<>();
        indexes.put("user_id", "integer");
        indexes.put("school_code", "keyword");
        indexes.put("gender", "keyword");
        indexes.put("expected_gender", "keyword");
        indexes.put("age", "integer");
        indexes.put("height_cm", "integer");
        indexes.put("iso_cells", "keyword");
        indexes.put("interest_tags", "keyword");
        indexes.put("value_tags", "keyword");
        indexes.put("embedding_version", "keyword");
        indexes.put("bucket", "keyword");
        indexes.put("is_active", "bool");
        indexes.put("verified", "bool");
        indexes.put("photo_approved", "bool");
        indexes.put("is_synthetic", "bool");
        for (Map.Entry<String, String> e : indexes.entrySet()) {
            try {
                request("PUT", "/collections/" + collection + "/index?wait=true",
                        Json.write(Map.of("field_name", e.getKey(), "field_schema", e.getValue())));
            } catch (RuntimeException ignored) {
                // 已存在或服务版本不支持该字段类型时忽略，不影响主流程
            }
        }
    }

    public void upsert(String collection, List<RecallModel.Point> points, int batchSize) {
        for (int from = 0; from < points.size(); from += batchSize) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (RecallModel.Point p : points.subList(from, Math.min(points.size(), from + batchSize))) {
                Map<String, Object> vector = new LinkedHashMap<>();
                vector.put("taste", toList(p.taste()));
                vector.put("persona", toList(p.persona()));
                batch.add(Map.of("id", p.userId(), "vector", vector, "payload", p.payload()));
            }
            request("PUT", "/collections/" + collection + "/points?wait=true",
                    Json.write(Map.of("points", batch)));
        }
    }

    /** 单路 ANN 检索。hnswEf 必须 >= limit，否则会静默少结果。 */
    public List<RecallModel.Hit> search(String collection, String vectorName, float[] vector,
                                        RecallModel.Filter filter, int limit, int hnswEf,
                                        RecallModel.Source source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", Map.of("name", vectorName, "vector", toList(vector)));
        body.put("filter", toQdrantFilter(filter));
        body.put("limit", limit);
        body.put("params", Map.of("hnsw_ef", Math.max(hnswEf, limit)));
        body.put("with_payload", false);
        body.put("with_vector", false);
        JsonNode result = request("POST", "/collections/" + collection + "/points/search", Json.write(body))
                .get("result");
        List<RecallModel.Hit> hits = new ArrayList<>();
        if (result != null) {
            for (JsonNode n : result) {
                hits.add(new RecallModel.Hit(n.get("id").asLong(), n.path("score").asDouble(), source));
            }
        }
        return hits;
    }

    /** 按 payload 过滤后 scroll 出全部向量，用于 FLAT 精确对照。 */
    public List<VectorPoint> scroll(String collection, RecallModel.Filter filter, int pageSize) {
        List<VectorPoint> out = new ArrayList<>();
        Object offset = null;
        while (true) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", toQdrantFilter(filter));
            body.put("limit", pageSize);
            body.put("with_payload", true);
            body.put("with_vector", true);
            if (offset != null) body.put("offset", offset);
            JsonNode result = request("POST", "/collections/" + collection + "/points/scroll", Json.write(body))
                    .get("result");
            if (result == null) break;
            JsonNode points = result.get("points");
            if (points == null || points.isEmpty()) break;
            for (JsonNode p : points) {
                JsonNode vec = p.get("vector");
                if (vec == null) continue;
                out.add(new VectorPoint(p.get("id").asLong(),
                        floats(vec.get("taste")), floats(vec.get("persona")),
                        payload(p.get("payload"))));
            }
            JsonNode next = result.get("next_page_offset");
            if (next == null || next.isNull()) break;
            offset = Json.toPlain(next);
        }
        return out;
    }

    /** 轻量计数，供验收与监控使用（避免 scroll 拉全量向量）。 */
    public long count(String collection, RecallModel.Filter filter) {
        JsonNode result = request("POST", "/collections/" + collection + "/points/count",
                Json.write(Map.of("filter", toQdrantFilter(filter), "exact", true))).get("result");
        JsonNode c = result == null ? null : result.get("count");
        return c != null && c.isNumber() ? c.asLong() : 0L;
    }

    /** 注销 / 拉黑：向量库必须真删，不能只靠 filter（数据最小化）。 */
    public void delete(String collection, List<Long> ids) {
        if (ids.isEmpty()) return;
        request("POST", "/collections/" + collection + "/points/delete?wait=true",
                Json.write(Map.of("points", ids)));
    }

    public void createV4Collection(String collection, int dimension) {
        cn.aiditto.domain.recall.v4.V4IndexNames.refuseUnlessIsolated(collection);
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("self", Map.of("size", dimension, "distance", "Cosine"));
        vectors.put("preference", Map.of("size", dimension, "distance", "Cosine"));
        request("PUT", "/collections/" + collection, Json.write(Map.of("vectors", vectors)));
        for (String field : new String[] {"visible", "authorized", "withdrawn"}) {
            try {
                request("PUT", "/collections/" + collection + "/index?wait=true",
                        Json.write(Map.of("field_name", field, "field_schema", "bool")));
            } catch (RuntimeException ignored) {
                // A small isolated collection can still filter without the index.
            }
        }
        try {
            request("PUT", "/collections/" + collection + "/index?wait=true",
                    Json.write(Map.of("field_name", "model_version", "field_schema", "keyword")));
        } catch (RuntimeException ignored) {
            // Same as above.
        }
    }

    public void upsertV4(String collection, long id, float[] self, float[] preference, Map<String, Object> payload) {
        cn.aiditto.domain.recall.v4.V4IndexNames.refuseUnlessIsolated(collection);
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", id);
        point.put("vector", Map.of("self", toList(self), "preference", toList(preference)));
        point.put("payload", payload);
        request("PUT", "/collections/" + collection + "/points?wait=true", Json.write(Map.of("points", List.of(point))));
    }

    public JsonNode retrieveV4(String collection, long id) {
        cn.aiditto.domain.recall.v4.V4IndexNames.refuseUnlessIsolated(collection);
        JsonNode result = request("POST", "/collections/" + collection + "/points",
                Json.write(Map.of("ids", List.of(id), "with_payload", true, "with_vector", false))).get("result");
        if (result == null || !result.isArray() || result.isEmpty()) return null;
        return result.get(0);
    }

    public List<Long> searchV4(String collection, String vectorName, float[] vector, String modelVersion, int limit) {
        cn.aiditto.domain.recall.v4.V4IndexNames.refuseUnlessIsolated(collection);
        Map<String, Object> filter = Map.of("must", List.of(
                Map.of("key", "visible", "match", Map.of("value", true)),
                Map.of("key", "authorized", "match", Map.of("value", true)),
                Map.of("key", "withdrawn", "match", Map.of("value", false)),
                Map.of("key", "model_version", "match", Map.of("value", modelVersion))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", Map.of("name", vectorName, "vector", toList(vector)));
        body.put("filter", filter);
        body.put("limit", limit);
        body.put("with_payload", true);
        JsonNode result = request("POST", "/collections/" + collection + "/points/search", Json.write(body)).get("result");
        List<Long> hits = new ArrayList<>();
        if (result != null) {
            for (JsonNode node : result) hits.add(node.get("id").asLong());
        }
        return hits;
    }

    public void deleteCollection(String collection) {
        request("DELETE", "/collections/" + collection, null);
    }

    public Map<String, Object> toQdrantFilter(RecallModel.Filter f) {
        List<Object> must = new ArrayList<>();
        must.add(Map.of("key", "embedding_version", "match", Map.of("value", f.embeddingVersion())));
        must.add(Map.of("key", "is_active", "match", Map.of("value", true)));
        must.add(Map.of("key", "verified", "match", Map.of("value", true)));
        must.add(Map.of("key", "photo_approved", "match", Map.of("value", true)));
        if (f.schoolCode() != null && !f.schoolCode().isBlank()) {
            must.add(Map.of("key", "school_code", "match", Map.of("value", f.schoolCode())));
        }
        if (!f.expectedGenders().isEmpty()) {
            must.add(Map.of("key", "gender", "match", Map.of("any", f.expectedGenders())));
        }
        if (f.selfGender() != null && !f.selfGender().isBlank()) {
            must.add(Map.of("key", "expected_gender", "match", Map.of("value", f.selfGender())));
        }
        if (f.ageMin() != null || f.ageMax() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (f.ageMin() != null) range.put("gte", f.ageMin());
            if (f.ageMax() != null) range.put("lte", f.ageMax());
            must.add(Map.of("key", "age", "range", range));
        }
        if (f.heightMin() != null || f.heightMax() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (f.heightMin() != null) range.put("gte", f.heightMin());
            if (f.heightMax() != null) range.put("lte", f.heightMax());
            must.add(Map.of("key", "height_cm", "range", range));
        }
        if (!f.isoCells().isEmpty()) {
            must.add(Map.of("key", "iso_cells", "match", Map.of("any", f.isoCells())));
        }
        if (f.selectivityBucket() != null && !f.selectivityBucket().isBlank()) {
            must.add(Map.of("key", "bucket", "match", Map.of("value", f.selectivityBucket())));
        }
        List<Object> mustNot = new ArrayList<>();
        List<Long> forbidden = new ArrayList<>(f.forbidden());
        forbidden.add(f.selfId());
        mustNot.add(Map.of("has_id", forbidden));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("must", must);
        out.put("must_not", mustNot);
        return out;
    }

    private JsonNode request(String method, String path, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json; charset=UTF-8");
        switch (method) {
            case "GET" -> b.GET();
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            case "DELETE" -> b.DELETE();
            default -> throw new IllegalArgumentException(method);
        }
        try {
            HttpResponse<String> r = http.send(b.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = r.body() == null || r.body().isBlank() ? null : Json.readTree(r.body());
            if (r.statusCode() < 200 || r.statusCode() >= 300 || root == null || !statusOk(root)) {
                throw new IllegalStateException("qdrant " + method + " " + path + " -> http " + r.statusCode()
                        + " body=" + (r.body() == null ? "" : r.body().substring(0, Math.min(200, r.body().length()))));
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("qdrant io: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("qdrant interrupted", e);
        }
    }

    private static boolean statusOk(JsonNode root) {
        JsonNode s = root.get("status");
        return s != null && s.isTextual() && "ok".equals(s.asString());
    }

    private static List<Float> toList(float[] v) {
        List<Float> out = new ArrayList<>(v.length);
        for (float x : v) out.add(x);
        return out;
    }

    private static float[] floats(JsonNode n) {
        if (n == null || !n.isArray()) return new float[0];
        float[] out = new float[n.size()];
        for (int i = 0; i < n.size(); i++) out[i] = (float) n.get(i).asDouble();
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(JsonNode n) {
        if (n == null || !n.isObject()) return Map.of();
        Object v = Json.toPlain(n);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
