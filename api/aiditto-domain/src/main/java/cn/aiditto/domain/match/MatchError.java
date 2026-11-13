package cn.aiditto.domain.match;

/** Error codes raised by the match rules; names match 技术方案 §6.7 where a code exists there. */
public enum MatchError {
    /** The event is not allowed in the current state (HTTP 409). */
    MATCH_STATE,
    /** The acting user is not one of the two people in the match (HTTP 404). */
    MATCH_NOT_FOUND,
    SLOTS_EMPTY,
    SLOT_UNKNOWN,
    CHECKIN_WINDOW,
    FEEDBACK_EXISTS,
    CATEGORY_UNKNOWN,
    /** A deadline event arrived before its due time; only schedulers can hit this. */
    DEADLINE_NOT_REACHED
}
