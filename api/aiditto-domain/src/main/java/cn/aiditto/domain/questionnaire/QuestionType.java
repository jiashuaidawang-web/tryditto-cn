package cn.aiditto.domain.questionnaire;

/** Mirrors {@code QuestionType} in web/src/onboarding/types.ts. */
public enum QuestionType {
    SINGLE("single"),
    MULTI("multi"),
    TEXT("text"),
    LONG_TEXT("longText"),
    DATE("date"),
    AGE_RANGE("ageRange"),
    HEIGHT("height"),
    HEIGHT_RANGE("heightRange"),
    INTERSTITIAL("interstitial"),
    EMAIL("email"),
    PHOTOS("photos"),
    CODE("code"),
    LOCATION("location");

    private final String wire;

    QuestionType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static QuestionType fromWire(String s) {
        for (QuestionType t : values()) {
            if (t.wire.equals(s)) return t;
        }
        throw new IllegalArgumentException("unknown question type: " + s);
    }
}
