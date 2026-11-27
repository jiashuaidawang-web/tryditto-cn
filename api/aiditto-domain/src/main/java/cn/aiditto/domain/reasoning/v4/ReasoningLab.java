package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.FeatureMatchContribution;

import java.util.ArrayList;
import java.util.List;

/** Eight fictional cases. The reasoning comes from the offline fixture, not from a live model. */
public final class ReasoningLab {
    public static void main(String[] args) {
        System.out.print(json());
    }

    private ReasoningLab() {}

    public static String json() {
        ReasoningExperiment experiment = ReasoningExperiment.v1();
        RuleEchoModel model = new RuleEchoModel();
        ReasoningBudget budget = ReasoningBudget.experiment();
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"promptVersion\": \"").append(ReasoningPrompt.VERSION).append("\"");
        out.append(",\n  \"promptHash\": \"").append(ReasoningPrompt.hash()).append("\"");
        out.append(",\n  \"datasetHash\": \"").append(ReasoningDataset.hash()).append("\"");
        out.append(",\n  \"policy\": \"").append(ReasoningExperiment.POLICY).append("\"");
        out.append(",\n  \"fixtureModel\": \"").append(RuleEchoModel.NAME).append("\"");
        out.append(",\n  \"realModel\": \"NOT_RUN\"");
        out.append(",\n  \"cases\": [\n");
        List<ReasoningDataset.Kind> kinds = List.of(
                ReasoningDataset.Kind.MUTUAL, ReasoningDataset.Kind.ONE_WAY, ReasoningDataset.Kind.SHARED_CONFLICT,
                ReasoningDataset.Kind.COMPLEMENT, ReasoningDataset.Kind.MISSING, ReasoningDataset.Kind.CONTRADICTION,
                ReasoningDataset.Kind.MALICIOUS, ReasoningDataset.Kind.HARD_VETO);
        for (int i = 0; i < kinds.size(); i++) {
            ReasoningDataset.Relation relation = find(kinds.get(i));
            CompatibilityContext context = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                    BidirectionalCompatibilityEngine.NOW, List.of(relation.seeker(), relation.candidate()));
            ReasoningExperiment.PairResult result = experiment.reason(relation.seeker(), relation.candidate(), context, model, budget);
            if (i > 0) out.append(",\n");
            write(out, relation, result);
        }
        out.append("\n  ]\n}\n");
        return out.toString();
    }

    private static ReasoningDataset.Relation find(ReasoningDataset.Kind kind) {
        return ReasoningDataset.all().stream()
                .filter(relation -> relation.kind() == kind && "dev".equals(relation.split()) && relation.copy() == 0)
                .findFirst().orElseThrow();
    }

    private static void write(StringBuilder out, ReasoningDataset.Relation relation, ReasoningExperiment.PairResult result) {
        out.append("    {\n      \"id\": \"").append(relation.id()).append("\"");
        out.append(",\n      \"kind\": \"").append(relation.kind()).append("\"");
        out.append(",\n      \"note\": \"").append(relation.note()).append("\"");
        out.append(",\n      \"profileA\": ").append(profile(relation.seeker()));
        out.append(",\n      \"profileB\": ").append(profile(relation.candidate()));
        out.append(",\n      \"maliciousTextInPrompt\": ").append(result.context().sealed()
                ? "false" : String.valueOf(promptContainsMalicious(result)));
        out.append(",\n      \"m02\": {");
        out.append("\"scoreAB\": ").append(num(result.decision().pair().scoreAB()));
        out.append(", \"scoreBA\": ").append(num(result.decision().pair().scoreBA()));
        out.append(", \"coverageAB\": ").append(num(result.decision().pair().coverageAB()));
        out.append(", \"coverageBA\": ").append(num(result.decision().pair().coverageBA()));
        out.append(", \"items\": ").append(items(result.decision().pair().evidenceContributions()));
        out.append("}");
        out.append(",\n      \"agentA\": ").append(agent(result.agentA()));
        out.append(",\n      \"agentB\": ").append(agent(result.agentB()));
        out.append(",\n      \"sharedFits\": ").append(shared(result));
        out.append(",\n      \"potentialConflicts\": ").append(codes(result, ReasoningCategory.POTENTIAL_CONFLICT));
        out.append(",\n      \"evidenceGaps\": ").append(codes(result, ReasoningCategory.INSUFFICIENT_EVIDENCE));
        out.append(",\n      \"validation\": {\"agentA\": \"").append(result.agentA().status())
                .append("\", \"agentB\": \"").append(result.agentB().status())
                .append("\", \"unsupportedA\": ").append(result.agentA().unsupportedClaims().size())
                .append(", \"unsupportedB\": ").append(result.agentB().unsupportedClaims().size()).append("}");
        out.append(",\n      \"rankBefore\": ").append(num(result.schemeA().experimentalScore()));
        out.append(",\n      \"rankAfter\": ").append(num(result.schemeC().experimentalScore()));
        out.append(",\n      \"schemeC\": \"").append(result.schemeC().status()).append("\"");
        out.append(",\n      \"judgement\": \"").append(relation.note()).append("\"");
        out.append("\n    }");
    }

    private static boolean promptContainsMalicious(ReasoningExperiment.PairResult result) {
        String ab = ReasoningPrompt.render(result.context(), "AB",
                cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry.v1().features()).user();
        String ba = ReasoningPrompt.render(result.context(), "BA",
                cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry.v1().features()).user();
        return ab.contains(ReasoningDataset.MALICIOUS_TEXT) || ba.contains(ReasoningDataset.MALICIOUS_TEXT);
    }

    private static String profile(cn.aiditto.domain.compatibility.v4.CompatibilitySubject person) {
        List<String> rows = new ArrayList<>();
        person.self().features().forEach(feature -> rows.add("{\"role\":\"self\",\"code\":\"" + feature.featureCode()
                + "\",\"value\":\"" + RelationshipReasoningContext.text(feature.value()) + "\"}"));
        person.preference().targets().forEach(target -> rows.add("{\"role\":\"preference\",\"code\":\"" + target.featureCode()
                + "\",\"value\":\"" + RelationshipReasoningContext.text(target.targetValue()) + "\"}"));
        return "[" + String.join(",", rows) + "]";
    }

    private static String items(List<FeatureMatchContribution> rows) {
        List<String> out = new ArrayList<>();
        for (FeatureMatchContribution row : rows) {
            out.add("{\"direction\":\"" + row.direction() + "\",\"preference\":\"" + row.preferenceCode()
                    + "\",\"satisfaction\":\"" + row.satisfaction() + "\"}");
        }
        return "[" + String.join(",", out) + "]";
    }

    private static String agent(DirectionalReasoning reasoning) {
        return "{\"status\":\"" + reasoning.status() + "\",\"supported\":" + reasoning.supportedMatches().size()
                + ",\"complements\":" + reasoning.potentialComplements().size()
                + ",\"conflicts\":" + reasoning.possibleConflicts().size()
                + ",\"missing\":" + reasoning.missingInformation().size()
                + ",\"contradictions\":" + reasoning.contradictions().size() + "}";
    }

    private static String shared(ReasoningExperiment.PairResult result) {
        List<String> codes = new ArrayList<>();
        for (ReasoningClaim left : result.agentA().supportedMatches()) {
            boolean other = result.agentB().supportedMatches().stream()
                    .anyMatch(claim -> claim.targetFeatureCode().equals(targetOf(left.preferenceCode())));
            if (other) codes.add("\"" + left.preferenceCode() + "\"");
        }
        return "[" + String.join(",", codes) + "]";
    }

    private static String targetOf(String preference) {
        return preference.replace("partner.", "self.");
    }

    private static String codes(ReasoningExperiment.PairResult result, ReasoningCategory category) {
        List<String> codes = new ArrayList<>();
        for (ReasoningClaim claim : result.agentA().usable()) if (claim.category() == category) codes.add("\"AB:" + claim.preferenceCode() + "\"");
        for (ReasoningClaim claim : result.agentB().usable()) if (claim.category() == category) codes.add("\"BA:" + claim.preferenceCode() + "\"");
        return "[" + String.join(",", codes) + "]";
    }

    private static String num(Double value) {
        return value == null ? "null" : Double.toString(value);
    }
}
