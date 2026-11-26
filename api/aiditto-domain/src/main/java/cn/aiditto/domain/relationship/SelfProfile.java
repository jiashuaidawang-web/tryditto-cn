package cn.aiditto.domain.relationship;

import java.util.List;

public record SelfProfile(List<ProfileFeature> features) {
    public SelfProfile { features = List.copyOf(features); }
    public static SelfProfile empty() { return new SelfProfile(List.of()); }
}
