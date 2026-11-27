package cn.aiditto.domain.compatibility.v4;

import java.util.List;

/** One direction: the chooser's preferences against the target's own features. */
public record DirectionalCompatibilityResult(
        long chooserId,
        long targetId,
        String direction,
        Double score,
        Double coverage,
        EvidenceCoverage evidence,
        List<FeatureMatchContribution> contributions,
        int withheldOmitted,
        int notAdmitted,
        CompatibilityStatus status) {

    public DirectionalCompatibilityResult {
        contributions = List.copyOf(contributions);
        if (direction == null || evidence == null || status == null) throw new IllegalArgumentException("direction result");
    }
}
