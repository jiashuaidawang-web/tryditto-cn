package cn.aiditto.domain.relationship;

import java.util.List;

public record MemoryProfile(List<MemoryObservation> observations) {
    public MemoryProfile { observations = List.copyOf(observations); }
    public static MemoryProfile empty() { return new MemoryProfile(List.of()); }
}
