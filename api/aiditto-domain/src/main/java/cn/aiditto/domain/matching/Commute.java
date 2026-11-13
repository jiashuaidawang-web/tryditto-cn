package cn.aiditto.domain.matching;

/** Straight-line distance between area centres and the estimated travel time to the midpoint (技术方案 §7.2 rule 5). */
public final class Commute {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Commute() {}

    public static double distanceKm(Location a, Location b) {
        if (a.placeId().equals(b.placeId())) return 0;
        double p1 = Math.toRadians(a.lat());
        double p2 = Math.toRadians(b.lat());
        double dp = p2 - p1;
        double dl = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Minutes for {@code who} to reach the midpoint between the two areas, with the detour factor. */
    public static double minutesToMidpoint(Location who, Location other, Weights w) {
        double km = distanceKm(who, other) / 2 * w.detourFactor();
        return km / w.speedKmh(who.mode()) * 60;
    }
}
