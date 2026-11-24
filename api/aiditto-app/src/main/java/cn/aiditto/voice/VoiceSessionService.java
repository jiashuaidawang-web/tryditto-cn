package cn.aiditto.voice;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.voice.VoiceModels;
import cn.aiditto.domain.voice.VoiceModels.AnswerOutcome;
import cn.aiditto.domain.voice.VoiceModels.Option;
import cn.aiditto.domain.voice.VoiceModels.Scenario;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自适应情景采集引擎（创始人 2026-11-18 拍板「治本」：分身保真度比模拟次数重要）。
 *
 * 闭环：LLM 基于已答内容出情景题 + 按「预测用户最可能选什么」给选项排序（A=最可能）→
 * 用户选或自定义输入 →「预测 vs 实际」差距成为校准信号 → 生成下一题更贴合真人 →
 * 用户可随时停 → 基于全部作答推断人格 + 语气样本入库（资产，供分身学习）。
 *
 * 大模型失败绝不暴露给用户：出题有固定兜底题库（FALLBACK），推断失败存低置信度结论。
 */
@Service
public class VoiceSessionService {
    private static final int MAX_STEPS = 12;
    private static final int MIN_STEPS = 3;      // 至少答 3 题才考虑自动收敛
    private static final int STREAK_NEEDED = 3;  // 连续 3 次命中预测第一 → 系统已能稳定预测此人

    private final com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator ids;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final LlmGateway llm;
    private final Crypto crypto;
    private final TransactionTemplate tx;
    private final Clock clock;

    public VoiceSessionService(IdentifierGenerator ids, org.springframework.jdbc.core.JdbcTemplate jdbc,
                               LlmGateway llm, Crypto crypto, TransactionTemplate tx, Clock clock) {
        this.ids = ids; this.jdbc = jdbc; this.llm = llm; this.crypto = crypto; this.tx = tx; this.clock = clock;
    }

    private long newId() { return ((Number) ids.nextId(new Object())).longValue(); }
    private Timestamp ts(Instant i) { return Timestamp.from(i); }

    /** 开始一次采集：建 session + 生成第一题。 */
    public Map<String, Object> start(long userId) {
        // 若已有 active session，直接返回它的当前题（幂等，防重复开）
        List<Map<String, Object>> active = jdbc.queryForList("""
                SELECT id, state FROM voice_session WHERE user_id=? AND state='active' ORDER BY started_at DESC LIMIT 1
                """, userId);
        long sessionId;
        int step;
        if (!active.isEmpty()) {
            sessionId = ((Number) active.get(0).get("id")).longValue();
            // 刷新或重进：还没答的当前题原样返回，不再另出一题（否则未答题会堆在库里）。
            Map<String, Object> open = openQuestion(sessionId);
            if (open != null) return open;
            Integer mx = jdbc.queryForObject("SELECT COALESCE(MAX(step),0) FROM voice_question WHERE session_id=?",
                    Integer.class, sessionId);
            step = mx == null ? 0 : mx;
        } else {
            sessionId = newId();
            step = 0;
            Instant now = clock.instant();
            jdbc.update("""
                    INSERT INTO voice_session (id,user_id,state,prompt_version,step_count,started_at,created_at,updated_at)
                    VALUES (?,?,'active',?,0,?,?,?)
                    """, sessionId, userId, ScenarioPrompts.PROMPT_VERSION, ts(now), ts(now), ts(now));
        }
        int nextStep = step + 1;
        Scenario q = generateQuestion(userId, sessionId, nextStep);
        long questionId = persistQuestion(sessionId, nextStep, q);
        jdbc.update("UPDATE voice_session SET step_count=?, updated_at=? WHERE id=?", nextStep, ts(clock.instant()), sessionId);
        return questionView(sessionId, questionId, nextStep, q);
    }

