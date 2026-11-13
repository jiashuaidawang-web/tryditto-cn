package cn.aiditto.domain.matching;

/** Answer to {@code educationImportance}. */
public enum Importance {
    NONE("none"),
    KINDA("kinda"),
    SUPER("super");

    private final String id;

    Importance(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static Importance fromId(String id) {
        for (Importance v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown Importance option: " + id);
    }
}
