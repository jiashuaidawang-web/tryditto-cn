package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;
import cn.aiditto.domain.feedback.v4.strategy.RelationshipLabels.Mutual;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Match counts and user counts. A later stage is not forced to be a subset of the previous one. */
public final class GroupFunnel {
    private GroupFunnel() {}

    public record Stage(String name, int matches, int users, int denominator, int missing, int contested,
                        int retracted, double authorizationCoverage) {}

    public static List<Stage> matchStages(List<MatchingObservation> rows) {
        int denominator = rows.size();
        int retracted = (int) rows.stream().filter(MatchingObservation::retracted).count();
        int contested = (int) rows.stream().filter(row -> row.attendance() == Attendance.CONTESTED).count();
        int missing = (int) rows.stream().filter(row -> row.responseA() == Response.UNKNOWN || row.responseB() == Response.UNKNOWN).count();
        long open = rows.stream().filter(row -> row.feedbackAuthorization() == Access.OPEN && !row.retracted()).count();
        double coverage = denominator == 0 ? 0 : open / (double) denominator;
        List<Stage> stages = new ArrayList<>();
        stages.add(stage("published", rows.size(), users(rows), denominator, missing, contested, retracted, coverage));
        stages.add(stage("viewed", count(rows, MatchingObservation::viewed), users(rows.stream().filter(MatchingObservation::viewed).toList()),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("responded", count(rows, GroupFunnel::anyResponse), users(filter(rows, GroupFunnel::anyResponse)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("bothResponded", count(rows, GroupFunnel::bothResponded), users(filter(rows, GroupFunnel::bothResponded)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("bothAccepted", count(rows, row -> RelationshipLabels.of(row).y1() == Mutual.BOTH_ACCEPTED),
                users(filter(rows, row -> RelationshipLabels.of(row).y1() == Mutual.BOTH_ACCEPTED)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("dateArranged", count(rows, MatchingObservation::dateArranged),
                users(filter(rows, MatchingObservation::dateArranged)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("oneReportedAttendance", count(rows, row -> row.attendance() == Attendance.ONE_ATTENDED
                        || row.attendance() == Attendance.ONE_ABSENT || row.attendance() == Attendance.BOTH_ATTENDED
                        || row.attendance() == Attendance.BOTH_ABSENT || row.attendance() == Attendance.CONTESTED),
                users(filter(rows, row -> row.attendance() != Attendance.UNKNOWN && row.attendance() != Attendance.NOT_APPLICABLE)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("bothConfirmedAttendance", count(rows, row -> row.attendance() == Attendance.BOTH_ATTENDED
                        || row.attendance() == Attendance.BOTH_ABSENT),
                users(filter(rows, row -> row.attendance() == Attendance.BOTH_ATTENDED || row.attendance() == Attendance.BOTH_ABSENT)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("oneContinue", count(rows, GroupFunnel::anyContinue), users(filter(rows, GroupFunnel::anyContinue)),
                denominator, missing, contested, retracted, coverage));
        stages.add(stage("bothContinue", count(rows, row -> row.continueA() == Continue.EXPRESSED && row.continueB() == Continue.EXPRESSED),
                users(filter(rows, row -> row.continueA() == Continue.EXPRESSED && row.continueB() == Continue.EXPRESSED)),
                denominator, missing, contested, retracted, coverage));
        return List.copyOf(stages);
    }

    private static Stage stage(String name, int matches, int users, int denominator, int missing, int contested,
                               int retracted, double coverage) {
        return new Stage(name, matches, users, denominator, missing, contested, retracted, coverage);
    }

    private static int count(List<MatchingObservation> rows, java.util.function.Predicate<MatchingObservation> test) {
        return (int) rows.stream().filter(test).count();
    }

    private static List<MatchingObservation> filter(List<MatchingObservation> rows, java.util.function.Predicate<MatchingObservation> test) {
        return rows.stream().filter(test).toList();
    }

    private static int users(List<MatchingObservation> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (MatchingObservation row : rows) {
            ids.add(row.userA());
            ids.add(row.userB());
        }
        return ids.size();
    }

    private static boolean anyResponse(MatchingObservation row) {
        return responded(row.responseA()) || responded(row.responseB());
    }

    private static boolean bothResponded(MatchingObservation row) {
        return responded(row.responseA()) && responded(row.responseB());
    }

    private static boolean responded(Response response) {
        return response == Response.ACCEPTED || response == Response.DECLINED;
    }

    private static boolean anyContinue(MatchingObservation row) {
        return row.continueA() == Continue.EXPRESSED || row.continueB() == Continue.EXPRESSED
                || row.continueA() == Continue.DECLINED || row.continueB() == Continue.DECLINED;
    }
}
