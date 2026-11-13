package cn.aiditto.onboarding;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes answers; sensitive questions go through AES-GCM (技术方案 §4.4). */
@Component
public class AnswerStore {
    private final AnswerMapper answers;
    private final QuestionCatalog catalog;
    private final Crypto crypto;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public AnswerStore(AnswerMapper answers, QuestionCatalog catalog, Crypto crypto, Clock clock, JdbcTemplate jdbc) {
        this.answers = answers;
        this.catalog = catalog;
        this.crypto = crypto;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public Map<String, Object> load(long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Answer a : answers.selectList(new QueryWrapper<Answer>().eq("user_id", userId).orderByAsc("question_id"))) {
            String json = a.valueEnc != null ? crypto.decrypt(a.valueEnc) : a.valueJson;
            out.put(a.questionId, Json.readPlain(json));
        }
        return out;
    }

    public void put(long userId, String questionId, Object value) {
        String json = Json.write(value);
        boolean secret = catalog.isSensitive(questionId);
        answers.upsert(userId, questionId, secret ? null : json, secret ? crypto.encrypt(json) : null, Times.millis(clock.instant()));
    }

    /** One round trip for a whole questionnaire. Same encryption rules as {@link #put}. */
    public void putAll(long userId, Map<String, Object> values) {
        if (values.isEmpty()) return;
        Instant at = Times.millis(clock.instant());
        StringBuilder sql = new StringBuilder("""
                INSERT INTO onboarding_answer (user_id, question_id, value_json, value_enc, updated_at) VALUES 
                """);
        List<Object> args = new ArrayList<>(values.size() * 5);
        int n = 0;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (n++ > 0) sql.append(',');
            sql.append("(?,?,CAST(NULLIF(?, '') AS JSON),?,?)");
            String json = Json.write(e.getValue());
            boolean secret = catalog.isSensitive(e.getKey());
            args.add(userId);
            args.add(e.getKey());
            args.add(secret ? "" : json);
            args.add(secret ? crypto.encrypt(json) : null);
            args.add(Timestamp.from(at));
        }
        sql.append(" AS new ON DUPLICATE KEY UPDATE value_json = new.value_json, value_enc = new.value_enc, updated_at = new.updated_at");
        jdbc.update(sql.toString(), args.toArray());
    }

    public void delete(long userId, Collection<String> questionIds) {
        if (questionIds.isEmpty()) return;
        answers.delete(new QueryWrapper<Answer>().eq("user_id", userId).in("question_id", questionIds));
    }

    public void deleteAll(long userId) {
        answers.delete(new QueryWrapper<Answer>().eq("user_id", userId));
    }
}
