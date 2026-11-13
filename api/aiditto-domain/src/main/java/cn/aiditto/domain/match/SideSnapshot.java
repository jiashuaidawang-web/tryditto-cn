package cn.aiditto.domain.match;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** One person's {@code match_side} row plus their {@code feedback} row (null until submitted). */
public record SideSnapshot(
        long userId,
        Response response,
        Set<String> slotIds,
        boolean reselected,
        Instant checkedInAt,
        FeedbackAnswer feedback) {

    public SideSnapshot {
        Objects.requireNonNull(response, "response");
        slotIds = Set.copyOf(slotIds);
    }

    public static SideSnapshot pending(long userId) {
        return new SideSnapshot(userId, Response.PENDING, Set.of(), false, null, null);
    }

    public SideSnapshot withResponse(Response r) {
        return new SideSnapshot(userId, r, slotIds, reselected, checkedInAt, feedback);
    }

    public SideSnapshot withSlots(Set<String> ids, boolean reselect) {
        return new SideSnapshot(userId, Response.ACCEPT, ids, reselect, checkedInAt, feedback);
    }

    public SideSnapshot withCheckIn(Instant at) {
        return new SideSnapshot(userId, response, slotIds, reselected, at, feedback);
    }

    public SideSnapshot withFeedback(FeedbackAnswer f) {
        return new SideSnapshot(userId, response, slotIds, reselected, checkedInAt, f);
    }
}
