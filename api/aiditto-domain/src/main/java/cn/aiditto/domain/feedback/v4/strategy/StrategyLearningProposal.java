package cn.aiditto.domain.feedback.v4.strategy;

import cn.aiditto.domain.feedback.v4.FeedbackRejection;

import java.time.Instant;

/**
 * An offline proposal. Calling {@link #activate()} does not change a scorer or a solver weight.
 */
public record StrategyLearningProposal(
        String proposalId,
        String basePolicyVersion,
        String candidatePolicyVersion,
        String evaluationDatasetVersion,
        String applicablePopulation,
        String targetMetric,
        String proposedChange,
        String expectedBenefit,
        String observedOfflineBenefit,
        String evidenceLimitations,
        String safetyCheck,
        String qualityTradeoff,
        Status status,
        Instant createdAt) {

    public enum Status { INSUFFICIENT_DATA, EXPERIMENTAL, EVALUATED, REJECTED, APPROVAL_REQUIRED }

    public static final String REAL_LIFT = "NOT_IDENTIFIED";
    public static final String EXPERIMENT = "NOT_STARTED";

    public StrategyLearningProposal {
        if (proposalId == null || proposalId.isBlank() || status == null || createdAt == null) {
            throw new FeedbackRejection("PROPOSAL");
        }
        if (basePolicyVersion == null || candidatePolicyVersion == null || evaluationDatasetVersion == null) {
            throw new FeedbackRejection("PROPOSAL");
        }
    }

    public StrategyLearningProposal activate() {
        throw new FeedbackRejection("STRATEGY_NOT_ACTIVATED");
    }

    public static StrategyLearningProposal from(String datasetVersion, int learnable, boolean safetyPassed, Instant createdAt) {
        Status status;
        if (!safetyPassed) status = Status.REJECTED;
        else if (learnable < 30) status = Status.INSUFFICIENT_DATA;
        else status = Status.EVALUATED;
        return new StrategyLearningProposal("strategy-proposal-1", "global-objective-g2-1", "global-objective-g2-1",
                datasetVersion, "synthetic-published-matches", "Y1",
                "no weight change", "not estimated", "synthetic only",
                "no counterfactual outcomes for unpublished pairs",
                safetyPassed ? "hard constraints unchanged" : "safety check failed",
                "not measured on real users", status, createdAt);
    }
}
