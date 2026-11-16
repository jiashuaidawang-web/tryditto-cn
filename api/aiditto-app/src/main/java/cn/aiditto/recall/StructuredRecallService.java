package cn.aiditto.recall;

import cn.aiditto.domain.geo.IsoCellGrid;
import cn.aiditto.infra.json.Json;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R3 标签 / R4 结构化偏好 / R5 探索公平：纯 PG 侧，不依赖 Qdrant。
 * Qdrant 不可用时这三路仍能独立工作，是「降级到结构化召回仍可交付」的底座。
 *
 * 一次 SQL 取候选基础集后在内存应用与向量路完全相同的 Filter，
 * 保证「同一 filter 复用于所有路与 FLAT 对照」；数据规模到万级时再把条件下推 SQL。
 *
 * Step 5 偏离补全：
 *  - 地理判定由「place_id 单点相等」升级为「本地几何等时圈格网交集」，
 *    与 Qdrant payload 的 iso_cells 完全同口径（同一个 IsoCellGrid 纯函数）。
 *  - R5 探索公平使用真实 starve_weeks（连续空窗周期）而非恒 0 + 取模，
 *    让长期没被匹配到的用户获得确定性优先，仍是纯确定性、仿真可复现。
 *
 * R4 合规说明：规格里的「审美多模态图像 embedding」与技术方案 §8.1
 * 「照片不发给任何模型」冲突，本实现保持结构化偏好路（不碰任何照片），
 * 并把该路的可用维度补到合规上限（见 preferenceScore）。
 */
@Service
public class StructuredRecallService {

    private record Row(long userId, String school, String gender, Integer age, Integer height,
                       Set<String> tags, Set<String> valueTags, String matchingStyle,
                       String educationImportance, String budget, String placeId,
                       List<String> cells, int starveWeeks, long completedAt, boolean synthetic) {}

    private static final String BASE_SQL = """
            SELECT p.user_id, p.school_domain, p.gender,
                   EXTRACT(YEAR FROM AGE(p.birthday)) AS age, p.height_cm, p.hobbies,
                   p.relationship_looking, p.matching_style, p.education_importance, p.budget,
                   p.place_id, p.travel_mode, p.travel_minutes,
                   pl.lat AS place_lat, pl.lng AS place_lng,
                   u.onboarding_completed_at, u.is_synthetic,
                   (SELECT COUNT(*) FROM week w
                    WHERE w.state = 'closed'
                      AND w.id > COALESCE((SELECT MAX(m.week_id) FROM "match" m
                                           WHERE (m.user_low = p.user_id OR m.user_high = p.user_id)
                                             AND m.state IN ('confirmed','feedback_open','closed')), 0)
                   ) AS starve_weeks
            FROM profile p JOIN "user" u ON u.id = p.user_id
            LEFT JOIN place pl ON pl.id = p.place_id
            WHERE u.status = 'active'
            """;

    private final JdbcTemplate jdbc;
    private final RecallProperties props;

