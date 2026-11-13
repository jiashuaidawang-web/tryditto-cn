package cn.aiditto.domain.match;

/** Whether the viewer takes part in this week's batch; only consulted when they have no match. */
public enum ParticipationStatus {
    ACTIVE,
    /** "暂停本周". */
    PAUSED,
    /** No-show penalty or urgent report pending review. */
    SUSPENDED
}
