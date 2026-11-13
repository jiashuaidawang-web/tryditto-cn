package cn.aiditto.domain.matching;

/** Answer to {@code matchingStyle}, ordered from least to most selective. */
public enum MatchingStyle {
    FASTER("faster"),
    STEADY("steady"),
    FEWER("fewer"),
    THE_ONE("theOne");

    private final String id;

    MatchingStyle(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static MatchingStyle fromId(String id) {
        for (MatchingStyle v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown MatchingStyle option: " + id);
    }
}