    @Autowired
    public StructuredRecallService(JdbcTemplate jdbc, RecallProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    /** 兼容既有测试构造（等时圈默认开启）。 */
    public StructuredRecallService(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    /** 返回 R3/R4/R5 三路（已应用同一 Filter 的硬约束）。 */
    public Map<RecallModel.Source, List<RecallModel.Hit>> routes(
            long selfId, RecallModel.Filter filter, RecallProperties unusedProps) {

        List<Row> all = load();
        Row self = all.stream().filter(r -> r.userId() == selfId).findFirst().orElse(null);
        Set<Long> forbidden = Set.copyOf(filter.forbidden());
        List<Row> pool = new ArrayList<>();
        for (Row r : all) {
            if (r.userId() == selfId || forbidden.contains(r.userId())) continue;
            if (!matches(r, filter)) continue;
            pool.add(r);
        }

        Map<RecallModel.Source, List<RecallModel.Hit>> out = new LinkedHashMap<>();
        out.put(RecallModel.Source.R3_TAGS,
                rank(pool, r -> tagScore(self, r), unusedProps.tagTopK(), RecallModel.Source.R3_TAGS));
        out.put(RecallModel.Source.R4_PREFERENCE,
                rank(pool, r -> preferenceScore(self, r), unusedProps.tagTopK(), RecallModel.Source.R4_PREFERENCE));
        out.put(RecallModel.Source.R5_EXPLORE,
                rank(pool, r -> exploreScore(r), unusedProps.exploreTopK(), RecallModel.Source.R5_EXPLORE));
        return out;
    }

    private List<Row> load() {
        List<Row> out = new ArrayList<>();
        boolean iso = props == null || Boolean.TRUE.equals(props.isoEnabled());
        for (Map<String, Object> m : jdbc.queryForList(BASE_SQL)) {
            long id = num(m.get("user_id"));
            long completed = m.get("onboarding_completed_at") instanceof java.sql.Timestamp ts
                    ? ts.getTime() : 0L;
            String placeId = str(m.get("place_id"));
            List<String> cells = iso
                    ? IsoCellGrid.cellsOrWildcard(dbl(m.get("place_lat")), dbl(m.get("place_lng")),
                            str(m.get("travel_mode")), intOrNull(m.get("travel_minutes")))
                    : List.of();
            if (cells.isEmpty() && !placeId.isEmpty()) cells = List.of(placeId);
            out.add(new Row(id, str(m.get("school_domain")), GenderNorms.norm(str(m.get("gender"))),
                    m.get("age") instanceof Number n ? n.intValue() : null,
                    m.get("height_cm") instanceof Number n ? n.intValue() : null,
                    tags(m.get("hobbies")), tags(m.get("relationship_looking")),
                    str(m.get("matching_style")), str(m.get("education_importance")),
                    str(m.get("budget")), placeId, cells,
                    m.get("starve_weeks") instanceof Number n ? Math.max(0, n.intValue()) : 0,
                    completed, Boolean.TRUE.equals(m.get("is_synthetic"))));
        }
        return out;
    }

    private static boolean matches(Row r, RecallModel.Filter f) {
        if (f.schoolCode() != null && !f.schoolCode().isBlank()
                && !f.schoolCode().equals(r.school())) return false;
        if (!f.expectedGenders().isEmpty() && !f.expectedGenders().contains(r.gender())) return false;
        if (f.ageMin() != null && (r.age() == null || r.age() < f.ageMin())) return false;
        if (f.ageMax() != null && (r.age() == null || r.age() > f.ageMax())) return false;
        if (f.heightMin() != null && (r.height() == null || r.height() < f.heightMin())) return false;
        if (f.heightMax() != null && (r.height() == null || r.height() > f.heightMax())) return false;
        // 等时圈格网交集：与 Qdrant 的 iso_cells match-any 语义严格一致（方向不可颠倒）
        if (!IsoCellGrid.matches(f.isoCells(), r.cells())) return false;
        return true;
    }

    private static double tagScore(Row self, Row r) {
        if (self == null || self.tags().isEmpty() || r.tags().isEmpty()) return 0;
        long hit = r.tags().stream().filter(self.tags()::contains).count();
        return hit == 0 ? 0 : (double) hit / self.tags().size();
    }

    /**
     * R4 合规上限：不碰任何照片，把可用的结构化偏好维度全部纳入
     * （匹配风格 / 学历看重 / 预算 / 活动地点 + 价值观标签 Jaccard）。
     */
    private static double preferenceScore(Row self, Row r) {
        if (self == null) return 0;
        double s = 0;
        if (eq(self.matchingStyle(), r.matchingStyle())) s += 1;
        if (eq(self.educationImportance(), r.educationImportance())) s += 1;
        if (eq(self.budget(), r.budget())) s += 1;
        if (eq(self.placeId(), r.placeId())) s += 1;
        double base = s / 4.0;
        Set<String> a = self.valueTags(), b = r.valueTags();
        if (!a.isEmpty() && !b.isEmpty()) {
            long inter = a.stream().filter(b::contains).count();
            long union = a.size() + b.size() - inter;
            double jaccard = union == 0 ? 0 : (double) inter / union;
            return 0.7 * base + 0.3 * jaccard;
        }
        return base;
    }

    /** 确定性受控探索：真实饥饿周数优先 + 取模伪随机，绝不引入运行时随机导致回放不可复现。 */
    private static double exploreScore(Row r) {
        double starve = Math.min(8, r.starveWeeks()) / 8.0; // 封顶 8 周，避免老用户永久霸榜
        double recency = r.completedAt() <= 0 ? 0 : 0.1;
        return starve + recency + (r.userId() % 97) / 1000.0;
    }

    private static List<RecallModel.Hit> rank(List<Row> pool, java.util.function.ToDoubleFunction<Row> scorer,
                                              int limit, RecallModel.Source source) {
        List<Row> sorted = new ArrayList<>(pool);
        sorted.sort(Comparator.comparingDouble(scorer).reversed().thenComparingLong(Row::userId));
        List<RecallModel.Hit> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            Row r = sorted.get(i);
            out.add(new RecallModel.Hit(r.userId(), scorer.applyAsDouble(r), source));
        }
        return out;
    }

    private static Set<String> tags(Object json) {
        if (json == null) return Set.of();
        try {
            return Set.copyOf(Json.readStringList(String.valueOf(json)));
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private static boolean eq(String a, String b) {
        return a != null && !a.isBlank() && a.equals(b);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
