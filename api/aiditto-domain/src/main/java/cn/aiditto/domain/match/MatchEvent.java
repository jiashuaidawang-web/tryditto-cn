package cn.aiditto.domain.match;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Everything that can happen to a match. User events carry the acting user; deadline events don't. */
public sealed interface MatchEvent {

    /** Events performed by one of the two people. */
    sealed interface ByUser extends MatchEvent {
        long userId();
    }

    /** Fired by the deadline ticker once {@code now} has reached the matching due time. */
    sealed interface Deadline extends MatchEvent {}

    /** Batch publish step. {@code respondDue} is normally {@code Deadlines.respondDue(publishAt)}. */
    record Publish(Instant respondDue) implements MatchEvent {
        public Publish {
            Objects.requireNonNull(respondDue, "respondDue");
        }
    }

    record Decline(long userId) implements ByUser {}

    /** "Go + these slots" in {@code published}, or the one allowed reselect in {@code no_overlap}. */
    record SubmitSlots(long userId, Set<String> slotIds) implements ByUser {
        public SubmitSlots {
            slotIds = slotIds == null ? Set.of() : Set.copyOf(slotIds);
        }
    }

    /** "我到了". Side-level only; the match stays {@code confirmed}. */
    record CheckIn(long userId) implements ByUser {}

    /** M3 optional: cancel a confirmed date. */
    record Cancel(long userId) implements ByUser {}

    record SubmitFeedback(long userId, FeedbackAnswer feedback) implements ByUser {
        public SubmitFeedback {
            Objects.requireNonNull(feedback, "feedback");
        }
    }

    record Report(long userId, ReportCategory category) implements ByUser {
        public Report {
            Objects.requireNonNull(category, "category");
        }
    }

    /** {@code respond_due} reached. */
    record RespondDeadline() implements Deadline {}

    /** {@code reselect_due} reached. */
    record ReselectDeadline() implements Deadline {}

    /** {@code slot_end} reached. */
    record DateEnded() implements Deadline {}

    /** {@code feedback_due} reached. */
    record FeedbackDeadline() implements Deadline {}
}