    /** 作答一题：记录答案 + 算校准 + 生成下一题；若收敛或到上限则结束并推断人格。 */
    public Map<String, Object> answer(long userId, long questionId, String chosenKey, String customText) {
        // 校验题属于该用户
        Map<String, Object> qrow = jdbc.queryForMap("""
                SELECT q.session_id, q.step, q.scenario, q.options_json, s.state
                FROM voice_question q JOIN voice_session s ON s.id=q.session_id
                WHERE q.id=? AND s.user_id=?
                """, questionId, userId);
        long sessionId = ((Number) qrow.get("session_id")).longValue();
        String state = (String) qrow.get("state");
        if (!"active".equals(state)) throw DomainException.invalid("session", "这次采集已经结束");

        // JSONB 列读出是 PGobject，必须 toString() 而不是强转 String
        Scenario q = parseQuestion(String.valueOf(qrow.get("options_json")), (String) qrow.get("scenario"));
        boolean custom = customText != null && !customText.isBlank();
        AnswerOutcome outcome = custom ? AnswerOutcome.ofCustom() : VoiceModels.evaluate(q, chosenKey);
        if (!custom && chosenKeyIsInvalid(q, chosenKey)) throw DomainException.invalid("chosenKey", "请选一个选项，或自己写一句");

        Instant now = clock.instant();
        int step = ((Number) qrow.get("step")).intValue();
        int inserted = jdbc.update("""
                INSERT INTO voice_answer (id,question_id,session_id,user_id,chosen_key,custom_text_enc,predict_hit,trait_signal,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (question_id) DO NOTHING
                """, newId(), questionId, sessionId, userId,
                custom ? null : outcome.chosenKey(),
                custom ? crypto.encrypt(clip(customText.strip(), 80)) : null,
                custom ? null : outcome.predictHit(), outcome.traitSignal(), ts(now));
        if (inserted == 0) return answeredView(userId, sessionId, step);
        if (custom) {
            jdbc.update("""
                    INSERT INTO voice_sample (id,user_id,source,text_enc,context,created_at)
                    VALUES (?,?,'scenario_custom',?,?,?)
                    """, newId(), userId, crypto.encrypt(clip(customText.strip(), 80)),
                    clip(q.scenario(), 200), ts(now));
        }

        List<AnswerOutcome> all = outcomesOf(sessionId);
        boolean shouldStop = step >= MAX_STEPS || VoiceModels.converged(all, MIN_STEPS, STREAK_NEEDED);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", String.valueOf(sessionId));
        out.put("step", step);
        out.put("predictHit", custom ? null : outcome.predictHit());
        out.put("calibration", VoiceModels.calibrationRate(all));
        if (shouldStop) {
            Map<String, Object> profile = finish(userId, sessionId, "completed");
            out.put("done", true);
            out.put("profile", profile);
        } else {
            int nextStep = step + 1;
            Scenario next = generateQuestion(userId, sessionId, nextStep);
            long nextQid = persistQuestion(sessionId, nextStep, next);
            jdbc.update("UPDATE voice_session SET step_count=?, updated_at=? WHERE id=?",
                    nextStep, ts(clock.instant()), sessionId);
            out.put("done", false);
            out.put("question", questionView(sessionId, nextQid, nextStep, next));
        }
        return out;
    }

    /** 用户主动停止：基于已有作答推断，不勉强答完。 */
    public Map<String, Object> stop(long userId, long sessionId) {
        Map<String, Object> s = jdbc.queryForMap("SELECT user_id,state FROM voice_session WHERE id=?", sessionId);
        if (((Number) s.get("user_id")).longValue() != userId) throw DomainException.invalid("session", "这不是你的采集");
        if ("active".equals(s.get("state"))) return finish(userId, sessionId, "abandoned");
        return latestProfile(userId);
    }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private Scenario generateQuestion(long userId, long sessionId, int step) {
        List<String> history = historyLines(sessionId);
        LlmRequest req = new LlmRequest("voice_scenario", ScenarioPrompts.PROMPT_VERSION,
                ScenarioPrompts.system(), ScenarioPrompts.user(history), 600, 0.9, true, true);
        LlmResult r = llm.call(req);
        Scenario parsed = r.ok() ? ScenarioPrompts.parse(r.content()) : null;
        if (parsed == null) return fallbackQuestion(step);
        return parsed;
    }

    /** LLM 全挂时的固定兜底题库：保证流程不断，且首题就能勾出社交风格。 */
    private Scenario fallbackQuestion(int step) {
        List<Scenario> bank = List.of(
                new Scenario("你和一位刚认识的人约在校园咖啡馆，对方迟到了十分钟还没来。你会怎么做？", List.of(
                        new Option("A", "发消息问到哪了，顺便开玩笑说要罚一杯", "humor", 1),
                        new Option("B", "先等着，看看对方什么情况再说", "cautious", 2),
                        new Option("C", "直接打电话问是不是走错了", "direct", 3))),
                new Scenario("聊天时对方说了一个你不太认同的观点。你通常怎么回应？", List.of(
                        new Option("A", "先认真听完，再说出自己不同的看法", "empathetic", 1),
                        new Option("B", "直接说出你的看法，觉得坦诚更重要", "direct", 2),
                        new Option("C", "换开话题，避免当场争执", "cautious", 3))),
                new Scenario("周末突然空出来，你更想怎么度过？", List.of(
                        new Option("A", "约朋友出门，人越多越热闹", "outgoing", 1),
                        new Option("B", "一个人待着，安静做点自己的事", "independent", 2),
                        new Option("C", "看心情，有人约就出门", "easygoing", 3))));
        return bank.get((step - 1) % bank.size());
    }

