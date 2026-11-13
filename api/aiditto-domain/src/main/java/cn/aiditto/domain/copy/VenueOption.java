package cn.aiditto.domain.copy;

import java.util.Objects;

/** One of the 2–3 candidate venues the program picked for a pair (技术方案 §7.6). */
public record VenueOption(String id, String name, String defaultActivity, String defaultExtra) {
    public VenueOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        defaultActivity = defaultActivity == null ? "" : defaultActivity;
        defaultExtra = defaultExtra == null ? "" : defaultExtra;
    }
}
