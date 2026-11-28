package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen synthetic observations. The label is chosen by the case, not by the score. */
public final class StrategyDataset {
    public static final String VERSION = "f03-synthetic-1";
    public static final LocalDate DEV_PERIOD = LocalDate.of(2026, 11, 1);
    public static final LocalDate HOLDOUT_PERIOD = LocalDate.of(2026, 11, 20);
    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    private StrategyDataset() {}

    public enum Kind {
        BOTH_ACCEPT, ONE_ACCEPT, BOTH_DECLINE, IMMATURE, NO_RESPONSE, EXPIRED, CONTESTED, WITHHELD,
        PROFILE_PINNED, DUPLICATE_EVENTS, REPEAT_USER, STRATEGY_SHIFT, HIGH_SCORE_DECLINE, LOW_SCORE_ACCEPT,
        SCORE_DISAGREES, PUBLISHED_ONLY
    }

    public static List<MatchingObservation> observations() {
        List<MatchingObservation> rows = new ArrayList<>();
        Map<String, MatchingObservation> byMatch = new LinkedHashMap<>();
        int n = 0;
        for (Kind kind : Kind.values()) {
            for (int copy = 0; copy < 8; copy++) {
                boolean holdout = copy >= 6;
                MatchingObservation row = row(n, kind, holdout);
                String key = row.matchId() + ":" + row.matchingRunId();
                if (byMatch.put(key, row) != null) throw new FeedbackRejection("DUPLICATE_MATCH");
                rows.add(row);
                n++;
            }
        }
        return List.copyOf(rows);
    }

    public static boolean development(MatchingObservation row) {
        return row.userA() < 92000 && row.userB() < 92000 && row.matchingPeriod().equals(DEV_PERIOD);
    }

    public static String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\"dataset\":\"").append(VERSION).append("\",\"rows\":[");
        List<MatchingObservation> rows = observations();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) out.append(',');
            MatchingObservation row = rows.get(i);
            RelationshipLabels labels = RelationshipLabels.of(row);
            out.append("{\"id\":\"").append(row.observationId())
                    .append("\",\"split\":\"").append(development(row) ? "dev" : "holdout")
                    .append("\",\"strategy\":\"").append(row.candidateStrategyVersion())
                    .append("\",\"period\":\"").append(row.matchingPeriod())
                    .append("\",\"profileA\":").append(row.profileVersionA())
                    .append(",\"scoreAB\":").append(row.scoreAB())
                    .append(",\"scoreBA\":").append(row.scoreBA())
                    .append(",\"reciprocal\":").append(row.reciprocalScore())
                    .append(",\"y1\":\"").append(labels.y1())
                    .append("\",\"y2\":\"").append(labels.y2())
                    .append("\",\"y3\":\"").append(labels.y3())
                    .append("\",\"sources\":").append(row.sourceEventIds().size())
                    .append('}');
        }
        out.append("]}");
        return out.toString();
    }

    public static void rejectDuplicate(List<MatchingObservation> rows, MatchingObservation extra) {
        for (MatchingObservation row : rows) {
            if (row.matchId() == extra.matchId() && row.matchingRunId().equals(extra.matchingRunId())) {
                throw new FeedbackRejection("DUPLICATE_MATCH");
            }
        }
    }

    private static MatchingObservation row(int n, Kind kind, boolean holdout) {
        long userA = holdout ? 92001 + (n % 8) : 91001 + (n % 24);
        long userB = holdout ? 92011 + (n % 6) : 91040 + (n % 12);
        LocalDate period = holdout ? HOLDOUT_PERIOD : DEV_PERIOD;
        Instant published = T0.plusSeconds(n * 3600L);
        int score = 20 + (n * 7) % 70;
        int other = 25 + (n * 5) % 60;
        Response responseA = Response.UNKNOWN;
        Response responseB = Response.UNKNOWN;
        boolean viewed = true;
        Attendance attendance = Attendance.NOT_APPLICABLE;
        Continue continueA = Continue.NOT_APPLICABLE;
        Continue continueB = Continue.NOT_APPLICABLE;
        Access access = Access.OPEN;
        boolean retracted = false;
        boolean arranged = false;
        int profileA = 2;
        Instant observed = published.plusSeconds(20 * 24 * 3600L);
        Instant responseDeadline = published.plusSeconds(7 * 24 * 3600L);
        Instant dateDeadline = published.plusSeconds(14 * 24 * 3600L);
        Instant followDeadline = published.plusSeconds(21 * 24 * 3600L);
        List<String> sources = List.of("evt-" + n);
        switch (kind) {
            case BOTH_ACCEPT -> {
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
                arranged = true;
                attendance = Attendance.BOTH_ATTENDED;
                continueA = Continue.EXPRESSED;
                continueB = Continue.EXPRESSED;
            }
            case ONE_ACCEPT -> {
                responseA = Response.ACCEPTED;
                responseB = Response.DECLINED;
            }
            case BOTH_DECLINE -> {
                responseA = Response.DECLINED;
                responseB = Response.DECLINED;
            }
            case IMMATURE -> {
                observed = published.plusSeconds(3600);
                viewed = false;
            }
            case NO_RESPONSE -> viewed = false;
            case EXPIRED -> {
                responseA = Response.EXPIRED;
                responseB = Response.EXPIRED;
            }
            case CONTESTED -> {
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
                arranged = true;
                attendance = Attendance.CONTESTED;
            }
            case WITHHELD -> {
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
                access = Access.REVOKED;
            }
            case PROFILE_PINNED -> {
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
                arranged = true;
                profileA = 4;
            }
            case DUPLICATE_EVENTS -> {
                responseA = Response.ACCEPTED;
                responseB = Response.DECLINED;
                sources = List.of("evt-" + n, "evt-" + n + "-repeat");
            }
            case REPEAT_USER -> {
                userA = holdout ? 92001 : 91001;
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
            }
            case STRATEGY_SHIFT -> {
                responseA = n % 2 == 0 ? Response.ACCEPTED : Response.DECLINED;
                responseB = Response.DECLINED;
            }
            case HIGH_SCORE_DECLINE -> {
                score = 95;
                other = 93;
                responseA = Response.DECLINED;
                responseB = Response.DECLINED;
            }
            case LOW_SCORE_ACCEPT -> {
                score = 12;
                other = 18;
                responseA = Response.ACCEPTED;
                responseB = Response.ACCEPTED;
                arranged = true;
                attendance = Attendance.BOTH_ATTENDED;
                continueA = Continue.EXPRESSED;
                continueB = Continue.EXPRESSED;
            }
            case SCORE_DISAGREES -> {
                score = n % 2 == 0 ? 88 : 15;
                other = score;
                responseA = score > 50 ? Response.DECLINED : Response.ACCEPTED;
                responseB = responseA;
            }
            case PUBLISHED_ONLY -> {
                responseA = Response.ACCEPTED;
                responseB = Response.UNKNOWN;
                observed = published.plusSeconds(10 * 24 * 3600L);
            }
        }
        String strategy = StrategyComparison.PUBLISHED[n % StrategyComparison.PUBLISHED.length];
        int reciprocal = Math.min(score, other);
        return new MatchingObservation("obs-" + n, 50000L + n, "run-" + (holdout ? "hold" : "dev"), period,
                userA, userB, profileA, 3, 1, 1, strategy, "m02-b5", "global-objective-g2-1", "B5",
                score, other, 0.8, 0.7, reciprocal, published, responseDeadline, dateDeadline, followDeadline,
                responseA, responseB, viewed, arranged, attendance, continueA, continueB, access, retracted, sources, observed);
    }
}
