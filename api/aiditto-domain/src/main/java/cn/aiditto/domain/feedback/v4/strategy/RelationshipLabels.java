package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.time.Instant;

/**
 * Three outcomes. They are not folded into one relationship-success bit.
 * Unknown, expired, immature, withheld, and contested stay themselves.
 */
public record RelationshipLabels(Mutual y1, AttendanceLabel y2, ContinueLabel y3) {
    public enum Mutual {
        UNKNOWN, IMMATURE, NO_RESPONSE, EXPIRED, ONE_ACCEPTED, BOTH_ACCEPTED, BOTH_DECLINED, WITHHELD, NOT_APPLICABLE
    }

    public enum AttendanceLabel {
        UNKNOWN, NOT_APPLICABLE, IMMATURE, ONE_ATTENDED, BOTH_ATTENDED, ONE_ABSENT, BOTH_ABSENT, CONTESTED, WITHHELD
    }

    public enum ContinueLabel {
        UNKNOWN, NOT_APPLICABLE, IMMATURE, ONE_EXPRESSED, BOTH_EXPRESSED, ONE_DECLINED, BOTH_DECLINED, WITHHELD
    }

    public static RelationshipLabels of(MatchingObservation observation) {
        if (observation.feedbackAuthorization() != Access.OPEN || observation.retracted()) {
            return new RelationshipLabels(Mutual.WITHHELD, AttendanceLabel.WITHHELD, ContinueLabel.WITHHELD);
        }
        return new RelationshipLabels(mutual(observation), attendance(observation), cont(observation));
    }

    public boolean y1Learnable() {
        return y1 == Mutual.BOTH_ACCEPTED || y1 == Mutual.BOTH_DECLINED || y1 == Mutual.ONE_ACCEPTED;
    }

    public boolean y1Binary() {
        return y1 == Mutual.BOTH_ACCEPTED || y1 == Mutual.BOTH_DECLINED;
    }

    public boolean y2Learnable() {
        return y2 == AttendanceLabel.BOTH_ATTENDED || y2 == AttendanceLabel.BOTH_ABSENT
                || y2 == AttendanceLabel.ONE_ATTENDED || y2 == AttendanceLabel.ONE_ABSENT;
    }

    public boolean y3Learnable() {
        return y3 == ContinueLabel.BOTH_EXPRESSED || y3 == ContinueLabel.BOTH_DECLINED
                || y3 == ContinueLabel.ONE_EXPRESSED || y3 == ContinueLabel.ONE_DECLINED;
    }

    private static Mutual mutual(MatchingObservation observation) {
        Instant now = observation.observedAt();
        Response a = observation.responseA();
        Response b = observation.responseB();
        if (a == Response.UNKNOWN || b == Response.UNKNOWN) {
            if (now.isBefore(observation.responseDeadline())) return Mutual.IMMATURE;
            if (!observation.viewed()) return Mutual.NO_RESPONSE;
            if (a == Response.EXPIRED || b == Response.EXPIRED) return Mutual.EXPIRED;
            return Mutual.UNKNOWN;
        }
        if (a == Response.EXPIRED || b == Response.EXPIRED) return Mutual.EXPIRED;
        if (a == Response.NO_RESPONSE && b == Response.NO_RESPONSE) return Mutual.NO_RESPONSE;
        boolean acceptA = a == Response.ACCEPTED;
        boolean acceptB = b == Response.ACCEPTED;
        boolean declineA = a == Response.DECLINED;
        boolean declineB = b == Response.DECLINED;
        if (acceptA && acceptB) return Mutual.BOTH_ACCEPTED;
        if (declineA && declineB) return Mutual.BOTH_DECLINED;
        if (acceptA || acceptB) return Mutual.ONE_ACCEPTED;
        if (now.isBefore(observation.responseDeadline())) return Mutual.IMMATURE;
        return Mutual.UNKNOWN;
    }

    private static AttendanceLabel attendance(MatchingObservation observation) {
        if (observation.attendance() == Attendance.CONTESTED) return AttendanceLabel.CONTESTED;
        if (observation.attendance() == Attendance.NOT_APPLICABLE) return AttendanceLabel.NOT_APPLICABLE;
        if (observation.attendance() == Attendance.UNKNOWN) {
            if (observation.observedAt().isBefore(observation.dateDeadline())) return AttendanceLabel.IMMATURE;
            return AttendanceLabel.UNKNOWN;
        }
        return switch (observation.attendance()) {
            case ONE_ATTENDED -> AttendanceLabel.ONE_ATTENDED;
            case BOTH_ATTENDED -> AttendanceLabel.BOTH_ATTENDED;
            case ONE_ABSENT -> AttendanceLabel.ONE_ABSENT;
            case BOTH_ABSENT -> AttendanceLabel.BOTH_ABSENT;
            default -> AttendanceLabel.UNKNOWN;
        };
    }

    private static ContinueLabel cont(MatchingObservation observation) {
        Continue a = observation.continueA();
        Continue b = observation.continueB();
        if (a == Continue.NOT_APPLICABLE && b == Continue.NOT_APPLICABLE) return ContinueLabel.NOT_APPLICABLE;
        if (a == Continue.UNKNOWN || b == Continue.UNKNOWN) {
            if (observation.observedAt().isBefore(observation.followupDeadline())) return ContinueLabel.IMMATURE;
            if (a == Continue.UNKNOWN && b == Continue.UNKNOWN) return ContinueLabel.UNKNOWN;
        }
        boolean expressedA = a == Continue.EXPRESSED;
        boolean expressedB = b == Continue.EXPRESSED;
        boolean declinedA = a == Continue.DECLINED;
        boolean declinedB = b == Continue.DECLINED;
        if (expressedA && expressedB) return ContinueLabel.BOTH_EXPRESSED;
        if (declinedA && declinedB) return ContinueLabel.BOTH_DECLINED;
        if (expressedA || expressedB) return ContinueLabel.ONE_EXPRESSED;
        if (declinedA || declinedB) return ContinueLabel.ONE_DECLINED;
        return ContinueLabel.UNKNOWN;
    }
}
