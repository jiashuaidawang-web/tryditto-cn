package cn.aiditto.domain.matching;

/** Answer to {@code heightRange}. When {@code any} is true the bounds are ignored. */
public record HeightPref(boolean any, int min, int max) {
    public static final HeightPref ANY = new HeightPref(true, 0, 0);

    public HeightPref {
        if (!any && min > max) throw new IllegalArgumentException("min > max");
    }

    public static HeightPref between(int min, int max) {
        return new HeightPref(false, min, max);
    }

    public boolean accepts(int heightCm) {
        return any || (heightCm >= min && heightCm <= max);
    }
}
