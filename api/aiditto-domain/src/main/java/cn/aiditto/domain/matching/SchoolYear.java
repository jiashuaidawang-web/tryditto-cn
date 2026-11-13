package cn.aiditto.domain.matching;

/** Answer to {@code schoolYear} (students only), ordered by seniority; {@code OTHER} has no position. */
public enum SchoolYear {
    FRESHMAN("freshman"),
    SOPHOMORE("sophomore"),
    JUNIOR("junior"),
    SENIOR("senior"),
    MASTER("master"),
    PHD("phd"),
    OTHER("other");

    private final String id;

    SchoolYear(String id) {
        this.id = id;
    }

    /** Option id as used by {@code web/src/onboarding/questions.ts}. */
    public String id() {
        return id;
    }

    public static SchoolYear fromId(String id) {
        for (SchoolYear v : values()) if (v.id.equals(id)) return v;
        throw new IllegalArgumentException("unknown SchoolYear option: " + id);
    }
}
