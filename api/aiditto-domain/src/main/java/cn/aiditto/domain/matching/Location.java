package cn.aiditto.domain.matching;

import java.util.Objects;

/** Answer to {@code location} plus the area's centre from the {@code place} table. */
public record Location(String placeId, double lat, double lng, TravelMode mode, int minutes) {
    public Location {
        Objects.requireNonNull(placeId, "placeId");
        Objects.requireNonNull(mode, "mode");
        if (minutes <= 0) throw new IllegalArgumentException("minutes must be positive");
    }
}
