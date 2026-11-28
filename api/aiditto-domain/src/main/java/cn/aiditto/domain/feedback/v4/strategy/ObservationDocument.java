package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Access;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Attendance;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Continue;
import cn.aiditto.domain.feedback.v4.strategy.MatchingObservation.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned text form of one observation. The digest covers the dataset and all three deadlines. */
public record ObservationDocument(String contractVersion, Entry entry, String datasetVersion, MatchingObservation observation) {
    public static final String VERSION = "f03-observation-2";

    public enum Entry { EVENT, FIXTURE }

    public ObservationDocument {
        if (!VERSION.equals(contractVersion) || entry == null || datasetVersion == null || datasetVersion.isBlank()
                || observation == null) {
            throw new FeedbackRejection("OBSERVATION");
        }
    }

    public String serialize() {
        MatchingObservation row = observation;
        return String.join("\n",
                "contract=" + contractVersion,
                "entry=" + entry,
                "dataset=" + datasetVersion,
                "id=" + row.observationId(),
                "match=" + row.matchId(),
                "run=" + row.matchingRunId(),
                "period=" + row.matchingPeriod(),
                "userA=" + row.userA(),
                "userB=" + row.userB(),
                "profileA=" + row.profileVersionA(),
                "profileB=" + row.profileVersionB(),
                "generationA=" + row.lifecycleGenerationA(),
                "generationB=" + row.lifecycleGenerationB(),
                "strategy=" + row.candidateStrategyVersion(),
                "policy=" + row.compatibilityPolicyVersion(),
                "objective=" + row.globalObjectiveVersion(),
                "formula=" + row.scoreFormula(),
                "scoreAB=" + row.scoreAB(),
                "scoreBA=" + row.scoreBA(),
                "coverageAB=" + row.coverageAB(),
                "coverageBA=" + row.coverageBA(),
                "reciprocal=" + row.reciprocalScore(),
                "published=" + row.publishedAt(),
                "responseDeadline=" + row.responseDeadline(),
                "dateDeadline=" + row.dateDeadline(),
                "followupDeadline=" + row.followupDeadline(),
                "responseA=" + row.responseA(),
                "responseB=" + row.responseB(),
                "viewed=" + row.viewed(),
                "arranged=" + row.dateArranged(),
                "attendance=" + row.attendance(),
                "continueA=" + row.continueA(),
                "continueB=" + row.continueB(),
                "authorization=" + row.feedbackAuthorization(),
                "retracted=" + row.retracted(),
                "sources=" + String.join(",", row.sourceEventIds()),
                "observed=" + row.observedAt());
    }

    public String digest() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(serialize().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception failure) {
            throw new FeedbackRejection("OBSERVATION");
        }
    }

    public static ObservationDocument parse(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int cut = line.indexOf('=');
            if (cut < 1) throw new FeedbackRejection("OBSERVATION");
            fields.put(line.substring(0, cut), line.substring(cut + 1));
        }
        List<String> sources = new ArrayList<>();
        if (!fields.getOrDefault("sources", "").isBlank()) {
            for (String source : fields.get("sources").split(",")) sources.add(source);
        }
        MatchingObservation observation = new MatchingObservation(need(fields, "id"), Long.parseLong(need(fields, "match")),
                need(fields, "run"), LocalDate.parse(need(fields, "period")),
                Long.parseLong(need(fields, "userA")), Long.parseLong(need(fields, "userB")),
                Integer.parseInt(need(fields, "profileA")), Integer.parseInt(need(fields, "profileB")),
                Long.parseLong(need(fields, "generationA")), Long.parseLong(need(fields, "generationB")),
                need(fields, "strategy"), need(fields, "policy"), need(fields, "objective"), need(fields, "formula"),
                Integer.parseInt(need(fields, "scoreAB")), Integer.parseInt(need(fields, "scoreBA")),
                Double.parseDouble(need(fields, "coverageAB")), Double.parseDouble(need(fields, "coverageBA")),
                Integer.parseInt(need(fields, "reciprocal")), Instant.parse(need(fields, "published")),
                Instant.parse(need(fields, "responseDeadline")), Instant.parse(need(fields, "dateDeadline")),
                Instant.parse(need(fields, "followupDeadline")),
                Response.valueOf(need(fields, "responseA")), Response.valueOf(need(fields, "responseB")),
                Boolean.parseBoolean(need(fields, "viewed")), Boolean.parseBoolean(need(fields, "arranged")),
                Attendance.valueOf(need(fields, "attendance")), Continue.valueOf(need(fields, "continueA")),
                Continue.valueOf(need(fields, "continueB")), Access.valueOf(need(fields, "authorization")),
                Boolean.parseBoolean(need(fields, "retracted")), sources, Instant.parse(need(fields, "observed")));
        return new ObservationDocument(need(fields, "contract"), Entry.valueOf(need(fields, "entry")),
                need(fields, "dataset"), observation);
    }

    private static String need(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) throw new FeedbackRejection("OBSERVATION");
        return value;
    }
}
