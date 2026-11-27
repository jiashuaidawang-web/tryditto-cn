package cn.aiditto.domain.recall.v4;

/** Isolated index names. The production collection is refused. */
public final class V4IndexNames {
    public static final String PRODUCTION = "match_user_v1";

    private V4IndexNames() {}

    public static void refuseUnlessIsolated(String name) {
        if (name == null || name.isBlank() || PRODUCTION.equals(name) || name.startsWith("match_user")) {
            throw new IllegalArgumentException("refusing a production vector collection");
        }
        if (!name.startsWith("v4_it_") && !name.startsWith("v4_synth_")) {
            throw new IllegalArgumentException("vector collection is not an isolated v4 index");
        }
    }
}
