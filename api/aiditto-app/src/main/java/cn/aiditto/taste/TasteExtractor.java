package cn.aiditto.taste;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.questionnaire.QuestionCatalog;
import cn.aiditto.domain.taste.TasteFacts;
import cn.aiditto.domain.taste.TasteWeights;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import cn.aiditto.onboarding.AnswerStore;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * answers + profile → 版本化 user_taste（Step 4 信息收集衔接）。
 *
 * 设计要点：
 * - 输入只经 TasteFacts 脱敏白名单，模型提示词零 PII（技术方案 §8.1）
 * - 模型调用在写事务之外，避免长事务；任何模型异常走确定性模板兜底
 * - 写入用 PG advisory xact lock 串行化同一用户，锁内复查 input_hash 保证幂等与并发安全
 * - 版本只追加不覆盖，旧版本 is_active=false；taste_log 留痕可回滚、可举证
 * - ensure() 永不把异常抛给入驻链路；失败用户由 TasteBatchService.refreshMissing 补齐
 */
@Service
public class TasteExtractor {

    public enum Outcome { CREATED, SKIPPED, FAILED }

    public record Result(Outcome outcome, int version, String reason) {
        static Result failed(String reason) {
            return new Result(Outcome.FAILED, 0, reason);
        }
    }

    private final AnswerStore answers;
    private final QuestionCatalog catalog;
    private final LlmGateway gateway;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final IdentifierGenerator ids;
    private final Clock clock;

    public TasteExtractor(AnswerStore answers, QuestionCatalog catalog, LlmGateway gateway,
                          JdbcTemplate jdbc, PlatformTransactionManager txm,
                          IdentifierGenerator ids, Clock clock) {
        this.answers = answers;
        this.catalog = catalog;
        this.gateway = gateway;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.ids = ids;
        this.clock = clock;
    }

    /** 幂等入口：答案未变直接复用现有 active 版本。 */
    public Result ensure(long userId, String trigger) {
        try {
            Map<String, Object> raw = answers.load(userId);
            if (raw.isEmpty()) return new Result(Outcome.SKIPPED, 0, "no_answers");
            TasteFacts facts = TasteFacts.from(userId, raw, TasteWriter.PROMPT_VERSION, catalog);
            LlmResult llm = safeCall(facts);
            TasteWriter.Draft draft = TasteWriter.choose(llm, facts);
            Result written = tx.execute(status -> write(userId, facts, draft, trigger));
            return written == null ? Result.failed("tx_null") : written;
        } catch (RuntimeException e) {
            return Result.failed(e.getClass().getSimpleName());
        }
    }

    private LlmResult safeCall(TasteFacts facts) {
        try {
            return gateway.call(TasteWriter.request(facts));
        } catch (RuntimeException e) {
            return null; // 模型异常一律模板兜底
        }
    }

    private Result write(long userId, TasteFacts facts, TasteWriter.Draft draft, String trigger) {
        advisoryLock(userId);
        List<Map<String, Object>> active = jdbc.queryForList(
                "SELECT version, input_hash, source_feedback_cnt, weights, learned_feedback_cnt FROM user_taste "
                        + "WHERE user_id = ? AND is_active", userId);

        if (!active.isEmpty() && facts.inputHash().equals(str(active.get(0).get("input_hash")))) {
            return new Result(Outcome.SKIPPED, intOf(active.get(0).get("version")), "unchanged");
        }

        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM user_taste WHERE user_id = ?",
                Integer.class, userId);
        int version = next == null ? 1 : next;
        int previous = active.isEmpty() ? 0 : intOf(active.get(0).get("version"));
        int feedback = active.isEmpty() ? 0 : intOf(active.get(0).get("source_feedback_cnt"));
        // Step 8：答案改写产生新版本时必须继承已学到的结构化权重，否则用户编辑一次问卷
        // 就会把 12 周期学到的审美清零。冷启动缺省为中性权重。
        String weights = active.isEmpty() ? cn.aiditto.domain.taste.TasteWeights.neutral().toJson()
                : normalizeWeights(str(active.get(0).get("weights")));
        int learned = active.isEmpty() ? 0 : intOf(active.get(0).get("learned_feedback_cnt"));
        Timestamp now = Timestamp.from(Times.millis(clock.instant()));

        if (!active.isEmpty()) {
            jdbc.update("UPDATE user_taste SET is_active = FALSE WHERE user_id = ? AND is_active", userId);
        }
        jdbc.update("""
                INSERT INTO user_taste
                  (user_id, version, persona_text, taste_text, embedding_id, embedding_version,
                   vector_sync_status, source_feedback_cnt, updated_reason, is_active, created_at,
                   input_hash, weights, learned_feedback_cnt)
                VALUES (?,?,?,?,NULL,?,'pending',?,?,TRUE,?,?,CAST(? AS JSONB),?)
                """, userId, version, draft.persona(), draft.taste(), TasteWriter.EMBEDDING_VERSION,
                feedback, reason(trigger, draft), now, facts.inputHash(), weights, learned);

        jdbc.update("""
                INSERT INTO taste_log
                  (id, user_id, from_version, to_version, trigger_match_id, trigger_feedback,
                   diff_text, changed_dim, created_at)
                VALUES (?,?,?,?,NULL,NULL,?,?,?)
                """, newId(), userId, previous == 0 ? null : previous, version,
                "persona_len=" + draft.persona().length() + ";taste_len=" + draft.taste().length()
                        + ";source=" + draft.reason() + ";hash=" + facts.inputHash().substring(0, 12),
                "taste_extract", now);
        return new Result(Outcome.CREATED, version, draft.reason());
    }

    private void advisoryLock(long userId) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                ps.setLong(1, userId);
                ps.executeQuery();
            }
            return null;
        });
    }

    private long newId() {
        return ((Number) ids.nextId(new Object())).longValue();
    }

    private static String reason(String trigger, TasteWriter.Draft draft) {
        return (trigger == null ? "unknown" : trigger) + "|"
                + (draft.fromModel() ? "llm" : "template") + "|" + draft.reason();
    }

    /** 旧版本权重可能是 '{}'（V10 之前）或历史脏数据，统一规范化成 10 维中性补全。 */
    private static String normalizeWeights(String raw) {
        if (raw == null || raw.isBlank()) return TasteWeights.neutral().toJson();
        try {
            return TasteWeights.of(Json.readMap(raw)).toJson();
        } catch (RuntimeException e) {
            return TasteWeights.neutral().toJson();
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().strip();
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
