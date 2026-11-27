package cn.aiditto.domain.compatibility.v4;

import java.util.List;

/**
 * One explicit mapping from a partner preference to the other person's own feature.
 * {@code ordinal} is empty when this registry does not define a partial-satisfaction scale.
 */
public record FeatureAlignment(String preferenceCode, String selfCode, List<String> ordinal, String domainNote) {
    public FeatureAlignment {
        if (preferenceCode == null || preferenceCode.isBlank()) throw new IllegalArgumentException("preferenceCode");
        if (selfCode == null || selfCode.isBlank()) throw new IllegalArgumentException("selfCode");
        if (domainNote == null || domainNote.isBlank()) throw new IllegalArgumentException("domainNote");
        ordinal = List.copyOf(ordinal);
    }

    public boolean allowsPartial() {
        return ordinal.size() >= 2;
    }
}
