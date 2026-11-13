package cn.aiditto.domain.matching;

/** {@code location.mode}. */
public enum TravelMode {
    WALK("walk"),
    TRANSIT("transit"),
    DRIVE("drive");

    private final String id;

    TravelMode(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static TravelMode fromId(String id) {
        for (TravelMode v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown TravelMode option: " + id);
    }
}
