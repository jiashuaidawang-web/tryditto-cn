package cn.aiditto.domain.feedback.v4;

/** A refused command. The message is a stable code, not a personality label. */
public final class FeedbackRejection extends RuntimeException {
    public FeedbackRejection(String code) {
        super(code);
    }

    public String code() {
        return getMessage();
    }
}
