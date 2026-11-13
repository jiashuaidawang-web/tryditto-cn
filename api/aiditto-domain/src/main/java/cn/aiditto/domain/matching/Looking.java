package cn.aiditto.domain.matching;

/** One option of {@code relationshipLooking}. */
public enum Looking {
    LIFE_PARTNER("lifePartner"),
    SERIOUS("serious"),
    CASUAL("casual"),
    FRIENDS("friends"),
    NOT_SURE("notSure");

    private final String id;

    Looking(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static Looking fromId(String id) {
        for (Looking v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown Looking option: " + id);
    }
}
