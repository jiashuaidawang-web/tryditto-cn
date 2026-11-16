package cn.aiditto.domain.geo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 本地几何等时圈网格（不依赖任何外部地图服务 / 不内置任何 key / 不做地图渲染）。
 *
 * 目的：把「通勤可达范围」表达为一组离散格网编号 iso_cells，供 Qdrant payload 与 PG
 * 结构化路共用同一套硬过滤语义，替代 Step 5 里「place_id 单点近似」的偏离。
 *
 * 设计要点（面向 C 端学生的谨慎取舍）：
 *  - 纯本地 Haversine + 交通方式速度估算可达半径，不调用任何地图/路况 API，
 *    因此不触碰地图合规红线：不引海外地图源、不内置 key、不渲染地图、不展示他人坐标。
 *  - 输出到向量库的只是**格网编号字符串**（如 c11640_3990），不含经纬度、
 *    不含地址、不含任何个人位置数据，满足位置信息最小化。
 *  - 半径按「步行/骑行/公交/地铁/驾车」分档并**保守封顶**，宁可少召回也不误召回：
 *    学生无车场景默认公交；半径上限避免跨城误配。
 *  - 网格边长固定，双方各自展开成 cell 集合，「有交集」即判定通勤圈重叠。
 *    比单点 place_id 更贴近 Ditto 的 isochrone 语义（可达范围相交，而非同一地点）。
 *  - 所有计算确定性，无运行时随机，仿真可复现。
 */
public final class IsoCellGrid {

    /**
     * 全局固定网格边长（度）。0.02° ≈ 2.2km 纬度方向。
     * 必须全局固定且所有用户共用同一套编号，否则双方 cell 集合不可比、交集判断失效。
     * 配合 8km 半径封顶，单个用户典型展开 40–80 个 cell，不触发截断、Qdrant match-any 开销可控。
     */
    public static final double CELL_DEG = 0.02;

    /** 可达半径上限（km）。校园/同城约会场景保守封顶，防止异常 travel_minutes 造成跨城误召回。 */
    public static final double MAX_RADIUS_KM = 8.0;

    /** 单个用户展开的 cell 数上限（保险丝）。正常情况下不会触发。 */
    public static final int MAX_CELLS = 256;

    /**
     * 通配 cell：用户没有地理信息时写入该值，查询侧也总是带上它。
     * 保证「无地理信息不施加约束」在 Qdrant match-any 与 PG overlaps 两侧语义一致，
     * 不会让缺坐标的用户在向量路被静默淘汰、在结构化路又被保留。
     */
    public static final String WILDCARD = "cANY";

    private static final double EARTH_KM = 6371.0088;

    private IsoCellGrid() {}

    /** 交通方式 → 保守平均速度（km/h）。未知方式按公交处理。 */
    public static double speedKmh(String travelMode) {
        if (travelMode == null) return 18.0;
        return switch (travelMode.trim().toLowerCase(Locale.ROOT)) {
            case "walk", "walking", "foot", "步行" -> 4.5;
            case "bike", "cycling", "bicycle", "骑行" -> 12.0;
            case "bus", "transit", "公交", "公共交通" -> 15.0;
            case "metro", "subway", "rail", "地铁" -> 25.0;
            case "drive", "car", "taxi", "驾车", "打车" -> 22.0;
            default -> 18.0;
        };
    }

    /** 通勤分钟 → 可达半径（km），含 0.75 的保守折减（等时圈小于直线半径）。 */
    public static double radiusKm(String travelMode, Integer travelMinutes) {
        int minutes = travelMinutes == null ? 30 : Math.max(5, Math.min(90, travelMinutes));
        double raw = speedKmh(travelMode) * (minutes / 60.0) * 0.75;
        return Math.min(MAX_RADIUS_KM, Math.max(1.0, raw));
    }

