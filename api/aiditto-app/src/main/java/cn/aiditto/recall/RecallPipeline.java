package cn.aiditto.recall;

import cn.aiditto.domain.geo.IsoCellGrid;
import cn.aiditto.domain.recall.RrfFusion;
import cn.aiditto.infra.crypto.Crypto;
import cn.aiditto.infra.json.Json;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 五路召回编排（Step 5.3）。所有路共用同一个 Filter；RRF 融合 → 保底 → 粗排 Top15 → 留痕。
 *
 * 降级：Qdrant 不可用只跑 R3/R4/R5 结构化路，返回 degraded=true 但**仍有候选**，
 * 保证周三 18:00 前一定能交付。粗排结果是 LLM 全挂时的交付兜底。
 */
@Service
public class RecallPipeline {

    private static final Logger log = LoggerFactory.getLogger(RecallPipeline.class);

    private static final Map<String, Double> WEIGHTS = Map.of(
            "R1_TASTE", 1.0, "R2_PERSONA", 0.9, "R3_TAGS", 0.7,
            "R4_PREFERENCE", 0.4, "R5_EXPLORE", 0.6);

    public record Outcome(long userId, long weekId, List<RecallModel.Candidate> coarse,
                          Map<String, Double> shares, boolean degraded, int shortfall,
                          String engine, int fusedCount) {}

