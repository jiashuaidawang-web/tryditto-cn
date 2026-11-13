package cn.aiditto.domain.matching;

/** Answer to {@code educationLevel}, ordered; {@code PREFER_NOT} has no rank. */
public enum EducationLevel {
    HIGH_SCHOOL("highSchool"),
    COLLEGE("college"),
    GRADUATE("graduate"),
    PREFER_NOT("preferNot");

    private final String id;

    EducationLevel(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static EducationLevel fromId(String id) {
        for (EducationLevel v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown EducationLevel option: " + id);
    }
}
