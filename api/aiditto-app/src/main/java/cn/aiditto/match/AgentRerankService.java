package cn.aiditto.match;

import cn.aiditto.ai.LlmGateway;
import cn.aiditto.ai.LlmRequest;
import cn.aiditto.ai.LlmResult;
import cn.aiditto.domain.matching.Edge;
import cn.aiditto.domain.matching.PairKey;
import cn.aiditto.infra.json.Json;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 6：Agent 精排主导（Q1 决策）。
 *
 * 对粗排后的 Top-N 候选对跑「分身模拟 3 轮 → 裁判 rubric 打分」，AI 分写进
 * MatchingInput.aiScores 主导 Blossom 配对排序；HardFilter 硬约束、风格门槛、
 * 展示分全部不动（C 端红线：模型幻觉不能把不合格对推给学生）。
 *
 * 四级降级（任一失败都绝不让周三空发）：
 *  L1 主模型故障 → LlmGateway 已有主备切换/重试/熔断；
 *  L2 分身模拟失败 → 跳过模拟，裁判只看画像（降低 chemistry 可信度由 confidence 表达）；
 *  L3 裁判输出非法/全挂 → 该对不注入 AI 分，回落到 M2 规则边权；
 *  L4 整批降级 → 返回空 Map，等价于关闭精排，周批照常完成。
 *
 * 脱敏：画像只取 user_taste 的 persona_text/taste_text（Step 4 已由 TasteFacts 白名单产出），
 * 且再经 clip 与 PII 正则扫描；绝不读照片、手机号、邮箱、真实姓名。
 */
@Service
public class AgentRerankService {

    private static final Logger log = LoggerFactory.getLogger(AgentRerankService.class);

    private static final String TASTE_SQL = """
            SELECT t.user_id, t.persona_text, t.taste_text, p.ai_constraints::text AS ai_constraints
            FROM user_taste t
            LEFT JOIN profile p ON p.user_id = t.user_id
            WHERE t.is_active AND t.user_id = ANY (?)
            """;

    private final LlmGateway llm;
    private final JdbcTemplate jdbc;
    private final MatchAgentProperties props;
    private final IdentifierGenerator ids;
    private final Clock clock;
    private final JudgeCalibrationService calibration;

    public AgentRerankService(LlmGateway llm, JdbcTemplate jdbc, MatchAgentProperties props,
                              IdentifierGenerator ids, Clock clock, JudgeCalibrationService calibration) {
        this.llm = llm;
        this.jdbc = jdbc;
        this.props = props;
        this.ids = ids;
        this.clock = clock;
        this.calibration = calibration;
    }

    public record Outcome(Map<PairKey, Integer> aiScores, int judged, int degradedPairs,
                          int simulated, String mode) {}

    /**
     * 给通过硬约束与门槛的边打分。返回空 Map = 完全回落 M2（安全）。
     * 只处理规则分最高的 Top-N 对，控制 LLM 成本（v3 分层漏斗）。
     */
    public Outcome rerank(long weekId, List<Edge> edges) {
        if (!Boolean.TRUE.equals(props.enabled()) || edges == null || edges.isEmpty()) {
            return new Outcome(Map.of(), 0, 0, 0, "disabled");
        }
        calibration.refresh(); // 恢复/同步已生效的裁判校准偏置（未达标时为 0）
        List<Edge> top = new ArrayList<>(edges);
        top.sort(java.util.Comparator.comparingLong(Edge::weight).reversed());
        int limit = Math.min(props.topPairs(), top.size());
        top = top.subList(0, limit);

        Map<Long, String[]> personas = loadPersonas(top);
        Map<PairKey, Integer> scores = new LinkedHashMap<>();
        int judged = 0, degraded = 0, simulated = 0;
        for (Edge e : top) {
            String[] a = personas.getOrDefault(e.low(), new String[]{"", "", ""});
            String[] b = personas.getOrDefault(e.high(), new String[]{"", "", ""});
            java.util.Set<String> declared = declaredTags(a, b);
            if (a[0].isBlank() || b[0].isBlank()) {
                degraded++; // 无画像不猜分
                // 降级也必须留痕，否则事后无法举证"为什么这一对没被精排"
                persist(weekId, e, Map.of("status", "no-persona", "total", 0,
                        "confidence", 0.0, "degraded", true), "no-persona");
                continue;
            }
            String transcript = "";
            if (Boolean.TRUE.equals(props.simulateEnabled())) {
                LlmResult sim = llm.call(new LlmRequest("simulate_date", AiJudge.SIM_PROMPT_VERSION,
                        AiJudge.simulateSystem(), AiJudge.simulateUser(a[0], b[0], "校园"),
                        700, 0.8, true, true));
                String issue = sim.ok() ? AiJudge.validateTranscript(sim.content()) : "模拟不可用";
                if (issue == null) {
                    transcript = sim.content();
                    simulated++;
                } else {
                    log.debug("simulate degraded pair={} reason={}", e.key(), issue);
                }
            }
            LlmResult judgedResult = llm.call(new LlmRequest("judge_chemistry", AiJudge.PROMPT_VERSION,
                    AiJudge.judgeSystem(), AiJudge.judgeUser(a[0], b[0], a[1], b[1], transcript, true, true),
                    700, 0.2, true, true));
            // Step 8：只认 profile.ai_constraints 里声明过的结构化红线标签（§8.1 不送原文）。
            // 模型自报的泛泛"差异/爱好不同"一律忽略，防止跨表面共鸣被误杀。
            AiJudge.Verdict v = judgedResult.ok()
                    ? AiJudge.parse(judgedResult.content(), declared) : null;
            if (v == null || !v.valid()) {
                degraded++;
                persist(weekId, e, null, "invalid-verdict");
                continue;
            }
            // 裁判校准（≥30 条反馈才生效，±10 封顶）；无校准时逐位等同原值
            int total = calibration.apply(AiJudge.applyConfidence(v.total(), v.confidence()));
            if (total >= props.minTotal() && v.conflicts().isEmpty()) {
                scores.put(e.key(), total);
            }
            judged++;
            if (v.degraded()) degraded++;
            persist(weekId, e, AiJudge.report(v, transcript), v.degraded() ? "low-confidence" : "ok");
        }
        String mode = judged == 0 ? "fully-degraded" : (degraded > 0 ? "partial" : "full");
        log.info("agent rerank week={} top={} judged={} simulated={} degraded={} mode={}",
                weekId, limit, judged, simulated, degraded, mode);
        return new Outcome(scores, judged, degraded, simulated, mode);
    }

