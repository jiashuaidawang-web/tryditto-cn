package cn.aiditto.domain.recall.v4;

/**
 * Decides whether a vector write may become visible.
 * A published snapshot always has both directions. A withdraw records the generation and profile
 * watermark, so a delayed task cannot put the vectors back.
 */
public final class VectorLifecycle {
    public record State(
            long generation,
            long highWater,
            boolean opened,
            boolean authorized,
            boolean withdrawn,
            boolean published,
            String modelVersion,
            String selfDigest,
            String preferenceDigest) {
        public static State none() {
            return new State(0, -1, false, false, false, false, "", "", "");
        }
    }

    public record Publish(long generation, long profileVersion, String modelVersion, String selfDigest, String preferenceDigest) {}

    public record Withdraw(long generation, long profileVersion) {}

    public record Authorize(long generation) {}

    public record Decision(VersionedVectorIndex.Put outcome, State state) {}

    private VectorLifecycle() {}

    public static Decision publish(State state, Publish command) {
        State current = state.opened() ? state : new State(command.generation(), -1, true, true, false, false, "", "", "");
        if (command.generation() != current.generation()) return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        if (command.profileVersion() < current.highWater()) return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        if (current.withdrawn()) return new Decision(VersionedVectorIndex.Put.REJECT_WITHDRAWN, state);
        if (!current.authorized()) return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        if (current.published() && command.profileVersion() == current.highWater()) {
            boolean same = command.modelVersion().equals(current.modelVersion())
                    && command.selfDigest().equals(current.selfDigest())
                    && command.preferenceDigest().equals(current.preferenceDigest());
            return new Decision(same ? VersionedVectorIndex.Put.IDEMPOTENT : VersionedVectorIndex.Put.REJECT_CONFLICT, state);
        }
        return new Decision(VersionedVectorIndex.Put.STORED, new State(
                current.generation(), command.profileVersion(), true, true, false, true,
                command.modelVersion(), command.selfDigest(), command.preferenceDigest()));
    }

    public static Decision withdraw(State state, Withdraw command) {
        if (state.opened() && command.generation() < state.generation()) {
            return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        }
        if (state.opened() && command.generation() == state.generation()
                && command.profileVersion() < state.highWater() && state.published()) {
            return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        }
        long generation = state.opened() ? Math.max(state.generation(), command.generation()) : command.generation();
        long water = Math.max(state.highWater(), command.profileVersion());
        return new Decision(VersionedVectorIndex.Put.DELETED,
                new State(generation, water, true, false, true, false, "", "", ""));
    }

    public static Decision authorize(State state, Authorize command) {
        if (command.generation() < 1) return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        if (state.opened() && command.generation() <= state.generation()) {
            return new Decision(VersionedVectorIndex.Put.REJECT_STALE, state);
        }
        return new Decision(VersionedVectorIndex.Put.STORED, new State(
                command.generation(), state.highWater(), true, true, false, false, "", "", ""));
    }
}
