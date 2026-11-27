package cn.aiditto.domain.compatibility.v4;

/** Score and coverage stay separate. A null score is undefined, not zero and not fifty. */
public record EvidenceCoverage(
        double knownWeight,
        double unknownWeight,
        double conflictWeight,
        double contestedWeight,
        Double knownScore,
        Double coverage,
        Double strongSatisfaction,
        int comparedFeatures) {

    public EvidenceCoverage {
        if (knownWeight < 0 || unknownWeight < 0 || conflictWeight < 0 || contestedWeight < 0) {
            throw new IllegalArgumentException("weight");
        }
        if (comparedFeatures < 0) throw new IllegalArgumentException("compared");
    }
}
