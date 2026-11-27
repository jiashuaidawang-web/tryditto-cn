package cn.aiditto.domain.feedback.v4;

import java.util.Set;

/** Closed vocabularies. A reason code is the person's chosen category, not a measured trait. */
public final class FeedbackFacts {
    public static final String SCHEMA = "feedback-event-1";
    public static final Set<String> REASONS = Set.of(
            "COMMON_INTEREST", "COMMUNICATION_STYLE", "LIFESTYLE_PREFERENCE", "RELATIONSHIP_EXPECTATION",
            "SCHEDULING_DIFFICULTY", "NO_ROMANTIC_INTEREST", "INSUFFICIENT_INFORMATION", "OTHER", "PREFER_NOT_TO_SAY");

    public enum Direction { A_TO_B, B_TO_A, JOINT }

    public enum SourceType { SYSTEM, PARTICIPANT }

    public enum EvidenceStatus { SYSTEM_VERIFIED, SELF_REPORTED, MUTUAL_CONFIRMED, CONTESTED, UNKNOWN }

    public enum ResponseState { UNKNOWN, NOT_APPLICABLE, NOT_OCCURRED, ACCEPTED, DECLINED, EXPIRED, WITHHELD }

    public enum AttendanceState {
        UNKNOWN, NOT_APPLICABLE, NOT_OCCURRED,
        ONE_REPORTED_ATTENDED, BOTH_REPORTED_ATTENDED,
        ONE_REPORTED_NOT_ATTENDED, BOTH_REPORTED_NOT_ATTENDED,
        CONTESTED, WITHHELD
    }

    public enum ContinueState { UNKNOWN, NOT_APPLICABLE, EXPRESSED, DECLINED, WITHHELD }

    public enum ScheduleState { UNKNOWN, NOT_OCCURRED, SCHEDULED, CANCELLED }

    private FeedbackFacts() {}
}
