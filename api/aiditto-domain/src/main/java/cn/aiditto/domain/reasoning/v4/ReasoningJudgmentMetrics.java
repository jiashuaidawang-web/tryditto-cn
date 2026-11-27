package cn.aiditto.domain.reasoning.v4;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-judgment scores. A repeated claim does not create another true positive.
 * Dev and holdout are counted apart. These gates are not lowered after a run.
 */
public final class ReasoningJudgmentMetrics {
    private ReasoningJudgmentMetrics() {}

    public record Split(String name, int judgments, int structuredLegal, int structuredAttempted,
                        int directionCorrect, int directionAttempted,
                        int refsLegal, int refsSeen, int refsRelevant,
                        int supportTp, int supportPredicted, int supportExpected,
                        int unsupportedClaims, int emittedClaims, int unsupportedUsable, int usableClaims,
                        int duplicateClaims,
                        int hypothesisConfused, int hypothesisExpected,
                        int mustRejectAccepted, int mustRejectExpected) {
        public Double supportPrecision() { return ratio(supportTp, supportPredicted); }
        public Double supportRecall() { return ratio(supportTp, supportExpected); }
        public Double unsupportedRate() { return ratio(unsupportedClaims, emittedClaims); }
        public Double unsupportedUsableRate() { return ratio(unsupportedUsable, usableClaims); }
        public Double duplicateRate() { return ratio(duplicateClaims, usableClaims); }
        public Double hypothesisConfusion() { return ratio(hypothesisConfused, hypothesisExpected); }
    }

    public record Report(Split dev, Split holdout) {}

    public static Report evaluate(List<ReasoningChallenge.Case> cases, List<ReasoningExperiment.PairResult> results) {
        return new Report(count(cases, results, "dev"), count(cases, results, "holdout"));
    }

    private static Split count(List<ReasoningChallenge.Case> cases, List<ReasoningExperiment.PairResult> results, String split) {
        int judgments = 0;
        int legal = 0;
        int attempted = 0;
        int directionCorrect = 0;
        int directionAttempted = 0;
        int refsLegal = 0;
        int refsSeen = 0;
        int refsRelevant = 0;
        int supportTp = 0;
        int supportPredicted = 0;
        int supportExpected = 0;
        int unsupported = 0;
        int emitted = 0;
        int unsupportedUsable = 0;
        int usable = 0;
        int duplicates = 0;
        int confused = 0;
        int hypotheses = 0;
        int mustRejectAccepted = 0;
        int mustRejectExpected = 0;
        Set<String> truePositives = new HashSet<>();
        for (int i = 0; i < cases.size(); i++) {
            ReasoningChallenge.Case item = cases.get(i);
            if (!split.equals(item.split())) continue;
            ReasoningExperiment.PairResult result = results.get(i);
            for (String expected : List.of("AB", "BA")) {
                DirectionalReasoning reasoning = "AB".equals(expected) ? result.agentA() : result.agentB();
                attempted++;
                if (reasoning.status() == ReasoningStatus.COMPLETED || reasoning.status() == ReasoningStatus.REFUSED) legal++;
                if (reasoning.status() != ReasoningStatus.REFUSED) {
                    directionAttempted++;
                    if (reasoning.status() == ReasoningStatus.COMPLETED && expected.equals(reasoning.direction())) directionCorrect++;
                }
                Set<String> seen = new HashSet<>();
                List<ReasoningClaim> all = new ArrayList<>(reasoning.usable());
                all.addAll(reasoning.unsupportedClaims());
                all.addAll(reasoning.experimentFindings());
                emitted += all.size();
                usable += reasoning.usable().size();
                for (ReasoningClaim claim : reasoning.unsupportedClaims()) unsupported++;
                for (ReasoningClaim claim : reasoning.usable()) {
                    if (claim.category() == ReasoningCategory.UNSUPPORTED_INFERENCE) unsupportedUsable++;
                    String key = reasoning.direction() + "|" + claim.preferenceCode() + "|" + claim.targetFeatureCode() + "|" + claim.category();
                    if (!seen.add(key)) duplicates++;
                    refsSeen += claim.evidenceRefs().size();
                    for (String ref : claim.evidenceRefs()) {
                        if (result.context().view(reasoning.direction()).allows(ref)) refsLegal++;
                    }
                    if (ReasoningValidator.relevance(result.context().view(reasoning.direction()), claim) == EvidenceRelevance.RELEVANT) {
                        refsRelevant++;
                    }
                    if (claim.category() == ReasoningCategory.SUPPORTED_COMPATIBILITY) supportPredicted++;
                }
            }
            for (ReasoningChallenge.Judgment judgment : item.judgments()) {
                judgments++;
                DirectionalReasoning reasoning = "AB".equals(judgment.direction()) ? result.agentA() : result.agentB();
                if (judgment.disposition() == ReasoningChallenge.Disposition.SUPPORT
                        && judgment.expectedCategory() == ReasoningCategory.SUPPORTED_COMPATIBILITY) {
                    supportExpected++;
                    String key = judgment.relationId() + "|" + judgment.direction() + "|" + judgment.preferenceCode() + "|" + judgment.targetFeatureCode();
                    if (truePositives.add(key) && matches(judgment, reasoning)) supportTp++;
                }
                if (judgment.disposition() == ReasoningChallenge.Disposition.HYPOTHESIS) {
                    hypotheses++;
                    if (reasoning.supportedMatches().stream().anyMatch(claim -> sameCodes(judgment, claim))) confused++;
                }
                if (judgment.disposition() == ReasoningChallenge.Disposition.MUST_REJECT) {
                    mustRejectExpected++;
                    if (reasoning.usable().stream().anyMatch(claim -> sameCodes(judgment, claim)
                            && claim.category() != ReasoningCategory.UNSUPPORTED_INFERENCE)) mustRejectAccepted++;
                }
            }
        }
        return new Split(split, judgments, legal, attempted, directionCorrect, directionAttempted,
                refsLegal, refsSeen, refsRelevant, supportTp, supportPredicted, supportExpected,
                unsupported, emitted, unsupportedUsable, usable, duplicates, confused, hypotheses,
                mustRejectAccepted, mustRejectExpected);
    }

    private static boolean matches(ReasoningChallenge.Judgment judgment, DirectionalReasoning reasoning) {
        return reasoning.usable().stream().anyMatch(claim -> sameCodes(judgment, claim)
                && (claim.category() == judgment.expectedCategory() || judgment.acceptableAlternatives().contains(claim.category())));
    }

    private static boolean sameCodes(ReasoningChallenge.Judgment judgment, ReasoningClaim claim) {
        return judgment.preferenceCode().equals(claim.preferenceCode()) && judgment.targetFeatureCode().equals(claim.targetFeatureCode());
    }

    public static Double ratio(int numerator, int denominator) {
        if (denominator <= 0) return null;
        return numerator / (double) denominator;
    }
}
