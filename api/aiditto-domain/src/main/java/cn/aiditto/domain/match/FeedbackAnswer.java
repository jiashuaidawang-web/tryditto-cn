package cn.aiditto.domain.match;

import java.util.Objects;

/** One side's feedback, mirroring the frontend {@code Feedback} type. */
public record FeedbackAnswer(Met met, Integer rating, Again again, Boolean feltSafe) {

    public enum Met {
        /** "见到了". */
        MET("met"),
        /** "对方没来". */
        NO_SHOW("noShow"),
        /** "我没去". */
        I_SKIPPED("iSkipped");

        private final String id;

        Met(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Met fromId(String id) {
            for (Met m : values()) if (m.id.equals(id)) return m;
            throw new IllegalArgumentException("unknown met: " + id);
        }
    }

    public enum Again {
        YES, NO, UNSURE;

        public String id() {
            return name().toLowerCase();
        }

        public static Again fromId(String id) {
            return valueOf(id.toUpperCase());
        }
    }

    public FeedbackAnswer {
        Objects.requireNonNull(met, "met");
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("rating must be 1-5");
        }
    }

    public static FeedbackAnswer met(Again again) {
        return new FeedbackAnswer(Met.MET, null, again, true);
    }

    public static FeedbackAnswer of(Met met) {
        return new FeedbackAnswer(met, null, null, true);
    }

    public boolean wantsAgain() {
        return met == Met.MET && again == Again.YES;
    }

    public boolean reportsUnsafe() {
        return Boolean.FALSE.equals(feltSafe);
    }
}
