package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.ProfileContextBuilder;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Inputs;
import cn.aiditto.domain.relationship.ProfileContextBuilder.ScenarioChoice;
import cn.aiditto.domain.relationship.ProfileContextBuilder.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads stored inputs for one user. Scenario rows contribute the chosen key only.
 * Encrypted custom text and the scenario prompt are not read.
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
                       boolean modelExportGranted, List<Statement> statements) {
        List<ScenarioChoice> scenarios = new ArrayList<>();
        jdbc.query("SELECT chosen_key FROM voice_answer WHERE user_id = ? AND chosen_key IS NOT NULL",
                rs -> {
                    scenarios.add(new ScenarioChoice(rs.getString("chosen_key")));
                }, userId);
        return new Inputs(userId, sourceProfileVersion, generationEpoch, processingGranted, modelExportGranted,
                answers.loadAnswers(userId), statements, scenarios);
    }
}