    private static final String SELF_SQL = """
            SELECT p.school_domain, p.gender, p.age_min, p.age_max, p.height_min, p.height_max,
                   p.place_id, p.travel_mode, p.travel_minutes, pl.lat AS place_lat, pl.lng AS place_lng,
                   p.expected_genders_enc, t.persona_text, t.taste_text
            FROM profile p JOIN user_taste t ON t.user_id = p.user_id AND t.is_active
            LEFT JOIN place pl ON pl.id = p.place_id
            WHERE p.user_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final RecallService vector;
    private final EmbeddingService embedding;
    private final StructuredRecallService structured;
    private final FeedbackPrior feedbackPrior;
    private final RecallProperties props;
    private final Crypto crypto;
    private final IdentifierGenerator ids;
    private final Clock clock;

    /** 兼容既有测试构造：反馈先验由 jdbc 自建。 */
    public RecallPipeline(JdbcTemplate jdbc, RecallService vector, EmbeddingService embedding,
                          StructuredRecallService structured, RecallProperties props,
                          Crypto crypto, IdentifierGenerator ids, Clock clock) {
        this(jdbc, vector, embedding, structured, new FeedbackPrior(jdbc), props,
                crypto, ids, clock);
    }

    @Autowired
    public RecallPipeline(JdbcTemplate jdbc, RecallService vector, EmbeddingService embedding,
                          StructuredRecallService structured, FeedbackPrior feedbackPrior,
                          RecallProperties props, Crypto crypto, IdentifierGenerator ids, Clock clock) {
        this.jdbc = jdbc;
        this.vector = vector;
        this.embedding = embedding;
        this.structured = structured;
        this.feedbackPrior = feedbackPrior;
        this.props = props;
        this.crypto = crypto;
        this.ids = ids;
        this.clock = clock;
    }

    public Outcome recall(long userId, long weekId) {
        List<Map<String, Object>> selfRows = jdbc.queryForList(SELF_SQL, userId);
        if (selfRows.isEmpty()) {
            return new Outcome(userId, weekId, List.of(), Map.of(), true, props.minCandidates(),
                    "no-taste", 0);
        }
        Map<String, Object> self = selfRows.get(0);
        RecallModel.Filter filter = filter(userId, self);
        float[] taste = embedding.embed(String.valueOf(self.get("taste_text")));
        float[] persona = embedding.embed(String.valueOf(self.get("persona_text")));

        Map<RecallModel.Source, List<RecallModel.Hit>> hits = new LinkedHashMap<>();
        boolean degraded = false;
        int usedEf = props.hnswEf();
        try {
            hits.put(RecallModel.Source.R1_TASTE,
                    vector.taste(taste, filter, props.tasteTopK(), usedEf));
            hits.put(RecallModel.Source.R2_PERSONA,
                    vector.persona(persona, filter, props.personaTopK(), usedEf));
        } catch (RuntimeException e) {
            log.warn("recall qdrant unavailable user={} err={} -> structured-only", userId,
                    e.getClass().getSimpleName());
            degraded = true;
        }
        hits.putAll(structured.routes(userId, filter, props));

        List<RrfFusion.Fused> fused = fuse(hits);
        // 保底 ①：提高 hnsw_ef 重查；②仍不足则由结构化路保底并写 shortfall
        if (fused.size() < props.minCandidates() && !degraded) {
            usedEf = Math.min(props.maxHnswEf(), usedEf * 2);
            try {
                hits.put(RecallModel.Source.R1_TASTE,
                        vector.taste(taste, filter, props.tasteTopK(), usedEf));
                hits.put(RecallModel.Source.R2_PERSONA,
                        vector.persona(persona, filter, props.personaTopK(), usedEf));
                fused = fuse(hits);
            } catch (RuntimeException e) {
                degraded = true;
            }
        }
        int shortfall = Math.max(0, props.minCandidates() - fused.size());
        if (shortfall > 0) {
            log.warn("recall_shortfall user={} fused={} min={} ef={}", userId,
                    fused.size(), props.minCandidates(), usedEf);
        }

        Map<String, Double> shares = new LinkedHashMap<>();
        for (RecallModel.Source s : RecallModel.Source.values()) {
            shares.put(s.name(), RrfFusion.share(fused, s.name()));
        }
        // 独占占比才是"单路垄断"的真实信号；旧 share 在五路共识时会全部等于 1.0，
        // 用它告警会持续误报（200 人小池尤其明显）。
        double exclusive = cn.aiditto.domain.recall.RecallMatrix.maxExclusiveRouteShare(fused);
        if (exclusive > 0.6) {
            log.warn("recall single-route dominance user={} exclusiveShare={} fused={}", userId,
                    String.format("%.3f", exclusive), fused.size());
        }
        if (fused.size() < props.minCandidates()) {
            log.warn("recall narrow-pool user={} fused={} min={} (pool scale, not engine failure)",
                    userId, fused.size(), props.minCandidates());
        }

        List<RecallModel.Candidate> coarse = coarseRank(fused, hits);
        persist(userId, weekId, coarse, degraded);
        return new Outcome(userId, weekId, coarse, shares, degraded || shortfall > 0,
                shortfall, degraded ? "structured-only" : vector.engine(), fused.size());
    }

    private RecallModel.Filter filter(long userId, Map<String, Object> self) {
        List<String> expected = GenderNorms.ALL;
        Object enc = self.get("expected_genders_enc");
        if (enc instanceof byte[] b && b.length > 0) {
            try {
                List<String> v = Json.readStringList(crypto.decrypt(b));
                if (!v.isEmpty()) expected = GenderNorms.normList(v);
            } catch (RuntimeException ignored) {
                // 用默认双向偏好
            }
        }
        String place = str(self.get("place_id"));
        List<String> isoCells = Boolean.TRUE.equals(props.isoEnabled())
                ? IsoCellGrid.queryCells(dbl(self.get("place_lat")), dbl(self.get("place_lng")),
                        str(self.get("travel_mode")), intOrNull(self.get("travel_minutes")))
                : List.of();
        if (isoCells.isEmpty() && !place.isEmpty()) isoCells = List.of(place); // 无坐标回退单点
        List<Long> forbidden = new ArrayList<>();
        forbidden.addAll(jdbc.queryForList("""
                SELECT user_hi FROM ai_pair_memory WHERE forbidden AND user_lo = ?
                UNION SELECT user_lo FROM ai_pair_memory WHERE forbidden AND user_hi = ?
                """, Long.class, userId, userId));
        return new RecallModel.Filter(userId, str(self.get("school_domain")), expected,
                GenderNorms.norm(str(self.get("gender"))), intOrNull(self.get("age_min")), intOrNull(self.get("age_max")),
                intOrNull(self.get("height_min")), intOrNull(self.get("height_max")),
                isoCells, forbidden, embedding.version(), true, null);
    }

    private List<RrfFusion.Fused> fuse(Map<RecallModel.Source, List<RecallModel.Hit>> hits) {
        List<List<RrfFusion.Ranked>> routes = new ArrayList<>();
        for (Map.Entry<RecallModel.Source, List<RecallModel.Hit>> e : hits.entrySet()) {
            List<RrfFusion.Ranked> ranked = new ArrayList<>();
            List<RecallModel.Hit> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                ranked.add(new RrfFusion.Ranked(list.get(i).userId(), i + 1, e.getKey().name()));
            }
            routes.add(ranked);
        }
        return RrfFusion.merge(routes, WEIGHTS, props.rrfK());
    }

    /** 0.40 条件契合 + 0.30 RRF 归一 + 0.10 曝光公平 + 0.20 反馈历史（全部真实计算、权重可配）。 */
    private List<RecallModel.Candidate> coarseRank(List<RrfFusion.Fused> fused,
                                                    Map<RecallModel.Source, List<RecallModel.Hit>> hits) {
        if (fused.isEmpty()) return List.of();
        double maxRrf = fused.stream().mapToDouble(RrfFusion.Fused::score).max().orElse(1.0);
        Map<Long, Double> fit = new LinkedHashMap<>();
        for (RecallModel.Source s : List.of(RecallModel.Source.R3_TAGS, RecallModel.Source.R4_PREFERENCE)) {
            for (RecallModel.Hit h : hits.getOrDefault(s, List.of())) {
                fit.merge(h.userId(), h.score(), Math::max);
            }
        }
        List<Long> explored = hits.getOrDefault(RecallModel.Source.R5_EXPLORE, List.of())
                .stream().map(RecallModel.Hit::userId).toList();
        List<Long> ids = fused.stream().map(RrfFusion.Fused::userId).toList();
        Map<Long, Double> priors = feedbackPrior.load(ids);
        double wCond = props.coarseWeightCondition(), wRrf = props.coarseWeightRrf();
        double wFair = props.coarseWeightFairness(), wFb = props.coarseWeightFeedback();
        double sum = wCond + wRrf + wFair + wFb;
        if (sum <= 0) { wCond = 0.40; wRrf = 0.30; wFair = 0.10; wFb = 0.20; sum = 1.0; }
        List<RecallModel.Candidate> out = new ArrayList<>();
        for (RrfFusion.Fused f : fused) {
            double condition = Math.min(1.0, fit.getOrDefault(f.userId(), 0.0));
            double rrfNorm = maxRrf <= 0 ? 0 : f.score() / maxRrf;
            double fairness = explored.contains(f.userId()) ? 1.0 : 0.0;
            double feedback = feedbackPrior.neutralIfAbsent(priors, f.userId());
            double coarse = (wCond * condition + wRrf * rrfNorm + wFair * fairness
                    + wFb * feedback) / sum;
            List<RecallModel.Source> src = f.sources().stream()
                    .map(RecallModel.Source::valueOf).toList();
            out.add(new RecallModel.Candidate(f.userId(), f.score(), coarse, src));
        }
        out.sort(java.util.Comparator.comparingDouble(RecallModel.Candidate::coarseScore).reversed()
                .thenComparingLong(RecallModel.Candidate::userId));
        return out.stream().limit(props.coarseTopN()).toList();
    }

    private void persist(long userId, long weekId, List<RecallModel.Candidate> coarse, boolean degraded) {
        Timestamp now = Timestamp.from(clock.instant());
        int rank = 1;
        for (RecallModel.Candidate c : coarse) {
            String arr = "{" + String.join(",", c.sources().stream()
                    .map(Enum::name).toList()) + "}";
            jdbc.update("""
                    INSERT INTO recall_candidate
                      (id, week_id, user_id, cand_id, sources, rrf_score, recall_rank,
                       coarse_score, sim_report, final_score, llm_cost_rmb, degraded, created_at)
                    VALUES (?,?,?,?,CAST(? AS TEXT[]),?,?,?,NULL,?,0,?,?)
                    ON CONFLICT (week_id, user_id, cand_id) DO UPDATE SET
                      sources = EXCLUDED.sources, rrf_score = EXCLUDED.rrf_score,
                      recall_rank = EXCLUDED.recall_rank, coarse_score = EXCLUDED.coarse_score,
                      final_score = EXCLUDED.final_score, degraded = EXCLUDED.degraded
                    """, newId(), weekId, userId, c.userId(), arr, c.rrfScore(), rank++,
                    c.coarseScore(), c.coarseScore(), degraded, now);
        }
    }

    private long newId() {
        return ((Number) ids.nextId(new Object())).longValue();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