    /** 画像只读脱敏产物；再叠加 PII 正则，防止历史脏数据进入提示词。 */
    private Map<Long, String[]> loadPersonas(List<Edge> top) {
        List<Long> userIds = new ArrayList<>();
        for (Edge e : top) {
            userIds.add(e.low());
            userIds.add(e.high());
        }
        Map<Long, String[]> out = new LinkedHashMap<>();
        try {
            jdbc.query(TASTE_SQL,
                    ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint",
                            userIds.stream().distinct().toArray())),
                    rs -> {
                        long id = rs.getLong("user_id");
                        String persona = scrub(rs.getString("persona_text"));
                        String taste = scrub(rs.getString("taste_text"));
                        java.util.Set<String> tags = ConstraintTags.from(rs.getString("ai_constraints"));
                        // [0] 脱敏画像；[1] 给裁判的**标签 id 清单**（不是红线原文）；[2] 服务端 declared 集合
                        out.put(id, new String[]{persona.isBlank() ? taste : persona,
                                ConstraintTags.describe(tags), String.join(",", tags)});
                    });
        } catch (RuntimeException e) {
            log.warn("load personas failed -> fully degraded: {}", e.getClass().getSimpleName());
        }
        return out;
    }

    /** 双方声明过的结构化红线标签合集；只有命中这些标签才允许裁判强制 total=0。 */
    private static java.util.Set<String> declaredTags(String[] a, String[] b) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String[] p : new String[][]{a, b}) {
            if (p.length < 3 || p[2].isBlank()) continue;
            for (String t : p[2].split(",")) {
                if (ConstraintTags.ALLOWED.contains(t)) out.add(t);
            }
        }
        return out;
    }

    private static String scrub(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\w.+-]+@[\\w.-]+", "[邮箱]")
                .replaceAll("1[3-9]\\d{9}", "[手机号]")
                .replaceAll("sk-[A-Za-z0-9]{16,}", "[密钥]")
                .replaceAll("https?://\\S+", "[链接]");
        return t.length() > 800 ? t.substring(0, 800) : t;
    }

    /** 结构化留痕落 recall_candidate.sim_report（可举证、可回放、可审计）。失败不影响主流程。 */
    private void persist(long weekId, Edge e, Map<String, Object> report, String status) {
        try {
            String json = report == null ? Json.write(Map.of("status", status))
                    : Json.write(new LinkedHashMap<>(report) {{ put("status", status); }});
            jdbc.update("""
                    INSERT INTO recall_candidate
                      (id, week_id, user_id, cand_id, sources, rrf_score, recall_rank,
                       coarse_score, sim_report, final_score, llm_cost_rmb, degraded, created_at)
                    VALUES (?,?,?,?,'{R1_TASTE}'::TEXT[],?,1,?,CAST(? AS JSONB),?,0,?,?)
                    ON CONFLICT (week_id, user_id, cand_id) DO UPDATE SET
                      sim_report = EXCLUDED.sim_report, final_score = EXCLUDED.final_score,
                      degraded = EXCLUDED.degraded
                    """, ((Number) ids.nextId(new Object())).longValue(), weekId, e.low(), e.high(),
                    e.score().score() / 100.0, e.score().score() / 100.0, json,
                    report == null ? null : report.get("total"),
                    !"ok".equals(status), Timestamp.from(clock.instant()));
        } catch (RuntimeException ex) {
            log.debug("persist sim_report failed pair={} err={}", e.key(), ex.getClass().getSimpleName());
        }
    }
}
