package cn.aiditto.domain.slot;

import java.time.Instant;
import java.util.Objects;

/** One proposed date slot. Every slot lasts {@link Deadlines#SLOT_LENGTH}. */
public record Slot(String id, Instant startsAt) {
    public Slot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startsAt, "startsAt");
    }

    public Instant endsAt() {
        return startsAt.plus(Deadlines.SLOT_LENGTH);
    }
}
