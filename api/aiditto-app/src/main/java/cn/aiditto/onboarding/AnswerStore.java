package cn.aiditto.onboarding;

import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes answers; sensitive questions go through AES-GCM (技术方案 §4.4). */
@Component
public class AnswerStore {
    private final AnswerMapper answers;
    private final QuestionCatalog catalog;
    private final Crypto crypto;
    private final Clock clock;

    public AnswerStore(AnswerMapper answers, QuestionCatalog catalog, Crypto crypto, Clock clock) {
        this.answers = answers;
        this.catalog = catalog;
        this.crypto = crypto;
        this.clock = clock;
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

    public void delete(long userId, Collection<String> questionIds) {
        if (questionIds.isEmpty()) return;
        answers.delete(new QueryWrapper<Answer>().eq("user_id", userId).in("question_id", questionIds));
    }

    public void deleteAll(long userId) {
        answers.delete(new QueryWrapper<Answer>().eq("user_id", userId));
    }
}
