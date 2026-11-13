package cn.aiditto.match;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.domain.match.MatchError;
import cn.aiditto.domain.match.MatchRuleException;

/** Maps a rejected match event onto the HTTP error catalogue (技术方案 §6.7). */
public final class MatchErrors {
    private MatchErrors() {}

    public static DomainException translate(MatchRuleException e) {
        String detail = e.getMessage();
        ErrorCode code = switch (e.code()) {
            case MATCH_NOT_FOUND -> ErrorCode.MATCH_NOT_FOUND;
            case MATCH_STATE -> ErrorCode.MATCH_STATE;
            case SLOTS_EMPTY -> ErrorCode.SLOTS_EMPTY;
            case SLOT_UNKNOWN -> ErrorCode.SLOT_UNKNOWN;
            case CHECKIN_WINDOW -> ErrorCode.CHECKIN_WINDOW;
            case FEEDBACK_EXISTS -> ErrorCode.FEEDBACK_EXISTS;
            case CATEGORY_UNKNOWN -> ErrorCode.CATEGORY_UNKNOWN;
            case DEADLINE_NOT_REACHED -> ErrorCode.MATCH_STATE;
        };
        return new DomainException(code, detail);
    }
}
