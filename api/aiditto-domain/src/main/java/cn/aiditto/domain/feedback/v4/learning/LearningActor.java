package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

/**
 * The user the test has already authenticated.
 * A match participant id is not this actor, and a production session is not wired.
 */
public record LearningActor(long userId, String authority) {
    public static final String SYNTHETIC = "synthetic-test";

    public LearningActor {
        if (userId <= 0) throw new FeedbackRejection("USER");
        if (!SYNTHETIC.equals(authority)) throw new FeedbackRejection("PRODUCTION_ACTOR_NOT_WIRED");
    }

    public static LearningActor synthetic(long userId) {
        return new LearningActor(userId, SYNTHETIC);
    }
}
