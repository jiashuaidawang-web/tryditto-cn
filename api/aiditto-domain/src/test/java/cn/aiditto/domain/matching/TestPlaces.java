package cn.aiditto.domain.matching;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Area ids from {@code web/src/api/places.ts} with approximate coordinates, per city pool. */
public final class TestPlaces {
    public record Place(String id, String pool, double lat, double lng) {
        public Location at(TravelMode mode, int minutes) {
            return new Location(id, lat, lng, mode, minutes);
        }
    }

    public static final Map<String, Place> ALL = new LinkedHashMap<>();

    static {
        add("pku", "bj", 39.9928, 116.3055);
        add("tsinghua", "bj", 40.0000, 116.3264);
        add("ruc", "bj", 39.9700, 116.3135);
        add("bnu", "bj", 39.9613, 116.3660);
        add("wudaokou", "bj", 39.9925, 116.3378);
        add("sanlitun", "bj", 39.9334, 116.4547);
        add("fudan", "sh", 31.2989, 121.5035);
        add("tongji", "sh", 31.2847, 121.5020);
        add("ecnu", "sh", 31.2289, 121.4062);
        add("wukang", "sh", 31.2062, 121.4372);
    }

    private TestPlaces() {}

    private static void add(String id, String pool, double lat, double lng) {
        ALL.put(id, new Place(id, pool, lat, lng));
    }

    public static Place get(String id) {
        Place p = ALL.get(id);
        if (p == null) throw new IllegalArgumentException("unknown place " + id);
        return p;
    }

    public static List<Place> inPool(String pool) {
        return ALL.values().stream().filter(p -> p.pool().equals(pool)).toList();
    }
}