    /**
     * 以 (lat,lng) 为圆心、按通勤可达半径展开成格网编号集合。
     * 坐标为空/非法时返回空集（表示「不施加地理约束」，绝不代表「 anywhere 都匹配」——
     * 调用方必须区分「无约束」与「有约束但零交集」，见 overlaps 的语义）。
     */
    public static List<String> cells(Double lat, Double lng, String travelMode, Integer travelMinutes) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)) return List.of();
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return List.of();
        double radius = radiusKm(travelMode, travelMinutes);
        double latSpan = radius / 111.32;
        double cos = Math.max(0.1, Math.cos(Math.toRadians(lat)));
        double lngSpan = radius / (111.32 * cos);

        int minI = (int) Math.floor((lat - latSpan) / CELL_DEG);
        int maxI = (int) Math.floor((lat + latSpan) / CELL_DEG);
        int minJ = (int) Math.floor((lng - lngSpan) / CELL_DEG);
        int maxJ = (int) Math.floor((lng + lngSpan) / CELL_DEG);

        // 先按到圆心的距离收集，若真触发上限则做**对称**截断（取最近的），
        // 绝不按遍历顺序截断导致方向性偏差、同城用户误判不可达。
        List<double[]> inCircle = new ArrayList<>();
        for (int i = minI; i <= maxI; i++) {
            for (int j = minJ; j <= maxJ; j++) {
                double cellLat = (i + 0.5) * CELL_DEG;
                double cellLng = (j + 0.5) * CELL_DEG;
                double d = haversineKm(lat, lng, cellLat, cellLng);
                if (d <= radius + CELL_DEG * 111.32) inCircle.add(new double[]{i, j, d});
            }
        }
        if (inCircle.size() > MAX_CELLS) {
            inCircle.sort(java.util.Comparator.comparingDouble(a -> a[2]));
            inCircle = new ArrayList<>(inCircle.subList(0, MAX_CELLS));
        }
        Set<String> out = new LinkedHashSet<>();
        for (double[] c : inCircle) out.add(cellId((int) c[0], (int) c[1]));
        return new ArrayList<>(out);
    }

    /**
     * 写入 / 查询统一入口：无地理信息时返回通配 cell，保证两侧都「不施加约束」。
     * 返回列表恒非空，调用方无需再区分空集语义。
     */
    public static List<String> cellsOrWildcard(Double lat, Double lng, String travelMode,
                                               Integer travelMinutes) {
        List<String> c = cells(lat, lng, travelMode, travelMinutes);
        return c.isEmpty() ? List.of(WILDCARD) : c;
    }

    /** 单点的归属 cell（供「同一地点」快速判定与分页/缓存键复用）。 */
    public static String cellId(Double lat, Double lng) {
        if (lat == null || lng == null) return "";
        return cellId((int) Math.floor(lat / CELL_DEG), (int) Math.floor(lng / CELL_DEG));
    }

    public static String cellId(int i, int j) {
        return "c" + i + "_" + j;
    }

    /** Haversine 大圆距离（km）。 */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * 查询侧 cell 集合：有地理信息时在自己的格网上追加通配 cell，
     * 这样「候选无地理信息」在 Qdrant match-any 下也能被放行，与 PG 侧一致。
     * 自己无地理信息时返回空集 = 完全不施加地理约束（由调用方跳过 filter）。
     */
    public static List<String> queryCells(Double lat, Double lng, String travelMode,
                                          Integer travelMinutes) {
        List<String> c = cells(lat, lng, travelMode, travelMinutes);
        if (c.isEmpty()) return List.of(); // 自己无地理约束
        List<String> out = new ArrayList<>(c);
        out.add(WILDCARD);                 // 放行无地理信息的候选
        return out;
    }

    /**
     * 通勤圈是否重叠（对称语义，供纯函数测试与离线对照使用）。
     * 任一侧为空 = 不施加地理约束；任一侧含通配 cell = 该侧无地理信息，放行。
     */
    public static boolean overlaps(Collection<String> a, Collection<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return true;
        if (a.contains(WILDCARD) || b.contains(WILDCARD)) return true;
        return intersects(a, b);
    }

    /**
     * 在线匹配语义，与 Qdrant 的 {@code iso_cells match-any} 严格等价：
     *  - 查询侧为空 = 自己没有地理信息，不施加约束，放行全部；
     *  - 候选侧含通配 = 候选没有地理信息，被查询侧的通配值命中，放行；
     *  - 否则两侧真实格网求交集。
     * 注意：查询侧含通配**不**等于放行所有候选（它只匹配同样带通配的候选点），
     * 这是与对称 overlaps 的关键区别，混淆会让跨城候选被误召回。
     */
    public static boolean matches(Collection<String> queryCells, Collection<String> candidateCells) {
        if (queryCells == null || queryCells.isEmpty()) return true;
        if (candidateCells == null || candidateCells.isEmpty()) return true;
        if (candidateCells.contains(WILDCARD)) return true;
        return intersects(queryCells, candidateCells);
    }

    private static boolean intersects(Collection<String> a, Collection<String> b) {
        Set<String> small = a.size() <= b.size() ? new HashSet<>(a) : new HashSet<>(b);
        Collection<String> large = a.size() <= b.size() ? b : a;
        for (String s : large) if (small.contains(s)) return true;
        return false;
    }
}
