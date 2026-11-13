package cn.aiditto.domain.matching;

/** Answer to {@code gender}. {@code OTHER} only connects to people who chose {@code everyone}. */
public enum Gender {
    MAN("man"),
    WOMAN("woman"),
    NONBINARY("nonbinary"),
    OTHER("other");

    private final String id;

    Gender(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static Gender fromId(String id) {
        for (Gender v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown Gender option: " + id);
    }
}
