package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

import java.time.Instant;

/** The three deadlines that belong to one published match. */
public record ObservationWindow(Instant responseDeadline, Instant dateDeadline, Instant followupDeadline) {
    public ObservationWindow {
        if (responseDeadline == null || dateDeadline == null || followupDeadline == null) {
            throw new FeedbackRejection("WINDOW");
        }
    }
}
