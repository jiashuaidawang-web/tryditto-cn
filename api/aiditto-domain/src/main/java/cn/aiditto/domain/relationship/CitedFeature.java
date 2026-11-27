package cn.aiditto.domain.relationship;

/** A model candidate plus the span it claims to quote. An empty span is not a quote. */
public record CitedFeature(ExtractedFeature feature, String evidenceSpan) {
    public CitedFeature {
        if (feature == null) throw new IllegalArgumentException("feature");
        evidenceSpan = evidenceSpan == null ? "" : evidenceSpan;
    }

    public CitedFeature(ExtractedFeature feature) {
        this(feature, "");
    }
}
