package cn.aiditto.match;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;

/**
 * The pair cannot proceed. The message is the generic state error: it names neither person.
 * Transactions that mean to keep the safety hold declare {@code noRollbackFor} this type.
 */
public class EligibilityWithheld extends DomainException {
    public EligibilityWithheld() {
        super(ErrorCode.MATCH_STATE);
    }
}
