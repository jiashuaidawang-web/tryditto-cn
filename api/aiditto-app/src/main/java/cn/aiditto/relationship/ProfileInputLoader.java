package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.ProfileContextBuilder.Inputs;
import cn.aiditto.domain.relationship.ProfileContextBuilder.ScenarioChoice;
import cn.aiditto.infra.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads stored inputs for one user. A scenario is usable only when the chosen option's stored
 * text is exactly {@code low}, {@code steady}, or {@code high}. The prompt, custom text, trait,
 * and another user's rows are not read into the model document.
 */
@Component
public class ProfileInputLoader {
    private final JdbcTemplate jdbc;
    private final AnswerEvidenceDirectory answers;

    public ProfileInputLoader(JdbcTemplate jdbc, AnswerEvidenceDirectory answers) {
        this.jdbc = jdbc;
        this.answers = answers;
    }

    public Inputs load(long userId, int sourceProfileVersion, long generationEpoch, boolean processingGranted,
                       boolean modelExportGranted, long modelExportEpoch) {
        List<ScenarioChoice> scenarios = new ArrayList<>();
        jdbc.query("""
                SELECT a.id AS answer_id, a.chosen_key, q.id AS question_id, q.prompt_version, q.options_json
                FROM voice_answer a
                JOIN voice_question q ON q.id = a.question_id
                WHERE a.user_id = ? AND a.chosen_key IS NOT NULL
                """, rs -> {
            String meaning = approvedMeaning(rs.getString("options_json"), rs.getString("chosen_key"));
            if (meaning == null) return;
            scenarios.add(new ScenarioChoice(Long.toString(rs.getLong("question_id")), rs.getString("prompt_version"),
                    rs.getString("chosen_key"), meaning, Long.toString(rs.getLong("answer_id"))));
        }, userId);
        return new Inputs(userId, sourceProfileVersion, generationEpoch, processingGranted, modelExportGranted,
                answers.loadAnswers(userId), List.of(), scenarios, modelExportEpoch);
    }

    private static String approvedMeaning(String optionsJson, String choiceKey) {
        if (choiceKey == null || !choiceKey.matches("^[A-D]$")) return null;
        List<Map<String, Object>> options;
        try {
            options = Json.readList(optionsJson);
        } catch (RuntimeException ignored) {
            return null;
        }
        for (Map<String, Object> option : options) {
            if (!choiceKey.equals(String.valueOf(option.get("key")))) continue;
            String text = option.get("text") == null ? "" : String.valueOf(option.get("text")).strip();
            if ("low".equals(text) || "steady".equals(text) || "high".equals(text)) return text;
            return null;
        }
        return null;
    }
}
