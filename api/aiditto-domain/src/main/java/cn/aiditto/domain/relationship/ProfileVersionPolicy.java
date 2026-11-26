package cn.aiditto.domain.relationship;

/**
 * Separates the live content snapshot from the highest source version already processed.
 * A same-hash delivery advances the watermark and does not insert another snapshot.
 * A later delivery from an older source is rejected even when the live row still shows
 * the first source version.
 */
public final class ProfileVersionPolicy {
    private ProfileVersionPolicy() {}

    public enum WriteMode { DETERMINISTIC, MODEL_REVISION }

    public enum Kind {
        KEEP_CURRENT,
        INSERT,
        ACCEPT_REVISION,
        REJECT_INACTIVE,
        REJECT_UNAUTHORIZED,
        REJECT_GENERATION,
        REJECT_UNVERIFIED_SOURCE,
        REJECT_STALE,
        REJECT_CONFLICT
    }

    public record Decision(Kind kind, int watermark) {}

    public static Decision decide(boolean accountActive, boolean processingGranted, long expectedGeneration,
                                  long actualGeneration, int watermark, int actualSource, int claimedSource,
                                  boolean liveCurrent, String currentHash, String incomingHash, WriteMode mode) {
        if (!accountActive) return new Decision(Kind.REJECT_INACTIVE, watermark);
        if (!processingGranted) return new Decision(Kind.REJECT_UNAUTHORIZED, watermark);
        if (expectedGeneration != actualGeneration) return new Decision(Kind.REJECT_GENERATION, watermark);
        if (claimedSource != actualSource) return new Decision(Kind.REJECT_UNVERIFIED_SOURCE, watermark);
        if (claimedSource < watermark) return new Decision(Kind.REJECT_STALE, watermark);
        if (liveCurrent && incomingHash.equals(currentHash)) return new Decision(Kind.KEEP_CURRENT, claimedSource);
        if (liveCurrent && claimedSource == watermark && mode == WriteMode.DETERMINISTIC) {
            return new Decision(Kind.REJECT_CONFLICT, watermark);
        }
        if (liveCurrent && claimedSource == watermark && mode == WriteMode.MODEL_REVISION) {
            return new Decision(Kind.ACCEPT_REVISION, watermark);
        }
        return new Decision(Kind.INSERT, claimedSource);
    }
}
