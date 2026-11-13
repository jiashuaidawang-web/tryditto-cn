package cn.aiditto.match;

import cn.aiditto.domain.copy.CopyContext;
import cn.aiditto.domain.copy.CopyValidator;
import cn.aiditto.domain.copy.Lang;
import cn.aiditto.domain.copy.MatchCopy;
import cn.aiditto.domain.copy.TemplateCopy;
import cn.aiditto.domain.copy.VenueOption;
import cn.aiditto.infra.json.Json;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes template copy for one pair. Placeholders stay in the row and are filled in when a person reads the card. */
@Component
public class MatchCopyWriter {
    private final MatchDb db;

    public MatchCopyWriter(MatchDb db) {
        this.db = db;
    }

    public void write(long matchId, People.Member low, People.Member high) {
        if (db.hasCopy(matchId)) return;
        String place = low.placeNameZh() == null ? "见面地点" : low.placeNameZh();
        String venueId = "cafe-" + Long.toUnsignedString(matchId);
        List<VenueOption> venues = List.of(new VenueOption(venueId, place + "附近的咖啡馆", "", ""));
        MatchCopy copy = TemplateCopy.build(low.facts(), high.facts(), venues, Lang.ZH);
        List<String> issues = CopyValidator.validate(copy, CopyContext.of(low.facts(), high.facts(), venues, java.util.Set.of()))
                .stream().map(Object::toString).toList();
        if (!issues.isEmpty()) throw new IllegalStateException("template copy failed: " + String.join("；", issues));
        store(matchId, low.userId(), copy.forLow());
        store(matchId, high.userId(), copy.forHigh());
        Map<String, Object> venue = new LinkedHashMap<>();
        venue.put("id", venueId);
        venue.put("name", place + "附近的咖啡馆");
        venue.put("address", place);
        venue.put("activity", copy.activity());
        venue.put("extra", copy.extra() == null ? "" : copy.extra());
        db.jdbc().update("UPDATE date_plan SET venue_json = ?, activity = ?, extra = ? WHERE match_id = ?",
                Json.write(venue), copy.activity(), copy.extra(), matchId);
    }

    private void store(long matchId, long viewerId, MatchCopy.ViewerCopy v) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        for (MatchCopy.Reason r : v.reasons()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("text", r.text());
            one.put("basis", r.basis() == null ? "" : r.basis());
            one.put("cites", r.cites());
            reasons.add(one);
        }
        db.insertCopy(matchId, viewerId, "zh", v.headline(), Json.write(v.blurb()), Json.write(reasons), Json.write(v.openers()));
    }
}
