package cn.aiditto.infra.json;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** The one place that touches Jackson for storage-side JSON (技术方案 §3.3: keep a Boot 3.5 fallback cheap). */
public final class Json {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** JSON text → String / Boolean / Number / List / Map. */
    public static Object readPlain(String json) {
        return json == null ? null : MAPPER.readValue(json, Object.class);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static List<String> readStringList(String json) {
        return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<List<String>>() {});
    }

    /** JSON array → List&lt;Map&gt;（对象数组，如情景题选项）。 */
    public static List<Map<String, Object>> readList(String json) {
        return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    public static Map<String, Object> readMap(String json) {
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /** A request-body node → plain Java value; JSON null and a missing node both become null. */
    public static Object toPlain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return MAPPER.treeToValue(node, Object.class);
    }

    public static JsonNode readTree(String json) {
        return MAPPER.readTree(json);
    }
}
