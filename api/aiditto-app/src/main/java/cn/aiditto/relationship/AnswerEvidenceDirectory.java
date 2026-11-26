package cn.aiditto.relationship;

import cn.aiditto.domain.relationship.EvidenceIds;
import cn.aiditto.domain.relationship.EvidenceSourceDirectory;
import cn.aiditto.domain.relationship.OrdinarySnapshot;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.infra.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resolves questionnaire rows. It does not trust a type or id supplied beside the row. */
@Component
public class AnswerEvidenceDirectory implements EvidenceSourceDirectory {
    private final JdbcTemplate jdbc;

    public AnswerEvidenceDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> loadAnswers(long userId) {
        Map<String, Object> answers = new LinkedHashMap<>();
        jdbc.query("""
                SELECT question_id, value_json::text AS value_json, value_enc IS NOT NULL AS sealed
                FROM onboarding_answer WHERE user_id = ?
                """, rs -> {
            String questionId = rs.getString("question_id");
            if (!RelationshipProfileProjector.STORED_QUESTIONS.contains(questionId)) return;
            if (rs.getString("value_json") != null) {
                answers.put(questionId, Json.readPlain(rs.getString("value_json")));
            } else if (rs.getBoolean("sealed") && "expectedGender".equals(questionId)) {
                answers.put(questionId, List.of("__sealed__"));
            } else if (rs.getBoolean("sealed")) {
                answers.put(questionId, Boolean.TRUE);
            }
        }, userId);
        return answers;
    }

    @Override
    public Optional<SourceFact> find(long userId, String sourceRef, String sourceVersion) {
        if (!EvidenceIds.CATALOG.equals(sourceVersion) || !OrdinarySnapshot.safeToken(sourceRef)) return Optional.empty();
        Number count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM onboarding_answer
                WHERE user_id = ? AND question_id = ? AND (value_json IS NOT NULL OR value_enc IS NOT NULL)
                """, Number.class, userId, sourceRef);
        if (count == null || count.intValue() == 0) return Optional.empty();
        return Optional.of(new SourceFact(userId, sourceRef, sourceVersion, EvidenceSource.DIRECT_ANSWER,
                OrdinarySnapshot.PROFILE_SCOPE, false, false));
    }
}
