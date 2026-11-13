package cn.aiditto.domain.matching;

/** One option of {@code expectedGender} (multi; {@code everyone} is exclusive). */
public enum GenderPref {
    MEN("men"),
    WOMEN("women"),
    NONBINARY("nonbinary"),
    EVERYONE("everyone");

    private final String id;

    GenderPref(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static GenderPref fromId(String id) {
        for (GenderPref v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown GenderPref option: " + id);
    }
}
