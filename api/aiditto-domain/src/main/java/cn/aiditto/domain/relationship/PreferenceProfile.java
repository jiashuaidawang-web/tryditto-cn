package cn.aiditto.domain.relationship;

import java.util.List;

public record PreferenceProfile(List<PreferenceTarget> targets) {
    public PreferenceProfile { targets = List.copyOf(targets); }
    public static PreferenceProfile empty() { return new PreferenceProfile(List.of()); }
}
