package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.util.List;

/**
 * Label contract {@code f03-labels-2}. An explicit decline is not the same state as an unknown or an expiry.
 * The frozen {@code f03-synthetic-1} rows keep {@link RelationshipLabels}.
 */
public record ExplicitOutcome(Mutual y1, Meeting y2, Followup y3) {
    public static final String VERSION = "f03-labels-2";

    public enum Mutual {
        BOTH_ACCEPTED, NOT_MUTUAL, EXPIRED, IMMATURE, NO_RESPONSE, MISSING, UNAUTHORIZED
    }

    public enum Meeting {
        BOTH_ATTENDED, BOTH_ABSENT, ONE_SIDED, CONTESTED, NOT_APPLICABLE, IMMATURE, MISSING, UNAUTHORIZED
    }

    public enum Followup {
        BOTH_EXPRESSED, BOTH_DECLINED, ONE_SIDED, NOT_APPLICABLE, IMMATURE, MISSING, UNAUTHORIZED
    }

    public static ExplicitOutcome of(MatchingObservation row) {
        if (row.feedbackAuthorization() != Access.OPEN || row.retracted()) {
            return new ExplicitOutcome(Mutual.UNAUTHORIZED, Meeting.UNAUTHORIZED, Followup.UNAUTHORIZED);
        }
        return new ExplicitOutcome(mutual(row), meeting(row), followup(row));
    }

    public record Census(int published, int bothExplicit, int eitherDecline, int expired, int immature,
                         int noResponse, int missing, int unauthorized, int oldBinaryNegative, int newNotMutual) {}

    public static Census census(List<MatchingObservation> rows) {
        int bothExplicit = 0;
        int eitherDecline = 0;
        int expired = 0;
        int immature = 0;
        int noResponse = 0;
        int missing = 0;
        int unauthorized = 0;
        int oldBinaryNegative = 0;
        int newNotMutual = 0;
        for (MatchingObservation row : rows) {
            ExplicitOutcome outcome = of(row);
            if (outcome.y1 == Mutual.UNAUTHORIZED) unauthorized++;
            if (outcome.y1 == Mutual.EXPIRED) expired++;
            if (outcome.y1 == Mutual.IMMATURE) immature++;
            if (outcome.y1 == Mutual.NO_RESPONSE) noResponse++;
            if (outcome.y1 == Mutual.MISSING) missing++;
            if (outcome.y1 == Mutual.NOT_MUTUAL) newNotMutual++;
            boolean declineA = row.responseA() == Response.DECLINED;
            boolean declineB = row.responseB() == Response.DECLINED;
            boolean explicitA = declineA || row.responseA() == Response.ACCEPTED;
            boolean explicitB = declineB || row.responseB() == Response.ACCEPTED;
            if (explicitA && explicitB) bothExplicit++;
            if (declineA || declineB) eitherDecline++;
            if (RelationshipLabels.of(row).y1() == RelationshipLabels.Mutual.BOTH_DECLINED) oldBinaryNegative++;
        }
        return new Census(rows.size(), bothExplicit, eitherDecline, expired, immature, noResponse, missing,
                unauthorized, oldBinaryNegative, newNotMutual);
    }

    private static Mutual mutual(MatchingObservation row) {
        Response a = row.responseA();
        Response b = row.responseB();
        if (a == Response.DECLINED || b == Response.DECLINED) return Mutual.NOT_MUTUAL;
        if (a == Response.EXPIRED || b == Response.EXPIRED) return Mutual.EXPIRED;
        if (a == Response.ACCEPTED && b == Response.ACCEPTED) return Mutual.BOTH_ACCEPTED;
        if (row.observedAt().isBefore(row.responseDeadline())) return Mutual.IMMATURE;
        if (!row.viewed() && a != Response.ACCEPTED && b != Response.ACCEPTED) return Mutual.NO_RESPONSE;
        return Mutual.MISSING;
    }

    private static Meeting meeting(MatchingObservation row) {
        if (row.attendance() == Attendance.CONTESTED) return Meeting.CONTESTED;
        if (row.attendance() == Attendance.NOT_APPLICABLE) return Meeting.NOT_APPLICABLE;
        if (row.attendance() == Attendance.BOTH_ATTENDED) return Meeting.BOTH_ATTENDED;
        if (row.attendance() == Attendance.BOTH_ABSENT) return Meeting.BOTH_ABSENT;
        if (row.attendance() == Attendance.ONE_ATTENDED || row.attendance() == Attendance.ONE_ABSENT) {
            return Meeting.ONE_SIDED;
        }
        if (row.observedAt().isBefore(row.dateDeadline())) return Meeting.IMMATURE;
        return Meeting.MISSING;
    }

    private static Followup followup(MatchingObservation row) {
        Continue a = row.continueA();
        Continue b = row.continueB();
        if (a == Continue.NOT_APPLICABLE && b == Continue.NOT_APPLICABLE) return Followup.NOT_APPLICABLE;
        if (a == Continue.EXPRESSED && b == Continue.EXPRESSED) return Followup.BOTH_EXPRESSED;
        if (a == Continue.DECLINED && b == Continue.DECLINED) return Followup.BOTH_DECLINED;
        if (a == Continue.EXPRESSED || b == Continue.EXPRESSED || a == Continue.DECLINED || b == Continue.DECLINED) {
            return Followup.ONE_SIDED;
        }
        if (row.observedAt().isBefore(row.followupDeadline())) return Followup.IMMATURE;
        return Followup.MISSING;
    }
}
