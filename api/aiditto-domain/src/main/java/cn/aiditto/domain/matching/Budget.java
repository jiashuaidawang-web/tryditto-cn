package cn.aiditto.domain.matching;

/** Answer to {@code firstDateBudget}, ordered; {@code NO_PREF} matches every tier. */
public enum Budget {
    UNDER_20("under20"),
    FROM_20_TO_50("20to50"),
    FROM_50_TO_100("50to100"),
    OVER_100("100plus"),
    NO_PREF("noPref");

    private final String id;

    Budget(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static Budget fromId(String id) {
        for (Budget v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown Budget option: " + id);
    }
}
