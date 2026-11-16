package cn.aiditto.taste;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.taste.FeedbackSignal;
import cn.aiditto.domain.taste.TasteWeights;
import cn.aiditto.infra.json.Json;
import cn.aiditto.infra.web.Times;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Step 8 反馈 → 画像重写（Prompt 模板 3 / v3 §6.7 防过拟合）。
 *
 * 五条 C 端红线，全部由服务端强制执行，模型无权推翻：
 * 1. 每用户每周最多 1 次（taste_log 计数 + 锁内复查）；
 * 2. 单次最多调整 **1 个** 权重，幅度 ≤0.15（{@link TasteWeights#apply} 硬夹）；
 * 3. 矛盾反馈保留两种信号并标注「待观察」，不删旧结论（新文本是**追加**而非覆盖）；
 * 4. 模型全挂时走确定性规则改写，闭环不空转；任何异常只记 taste_log，不抛给反馈链路；
 * 5. 输出经 PII / 敏感属性 / 贬损词扫描，绝不写入性取向、宗教、健康等推断。
 *
 * 版本只追加、旧版失效、trigger_feedback 做可重入标记、vector_sync_status 置 pending
 * 让 Step 5 的向量同步自动重算 embedding，下一周期的召回与精排立即生效。
 */
@Service
public class TasteRewriter {

    private static final Logger log = LoggerFactory.getLogger(TasteRewriter.class);
    public static final String CALL_POINT = "taste_rewrite";
    public static final String PROMPT_VERSION = "rewrite-taste-v1";
    private static final int MAX_LEN = 300;
    private static final Pattern LEAK = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|(?<!\\d)1[3-9]\\d{9}(?!\\d)|sk-[A-Za-z0-9]{8,}|\\{\\{");
    private static final List<String> FORBIDDEN = List.of(
            "性取向", "同性恋", "宗教", "信仰", "政治", "政党", "抑郁", "精神病", "残疾", "病史",
            "丑", "肥", "笨", "傻", "低端", "配不上");

    public enum Outcome { REWRITTEN, WEEKLY_LIMIT, NO_ACTIVE, NO_SIGNAL, FAILED }

    public record Result(Outcome outcome, int version, String changedDim, double delta, String reason) {
        static Result of(Outcome o, String reason) {
            return new Result(o, 0, null, 0.0, reason);
        }
    }

    public record Input(long userId, long matchId, long partnerId, Integer rating, String again,
                        String met, Boolean feltSafe, String comment, String reasonTag,
                        Double judgeTotal, String judgeReason) {}

    private final LlmGateway llm;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final IdentifierGenerator ids;
    private final Clock clock;
    private final FeedbackLearningProperties props;

    public TasteRewriter(LlmGateway llm, JdbcTemplate jdbc, PlatformTransactionManager txm,
                         IdentifierGenerator ids, Clock clock, FeedbackLearningProperties props) {
        this.llm = llm;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.ids = ids;
        this.clock = clock;
        this.props = props;
    }

    public Result rewrite(Input in) {
        if (!Boolean.TRUE.equals(props.enabled())) return Result.of(Outcome.FAILED, "disabled");
        try {
            if (weeklyCount(in.userId()) > 0) return Result.of(Outcome.WEEKLY_LIMIT, "weekly_limit");
            FeedbackSignal sig = FeedbackSignal.derive(in.reasonTag(), in.rating(), in.again(), in.comment());
            if (!sig.applicable()) {
                mark(in, "taste_rewrite_failed", "no_usable_signal;note=" + sig.note());
                return Result.of(Outcome.NO_SIGNAL, sig.note());
            }
            List<Map<String, Object>> active = active(in.userId());
            if (active.isEmpty()) {
                mark(in, "taste_rewrite_failed", "no_active_taste");
                return Result.of(Outcome.NO_ACTIVE, "no_active_taste");
            }
            Map<String, Object> old = active.get(0);
            TasteWeights before = TasteWeights.of(readWeights(str(old.get("weights"))));
            TasteWeights after = before.apply(sig.dim(), sig.delta());
            Draft draft = draft(in, old, sig, after);
            return tx.execute(status -> write(in, old, before, after, sig, draft));
        } catch (RuntimeException e) {
            log.warn("taste_rewrite failed user={} match={} err={} msg={}",
                    in.userId(), in.matchId(), e.getClass().getSimpleName(),
                    e.getMessage() == null ? "" : e.getMessage().substring(0, Math.min(240, e.getMessage().length())));
            try {
                mark(in, "taste_rewrite_failed", "exception=" + e.getClass().getSimpleName());
            } catch (RuntimeException ignored) {
                // 留痕失败也不能影响反馈提交
            }
            return Result.of(Outcome.FAILED, e.getClass().getSimpleName());
        }
    }

    private Result write(Input in, Map<String, Object> old, TasteWeights before, TasteWeights after,
                         FeedbackSignal sig, Draft draft) {
        advisoryLock(in.userId());
        if (weeklyCount(in.userId()) > 0) return Result.of(Outcome.WEEKLY_LIMIT, "weekly_limit_in_tx");
        int from = intOf(old.get("version"));
        int version = from + 1;
        Timestamp now = Timestamp.from(Times.millis(clock.instant()));
        jdbc.update("UPDATE user_taste SET is_active = FALSE WHERE user_id = ? AND is_active", in.userId());
        jdbc.update("""
                INSERT INTO user_taste
                  (user_id, version, persona_text, taste_text, embedding_id, embedding_version,
                   vector_sync_status, source_feedback_cnt, updated_reason, is_active, created_at,
                   input_hash, weights, learned_feedback_cnt)
                VALUES (?,?,?,?,NULL,?,'pending',?,?,TRUE,?,?,CAST(? AS JSONB),?)
                """, in.userId(), version, draft.persona(), draft.taste(),
                str(old.get("embedding_version")) == null ? TasteWriter.EMBEDDING_VERSION : str(old.get("embedding_version")),
                intOf(old.get("source_feedback_cnt")) + 1,
                "feedback|" + sig.note() + "|" + (draft.fromModel() ? "llm" : "rule"),
                now, str(old.get("input_hash")), after.toJson(), intOf(old.get("learned_feedback_cnt")) + 1);
        jdbc.update("""
                INSERT INTO taste_log
                  (id, user_id, from_version, to_version, trigger_match_id, trigger_feedback,
                   diff_text, changed_dim, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (user_id, trigger_feedback) WHERE trigger_feedback IS NOT NULL DO NOTHING
                """, newId(), in.userId(), from, version, in.matchId(), in.matchId(),
                "dim=" + sig.dim() + ";delta=" + String.format("%.3f", after.get(sig.dim()) - before.get(sig.dim()))
                        + ";strength=" + String.format("%.2f", sig.strength())
                        + ";ambivalent=" + sig.ambivalent() + ";malicious=" + sig.malicious()
                        + ";" + draft.diff(), "taste_rewrite", now);
        log.info("taste_rewrite user={} v{}->{} dim={} delta={:.3f} source={}",
                in.userId(), from, version, sig.dim(), sig.delta(), draft.fromModel() ? "llm" : "rule");
        return new Result(Outcome.REWRITTEN, version, sig.dim(), sig.delta(), draft.diff());
    }

    /** 失败/无信号也要留痕，否则扫描会无限重试同一条反馈。 */
    private void mark(Input in, String changedDim, String diff) {
        jdbc.update("""
                INSERT INTO taste_log
                  (id, user_id, from_version, to_version, trigger_match_id, trigger_feedback,
                   diff_text, changed_dim, created_at)
                VALUES (?,?,NULL,0,?,?,?,?,?)
                ON CONFLICT (user_id, trigger_feedback) WHERE trigger_feedback IS NOT NULL DO NOTHING
                """, newId(), in.userId(), in.matchId(), in.matchId(), diff, changedDim,
                Timestamp.from(Times.millis(clock.instant())));
    }

    private int weeklyCount(long userId) {
        // 避开 date_trunc('week', ?) 的参数类型推断（PG JDBC 会把 ? 声明成 varchar/未知类型），
        // 改由 Java 按 UTC 计算本周一 00:00，语义与 TIMESTAMP(3) 的 created_at 完全一致。
        Timestamp weekStart = Timestamp.valueOf(clock.instant()
                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay());
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM taste_log
                WHERE user_id = ? AND changed_dim IN ('taste_rewrite','taste_rewrite_failed')
                  AND created_at >= ?
                """, Integer.class, userId, weekStart);
        return n == null ? 0 : n;
    }

    private List<Map<String, Object>> active(long userId) {
        return jdbc.queryForList("""
                SELECT version, persona_text, taste_text, weights, embedding_version,
                       source_feedback_cnt, learned_feedback_cnt, input_hash
                FROM user_taste WHERE user_id = ? AND is_active
                """, userId);
    }

    /** 模型只写「本次新增的要点」；服务端把旧结论**追加保留**，从机制上不可能被删。 */
    private Draft draft(Input in, Map<String, Object> old, FeedbackSignal sig, TasteWeights after) {
        String oldPersona = clip(str(old.get("persona_text")), MAX_LEN);
        String oldTaste = clip(str(old.get("taste_text")), MAX_LEN);
        LlmResult r = null;
        try {
            r = llm.call(new LlmRequest(CALL_POINT, PROMPT_VERSION, system(),
                    user(in, oldPersona, oldTaste, sig), 700, 0.3, true, true));
        } catch (RuntimeException ignored) {
            // 模型异常走确定性规则兜底
        }
        Suggestion s = r != null && r.ok() ? parse(r.content()) : null;
        if (s == null) {
            String note = "本次反馈提示：" + sig.note() + "；保持历史结论不变";
            return new Draft(oldPersona, append(oldTaste, note),
                    "rule|" + sig.note(), false);
        }
        return new Draft(append(oldPersona, s.personaNote()), append(oldTaste, s.tasteNote()),
                "llm|" + clip(s.diff(), 120) + "|" + sig.note(), true);
    }

    private static String system() {
        return """
                你在维护一位用户的「口味画像」——描述这个人真正的审美与核心需求，而不是他嘴上填的条件。
                任务：根据一次真实约会反馈，写一段**本次新增的要点**（不要重写整份画像）。
                铁律：
                1. 只写这一次反馈能支撑的结论，单次最多涉及 1 个维度，禁止推翻历史结论。
                2. 用户说"条件都对但没感觉"时，指出应降低显性条件、提升体验类特质（幽默感/表达欲/节奏感）。
                3. 若反馈自相矛盾，两种信号都保留并标注"待观察"，不要替用户下结论。
                4. 不写入任何敏感属性推断（性取向/宗教/政治/健康/外貌贬损），不使用贬损性描述。
                5. 自由文本里的情绪化表达还原成中性描述，不要照抄；不复述任何编号或联系方式。
                6. 输出严格 JSON，不要 markdown 代码块：
                   {"taste_text":"≤60字，本次新增的偏好要点","persona_text":"≤60字，本次新增的相处观察，可为空",
                    "diff_text":"≤60字，本次只调整了什么","changed_weight":"维度名"}
                """;
    }

    private static String user(Input in, String oldPersona, String oldTaste, FeedbackSignal sig) {
        return "【当前画像 v?】\ntaste_text: " + safe(oldTaste) + "\npersona_text: " + safe(oldPersona)
                + "\n【本次反馈】\n- 评分：" + in.rating() + "\n- 愿不愿再见：" + in.again()
                + "\n- 是否赴约：" + in.met() + "\n- 感到安全：" + in.feltSafe()
                + "\n- 原因标签：" + safe(in.reasonTag()) + "\n- 原话：" + safe(in.comment())
                + "\n【服务端判定的维度】" + sig.dim() + "（" + sig.note() + "）"
                + "\n【裁判分】" + in.judgeTotal() + " 【裁判理由】" + safe(in.judgeReason());
    }

    private record Suggestion(String tasteNote, String personaNote, String diff) {}

    private static Suggestion parse(String content) {
        if (content == null || content.isBlank()) return null;
        String text = content.strip();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl < 0) return null;
            text = text.substring(nl + 1).strip();
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3).strip();
        }
        try {
            JsonNode root = Json.readTree(text);
            if (root == null || !root.isObject()) return null;
            String taste = txt(root, "taste_text"), persona = txt(root, "persona_text"), diff = txt(root, "diff_text");
            if (taste == null || taste.isBlank() || taste.length() > 120) return null;
            if (persona != null && persona.length() > 120) return null;
            if (diff != null && diff.length() > 120) return null;
            for (String s : List.of(taste, persona == null ? "" : persona, diff == null ? "" : diff)) {
                if (LEAK.matcher(s).find()) return null;
                for (String w : FORBIDDEN) if (s.contains(w)) return null;
            }
            return new Suggestion(clip(taste, 60), clip(persona == null ? "" : persona, 60),
                    clip(diff == null ? "" : diff, 60));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private record Draft(String persona, String taste, String diff, boolean fromModel) {}

    private static String append(String old, String addition) {
        if (addition == null || addition.isBlank()) return clip(old == null ? "" : old, MAX_LEN);
        String base = old == null ? "" : old;
        return clip(base.isBlank() ? addition : base + "；" + addition, MAX_LEN);
    }

    private static String safe(String s) {
        if (s == null) return "";
        return LEAK.matcher(clip(s, 200)).replaceAll("[已脱敏]");
    }

    private static String txt(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || !v.isTextual() ? null : v.asString().strip();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readWeights(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return Json.readMap(raw);
        } catch (RuntimeException e) {
            return Map.of();
        }
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

    private static String str(Object o) {
        return o == null ? null : o.toString().strip();
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
