package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-user, per-edge, and global call caps for an offline experiment.
 * One direction is attempted at most once for a profile version. A timeout is not retried.
 */
public final class ReasoningBudget {
    public static final int MAX_ATTEMPTS = 1;

    private final int perUser;
    private final int perEdge;
    private final int global;
    private final Map<String, DirectionalReasoning> completed = new LinkedHashMap<>();
    private final Map<Long, Integer> userCalls = new LinkedHashMap<>();
    private final Map<String, Integer> edgeCalls = new LinkedHashMap<>();
    private final List<String> skips = new ArrayList<>();
    private int globalCalls;

    public ReasoningBudget(int perUser, int perEdge, int global) {
        if (perUser < 1 || perEdge < 1 || global < 1) throw new IllegalArgumentException("budget");
        this.perUser = perUser;
        this.perEdge = perEdge;
        this.global = global;
    }

    public static ReasoningBudget experiment() {
        return new ReasoningBudget(8, 2, 400);
    }

    public Admission admit(long chooser, long target, String direction, long chooserVersion, long targetVersion, String promptVersion) {
        String key = chooser + ":" + target + ":" + direction + ":" + chooserVersion + ":" + targetVersion + ":" + promptVersion;
        if (completed.containsKey(key)) return skip(key, "REPEAT");
        if (globalCalls >= global) return skip(key, "GLOBAL_CAP");
        if (userCalls.getOrDefault(chooser, 0) >= perUser) return skip(key, "USER_CAP");
        String edge = Math.min(chooser, target) + ":" + Math.max(chooser, target);
        if (edgeCalls.getOrDefault(edge, 0) >= perEdge) return skip(key, "EDGE_CAP");
        globalCalls++;
        userCalls.merge(chooser, 1, Integer::sum);
        edgeCalls.merge(edge, 1, Integer::sum);
        completed.put(key, null);
        return new Admission(true, key, "ADMIT");
    }

    public void remember(String key, DirectionalReasoning reasoning) {
        completed.put(key, reasoning);
    }

    public DirectionalReasoning cached(String key) {
        return completed.get(key);
    }

    public List<String> skips() { return List.copyOf(skips); }

    public int used() { return globalCalls; }

    public int userUsed(long userId) { return userCalls.getOrDefault(userId, 0); }

    private Admission skip(String key, String reason) {
        skips.add(key + " " + reason);
        return new Admission(false, key, reason);
    }

    public record Admission(boolean allowed, String key, String reason) {}
}
