package cn.aiditto.onboarding;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.questionnaire.QuestionSpec;
import cn.aiditto.domain.questionnaire.QuestionType;
import cn.aiditto.infra.json.Json;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Loads questions.json, which web/scripts/export-questions.mjs generates from the frontend question bank. */
@Configuration
public class CatalogConfig {

    @Bean
    QuestionCatalog questionCatalog() throws IOException {
        try (InputStream in = new ClassPathResource("questions.json").getInputStream()) {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static QuestionCatalog parse(String json) {
        List<QuestionSpec> specs = new ArrayList<>();
        for (JsonNode q : Json.readTree(json).get("questions")) {
            specs.add(new QuestionSpec(
                    q.get("id").asString(),
                    QuestionType.fromWire(q.get("type").asString()),
                    strings(q.get("options")),
                    q.has("maxSelect") ? q.get("maxSelect").asInt() : null,
                    q.has("maxLength") ? q.get("maxLength").asInt() : null,
                    strings(q.get("exclusive")),
                    q.path("optional").asBoolean(false),
                    q.has("emailKind") ? q.get("emailKind").asString() : null,
                    q.path("allowCustom").asBoolean(false),
                    q.path("private").asBoolean(false),
                    q.has("hobby") ? q.get("hobby").asString() : null,
                    strings(q.get("drop"))));
        }
        return new QuestionCatalog(specs);
    }

    private static Set<String> strings(JsonNode arr) {
        Set<String> out = new LinkedHashSet<>();
        if (arr != null) arr.forEach(n -> out.add(n.asString()));
        return out;
    }
}
