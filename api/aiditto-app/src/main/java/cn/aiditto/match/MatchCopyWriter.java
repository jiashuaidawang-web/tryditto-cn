package cn.aiditto.match;

import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.TemplateCopy;
import cn.aiditto.domain.copy.VenueOption;
import cn.aiditto.infra.json.Json;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes copy for one pair. Placeholders stay in the row and are filled in when a person reads the card.
 *
 * Step 7：默认仍是 M2 的 TemplateCopy（零回归）；开启 {@code aiditto.match.copy.enabled} 后
 * 先走 {@link AiCopyService}（网关重试 + CopyValidator 严格校验），任一环节失败立刻回落模板，
 * 并按真实结果写 {@code match_copy.source}（'ai' / 'template'）。绝不因 LLM 故障中断周三交付。
 */
@Component
public class MatchCopyWriter {
    private final MatchDb db;
    private final AiCopyService ai;
    private final AtomicInteger aiPairs = new AtomicInteger();
    private final AtomicInteger templatePairs = new AtomicInteger();
    private final AtomicInteger rejectedAfterAi = new AtomicInteger();

    @Autowired
    public MatchCopyWriter(MatchDb db, AiCopyService ai) {
        this.db = db;
        this.ai = ai;
    }

    /** 兼容旧构造（无 AI 文案，等价关闭）。 */
    public MatchCopyWriter(MatchDb db) {
        this(db, null);
    }

    public void write(long matchId, People.Member low, People.Member high) {
        if (db.hasCopy(matchId)) return;
        String place = low.placeNameZh() == null ? "见面地点" : low.placeNameZh();
        String venueId = "cafe-" + Long.toUnsignedString(matchId);
        List<VenueOption> venues = List.of(new VenueOption(venueId, place + "附近的咖啡馆", "", ""));
        CopyContext ctx = CopyContext.of(low.facts(), high.facts(), venues, java.util.Set.of());

        MatchCopy copy = null;
        String source = "template";
        String promptVersion = "template-v1";
        if (ai != null && ai.enabled()) {
            Optional<MatchCopy> candidate = ai.generate(low.facts(), high.facts(), ctx, venues);
            if (candidate.isPresent()) {
                // 纵深防御：子图已校验过，这里再跑一次，绝不把带病文案落库
                if (CopyValidator.isValid(candidate.get(), ctx)) {
                    copy = candidate.get();
                    source = "ai";
                    promptVersion = ai.promptVersion();
                } else {
                    rejectedAfterAi.incrementAndGet();
                }
            }
        }
        if (copy == null) copy = TemplateCopy.build(low.facts(), high.facts(), venues, Lang.ZH);
        List<String> issues = CopyValidator.validate(copy, ctx)
                .stream().map(Object::toString).toList();
        if (!issues.isEmpty()) throw new IllegalStateException("template copy failed: " + String.join("；", issues));

        if ("ai".equals(source)) aiPairs.incrementAndGet(); else templatePairs.incrementAndGet();
        store(matchId, low.userId(), copy.forLow(), source, promptVersion);
        store(matchId, high.userId(), copy.forHigh(), source, promptVersion);
        Map<String, Object> venue = new LinkedHashMap<>();
        venue.put("id", venueId);
        venue.put("name", place + "附近的咖啡馆");
        venue.put("address", place);
        venue.put("activity", copy.activity());
        venue.put("extra", copy.extra() == null ? "" : copy.extra());
        db.jdbc().update("UPDATE date_plan SET venue_json = ?, activity = ?, extra = ? WHERE match_id = ?",
                Json.write(venue), copy.activity(), copy.extra(), matchId);
    }

    private void store(long matchId, long viewerId, MatchCopy.ViewerCopy v, String source, String promptVersion) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        for (MatchCopy.Reason r : v.reasons()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("text", r.text());
            one.put("basis", r.basis() == null ? "" : r.basis());
            one.put("cites", r.cites());
            reasons.add(one);
        }
        db.insertCopy(matchId, viewerId, "zh", v.headline(), Json.write(v.blurb()),
                Json.write(reasons), Json.write(v.openers()), source, promptVersion);
    }

    /** 每个周批 COPY 阶段开始时清零，结束写进 week.stats，供「模板兜底率 ≤5%」闸门统计。 */
    public void resetStats() {
        aiPairs.set(0);
        templatePairs.set(0);
        rejectedAfterAi.set(0);
        if (ai != null) ai.reset();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>(ai == null ? Map.of() : ai.stats());
        int a = aiPairs.get(), t = templatePairs.get(), total = a + t;
        m.put("copyEnabled", ai != null && ai.enabled());
        m.put("copyPairs", total);
        m.put("copyAi", a);
        m.put("copyTemplate", t);
        m.put("copyTemplateRate", total == 0 ? 0.0 : Math.round(t * 10000.0 / total) / 10000.0);
        m.put("copyRejectedAfterAi", rejectedAfterAi.get());
        return m;
    }
}