    private List<String> historyLines(long sessionId) {
        return jdbc.query("""
                SELECT q.scenario, q.options_json, a.chosen_key, a.custom_text_enc
                FROM voice_answer a JOIN voice_question q ON q.id=a.question_id
                WHERE a.session_id=? ORDER BY q.step
                """, (rs, n) -> {
                    String line = "情景：" + rs.getString("scenario") + "；用户";
                    String key = rs.getString("chosen_key");
                    if (key != null) {
                        line += "选了 " + key;
                        String opts = String.valueOf(rs.getObject("options_json"));
                        try {
                            for (Object o : Json.readList(opts)) {
                                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                                if (key.equalsIgnoreCase(String.valueOf(m.get("key"))))
                                    line += "（" + m.get("text") + "）";
                            }
                        } catch (RuntimeException ignored) {}
                    } else {
                        byte[] enc = rs.getBytes("custom_text_enc");
                        String txt = enc == null ? "" : crypto.decrypt(enc);
                        line += "自己写：" + clip(txt, 80);
                    }
                    return line;
                }, sessionId);
    }

    private List<AnswerOutcome> outcomesOf(long sessionId) {
        return jdbc.query("""
                SELECT a.chosen_key, a.predict_hit, a.custom_text_enc, q.options_json, q.scenario
                FROM voice_answer a JOIN voice_question q ON q.id=a.question_id
                WHERE a.session_id=? ORDER BY q.step
                """, (rs, n) -> {
                    String key = rs.getString("chosen_key");
                    if (key == null) return AnswerOutcome.ofCustom();
                    Boolean hit = (Boolean) rs.getObject("predict_hit");
                    Scenario q = parseQuestion(String.valueOf(rs.getObject("options_json")), rs.getString("scenario"));
                    String trait = "";
                    for (Option o : q.options()) if (o.key().equalsIgnoreCase(key)) trait = o.trait();
                    return new AnswerOutcome(key, false, Boolean.TRUE.equals(hit), trait);
                }, sessionId);
    }

