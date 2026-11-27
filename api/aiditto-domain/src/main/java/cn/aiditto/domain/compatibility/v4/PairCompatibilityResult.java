package cn.aiditto.domain.compatibility.v4;

import java.util.List;

/**
 * Published bidirectional result. Rankable numbers are null when a hard constraint fails
 * or either direction has no defined known score. Explanations may still list the rows.
 */
public record PairCompatibilityResult(
        long userA,
        long userB,
        long profileVersionA,
        long profileVersionB,
        long generationA,
        long generationB,
        List<String> hardFilterResult,
        Double scoreAB,
        Double scoreBA,
        Double coverageAB,
        Double coverageBA,
        Double arithmeticMean,
        Double harmonicMean,
        Double minDirection,
        Double geometricMean,
        Double harmonicWithCoverage,
        Double harmonicWithCoverageAndStrength,
        List<String> strongPreferenceConflicts,
        List<String> unknownFeatures,
        List<FeatureMatchContribution> evidenceContributions,
        String formulaVersion,
        String registryVersion,
        String alignmentVersion,
        CompatibilityStatus status,
        DirectionalCompatibilityResult explanationAB,
        DirectionalCompatibilityResult explanationBA) {

    public PairCompatibilityResult {
        hardFilterResult = List.copyOf(hardFilterResult);
        strongPreferenceConflicts = List.copyOf(strongPreferenceConflicts);
        unknownFeatures = List.copyOf(unknownFeatures);
        evidenceContributions = List.copyOf(evidenceContributions);
        if (formulaVersion == null || registryVersion == null || alignmentVersion == null || status == null) {
            throw new IllegalArgumentException("pair result");
        }
    }
}
