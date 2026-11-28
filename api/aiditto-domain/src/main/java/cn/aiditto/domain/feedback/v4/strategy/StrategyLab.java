package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Ten replayable synthetic cases. They are not a production sample. */
public final class StrategyLab {
    private StrategyLab() {}

    public record Case(MatchingObservation observation, String reason) {}

    public static List<Case> cases() {
        Instant published = Instant.parse("2026-11-10T00:00:00Z");
        return List.of(
                one("L01", 1, "E+G2", 80, 76, Response.ACCEPTED, Response.ACCEPTED, true, true, Attendance.BOTH_ATTENDED,
                        Continue.EXPRESSED, Continue.EXPRESSED, Access.OPEN, "双方接受，见面，双方愿意继续"),
                one("L02", 2, "E+G2", 84, 20, Response.ACCEPTED, Response.DECLINED, true, false, Attendance.NOT_APPLICABLE,
                        Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, "只有一方接受，不能当成双方接受"),
                one("L03", 3, "E+G1", 40, 44, Response.DECLINED, Response.DECLINED, true, false, Attendance.NOT_APPLICABLE,
                        Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, "双方拒绝"),
                one("L04", 4, "E+G2", 70, 70, Response.UNKNOWN, Response.UNKNOWN, false, false, Attendance.UNKNOWN,
                        Continue.UNKNOWN, Continue.UNKNOWN, Access.OPEN, "窗口未到，不能标成失败", published.plusSeconds(3600)),
                one("L05", 5, "E+G3", 55, 60, Response.NO_RESPONSE, Response.NO_RESPONSE, false, false, Attendance.NOT_APPLICABLE,
                        Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, "已发布但没有人响应，和双方拒绝不是同一标签"),
                one("L06", 6, "E+G2", 61, 58, Response.EXPIRED, Response.EXPIRED, true, false, Attendance.NOT_APPLICABLE,
                        Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, "匹配超时保持为超时"),
                one("L07", 7, "E+G4", 66, 64, Response.ACCEPTED, Response.ACCEPTED, true, true, Attendance.CONTESTED,
                        Continue.UNKNOWN, Continue.UNKNOWN, Access.OPEN, "见面报告互相矛盾，不强行裁决"),
                one("L08", 8, "F", 72, 71, Response.ACCEPTED, Response.ACCEPTED, true, true, Attendance.ONE_ATTENDED,
                        Continue.EXPRESSED, Continue.UNKNOWN, Access.REVOKED, "一方撤回授权，不进入学习"),
                one("L09", 9, "E+G2", 90, 88, Response.DECLINED, Response.DECLINED, true, false, Attendance.NOT_APPLICABLE,
                        Continue.NOT_APPLICABLE, Continue.NOT_APPLICABLE, Access.OPEN, "高分双方拒绝，分数不是接受概率"),
                one("L10", 10, "M02-B5", 15, 12, Response.ACCEPTED, Response.ACCEPTED, true, true, Attendance.BOTH_ATTENDED,
                        Continue.EXPRESSED, Continue.EXPRESSED, Access.OPEN, "低分双方接受并愿意继续")
        );
    }

    public static String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\"dataset\":\"").append(StrategyDataset.VERSION).append("\",\"realCalibration\":\"")
                .append(ScoreCalibration.REAL_QUALITY).append("\",\"realLift\":\"")
                .append(StrategyLearningProposal.REAL_LIFT).append("\",\"cases\":[");
        List<Case> cases = cases();
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) out.append(',');
            MatchingObservation row = cases.get(i).observation();
            RelationshipLabels labels = RelationshipLabels.of(row);
            out.append("{\"id\":\"").append(row.observationId())
                    .append("\",\"strategy\":\"").append(row.candidateStrategyVersion())
                    .append("\",\"objective\":\"").append(row.globalObjectiveVersion())
                    .append("\",\"scoreAB\":").append(row.scoreAB())
                    .append(",\"scoreBA\":").append(row.scoreBA())
                    .append(",\"profileA\":").append(row.profileVersionA())
                    .append(",\"profileB\":").append(row.profileVersionB())
                    .append(",\"y1\":\"").append(labels.y1())
                    .append("\",\"y2\":\"").append(labels.y2())
                    .append("\",\"y3\":\"").append(labels.y3())
                    .append("\",\"learnable\":").append(labels.y1Learnable())
                    .append(",\"proposal\":\"")
                    .append(StrategyLearningProposal.from(StrategyDataset.VERSION, 0, true, row.publishedAt()).status())
                    .append("\",\"reason\":\"").append(cases.get(i).reason())
                    .append("\"}");
        }
        out.append("]}");
        return out.toString();
    }

    private static Case one(String id, int n, String strategy, int scoreAB, int scoreBA,
                            Response a, Response b, boolean viewed, boolean arranged, Attendance attendance,
                            Continue continueA, Continue continueB, Access access, String reason) {
        return one(id, n, strategy, scoreAB, scoreBA, a, b, viewed, arranged, attendance, continueA, continueB, access,
                reason, Instant.parse("2026-11-28T00:00:00Z"));
    }

    private static Case one(String id, int n, String strategy, int scoreAB, int scoreBA,
                            Response a, Response b, boolean viewed, boolean arranged, Attendance attendance,
                            Continue continueA, Continue continueB, Access access, String reason, Instant observed) {
        Instant published = Instant.parse("2026-11-10T00:00:00Z");
        MatchingObservation observation = new MatchingObservation(id, 60000L + n, "lab-run", LocalDate.of(2026, 11, 10),
                93000L + n, 94000L + n, 5, 5, 1, 1, strategy, "m02-b5", "global-objective-g2-1", "B5",
                scoreAB, scoreBA, 0.9, 0.9, Math.min(scoreAB, scoreBA), published,
                published.plusSeconds(7 * 86400L), published.plusSeconds(14 * 86400L), published.plusSeconds(21 * 86400L),
                a, b, viewed, arranged, attendance, continueA, continueB, access, false, List.of("lab-" + n), observed);
        return new Case(observation, reason);
    }

    public static void main(String[] args) throws Exception {
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]), json());
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]), StrategyDataset.json());
    }
}
