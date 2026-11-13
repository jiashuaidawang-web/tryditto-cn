package cn.aiditto.place;

import cn.aiditto.infra.json.Json;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Area search for the location page and /where. The table is small and static, so it is read once and kept in memory. */
@Service
public class PlaceService {
    static final int LIMIT = 8;

    /** Same shape as {@code Place} in web/src/api/places.ts; names stay bilingual (技术方案 F4: places are static data). */
    public record PlaceView(String id, Map<String, String> name, Map<String, String> sub, String kw, List<Object> nearby,
                            double density, Boolean enOnly) {}

    private final PlaceMapper places;
    private final AtomicReference<List<Place>> areas = new AtomicReference<>();

    public PlaceService(PlaceMapper places) {
        this.places = places;
    }

    public boolean isArea(String id) {
        return id != null && areas().stream().anyMatch(p -> p.id.equals(id));
    }

    public List<PlaceView> search(String query, String lang) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return List.of();
        boolean en = "en".equals(lang);
        return areas().stream()
                .filter(p -> en || !Boolean.TRUE.equals(p.enOnly))
                .filter(p -> contains(p.nameEn, q) || contains(p.nameZh, q) || contains(p.subEn, q) || contains(p.subZh, q) || contains(p.kw, q))
                .limit(LIMIT)
                .map(PlaceService::view)
                .toList();
    }

    private List<Place> areas() {
        List<Place> cached = areas.get();
        if (cached == null) {
            cached = places.selectList(new QueryWrapper<Place>().eq("kind", "area").eq("active", true).orderByAsc("sort_order"));
            areas.set(cached);
        }
        return cached;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    @SuppressWarnings("unchecked")
    private static PlaceView view(Place p) {
        List<Object> nearby = p.nearby == null ? List.of() : (List<Object>) Json.readPlain(p.nearby);
        return new PlaceView(p.id, t(p.nameEn, p.nameZh), t(p.subEn, p.subZh), p.kw, nearby,
                p.density == null ? 0 : p.density.doubleValue(), Boolean.TRUE.equals(p.enOnly) ? true : null);
    }

    private static Map<String, String> t(String en, String zh) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("en", en);
        m.put("zh", zh);
        return m;
    }
}