    private Map<String, Object> finish(long userId, long sessionId, String endState) {
        List<String> lines = historyLines(sessionId);
        LlmRequest req = new LlmRequest("voice_infer", ProfileInferencePrompts.PROMPT_VERSION,
                ProfileInferencePrompts.system(), ProfileInferencePrompts.user(lines), 600, 0.3, true, true);
        LlmResult r = llm.call(req);
        ProfileInferencePrompts.Inference inf = r.ok() ? ProfileInferencePrompts.parse(r.content()) : null;
        if (inf == null) inf = lowConfidenceFallback(lines);
        Instant now = clock.instant();
        int version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version),0)+1 FROM voice_profile WHERE user_id=?", Integer.class, userId);
        jdbc.update("UPDATE voice_profile SET is_active=FALSE WHERE user_id=?", userId);
        double calibration = VoiceModels.calibrationRate(outcomesOf(sessionId));
        jdbc.update("""
                INSERT INTO voice_profile (user_id,version,session_id,traits_json,summary_enc,calibration,prompt_version,is_active,created_at)
                VALUES (?,?,?,CAST(? AS JSONB),?,?,?,TRUE,?)
                """, userId, version, sessionId, Json.write(ProfileInferencePrompts.toJson(inf)),
                crypto.encrypt(inf.summary()), calibration, ProfileInferencePrompts.PROMPT_VERSION, ts(now));
        jdbc.update("UPDATE voice_session SET state=?, ended_at=?, updated_at=? WHERE id=?",
                endState, ts(now), ts(now), sessionId);
        return latestProfile(userId);
    }

    private ProfileInferencePrompts.Inference lowConfidenceFallback(List<String> lines) {
        String style = lines.isEmpty() ? "样本不足，暂无法判断说话风格。"
                : "基于 " + lines.size() + " 条情景作答，样本仍少，说话风格待更多互动确认。";
        return new ProfileInferencePrompts.Inference(
                List.of(new ProfileInferencePrompts.Inference.Trait("待观察", 0.2)), style, "样本不足，暂不推断性格。");
    }

    /** 对外只读当前生效的人格推断（资产）。 */
    public Map<String, Object> latestPublicProfile(long userId) {
        return latestProfile(userId);
    }

    private Map<String, Object> latestProfile(long userId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT version, traits_json, calibration, prompt_version, created_at
                    FROM voice_profile WHERE user_id=? AND is_active ORDER BY version DESC LIMIT 1
                    """, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Map.of("active", false);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", true);
        out.put("version", row.get("version"));
        Object traits = row.get("traits_json");
        tools.jackson.databind.JsonNode tree = traits instanceof String s ? cn.aiditto.infra.json.Json.readTree(s)
                : cn.aiditto.infra.json.Json.readTree(String.valueOf(traits));
        out.put("traits", tree);
        String style = tree.path("voice_style").asString("");
        String summary = tree.path("summary").asString("");
        if (!style.isBlank()) out.put("voiceStyle", style);
        if (!summary.isBlank()) out.put("summary", summary);
        out.put("calibration", row.get("calibration"));
        out.put("promptVersion", row.get("prompt_version"));
        return out;
    }

    /** 当前会话里还没作答的最新一题；没有则返回 null。 */
    private Map<String, Object> openQuestion(long sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT q.id, q.step, q.scenario, q.options_json
                FROM voice_question q
                LEFT JOIN voice_answer a ON a.question_id = q.id
                WHERE q.session_id = ? AND a.id IS NULL
                ORDER BY q.step DESC
                LIMIT 1
                """, sessionId);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        long qid = ((Number) row.get("id")).longValue();
        int step = ((Number) row.get("step")).intValue();
        Scenario q = parseQuestion(String.valueOf(row.get("options_json")), (String) row.get("scenario"));
        return questionView(sessionId, qid, step, q);
    }

    /** 同一题重复提交：不另出题，把已经生成的下一题或已结束的人格资产还回去。 */
    private Map<String, Object> answeredView(long userId, long sessionId, int step) {
        String state = jdbc.queryForObject("SELECT state FROM voice_session WHERE id=?", String.class, sessionId);
        List<Boolean> hits = jdbc.query("""
                SELECT a.predict_hit FROM voice_answer a
                JOIN voice_question q ON q.id = a.question_id
                WHERE q.session_id = ? AND q.step = ?
                """, (rs, n) -> (Boolean) rs.getObject("predict_hit"), sessionId, step);
        Boolean hit = hits.isEmpty() ? null : hits.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", String.valueOf(sessionId));
        out.put("step", step);
        out.put("predictHit", hit);
        out.put("calibration", VoiceModels.calibrationRate(outcomesOf(sessionId)));
        if (!"active".equals(state)) {
            out.put("done", true);
            out.put("profile", latestProfile(userId));
            return out;
        }
        Map<String, Object> open = openQuestion(sessionId);
        out.put("done", false);
        if (open != null) out.put("question", open);
        return out;
    }

    private long persistQuestion(long sessionId, int step, Scenario q) {
        List<Map<String, Object>> opts = new ArrayList<>();
        for (Option o : q.options())
            opts.add(Map.of("key", o.key(), "text", o.text(), "trait", o.trait(), "predict_rank", o.predictRank()));
        long id = newId();
        jdbc.update("""
                INSERT INTO voice_question (id,session_id,step,scenario,options_json,prompt_version,created_at)
                VALUES (?,?,?,?,CAST(? AS JSONB),?,?)
                """, id, sessionId, step, q.scenario(), Json.write(opts),
                ScenarioPrompts.PROMPT_VERSION, ts(clock.instant()));
        return id;
    }

    private Map<String, Object> questionView(long sessionId, long questionId, int step, Scenario q) {
        List<Map<String, Object>> opts = new ArrayList<>();
        for (Option o : q.options())
            opts.add(Map.of("key", o.key(), "text", o.text()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", String.valueOf(sessionId));
        out.put("questionId", String.valueOf(questionId));
        out.put("step", step);
        out.put("scenario", q.scenario());
        out.put("options", opts);   // 前端只看到文字，不泄露 predict_rank（防被刷）
        return out;
    }

    private Scenario parseQuestion(String optionsJson, String scenario) {
        try {
            List<Option> opts = new ArrayList<>();
            for (Object o : Json.readList(optionsJson)) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                opts.add(new Option(String.valueOf(m.get("key")), String.valueOf(m.get("text")),
                        String.valueOf(m.getOrDefault("trait", "")),
                        ((Number) m.getOrDefault("predict_rank", 99)).intValue()));
            }
            return new Scenario(scenario, opts);
        } catch (RuntimeException e) {
            return fallbackQuestion(1);
        }
    }

    private static boolean chosenKeyIsInvalid(Scenario q, String key) {
        if (key == null) return true;
        for (Option o : q.options()) if (o.key().equalsIgnoreCase(key.trim())) return false;
        return true;
    }

    private static String clip(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }
}
