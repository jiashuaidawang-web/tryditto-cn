package cn.aiditto.domain.relationship;

import java.util.List;

public record BehaviorProfile(List<BehaviorRecord> records) {
    public BehaviorProfile { records = List.copyOf(records); }
    public static BehaviorProfile empty() { return new BehaviorProfile(List.of()); }
}
