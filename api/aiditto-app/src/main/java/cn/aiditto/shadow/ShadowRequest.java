package cn.aiditto.shadow;

import cn.aiditto.domain.matching.v4.ShadowResourceBudget;

import java.time.Instant;
import java.time.LocalDate;

/** One isolated shadow run. The versions are the frozen E, B5, and G2 identifiers. */
public record ShadowRequest(
        String matchingRunId,
        LocalDate matchingPeriod,
        String cohort,
        String city,
        Instant sourceCutoff,
        String candidateStrategyVersion,
        String compatibilityPolicyVersion,
        String globalObjectiveVersion,
        ShadowResourceBudget budget,
        long seed) {
}
