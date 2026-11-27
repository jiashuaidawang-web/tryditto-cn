package cn.aiditto.domain.compatibility.v4;

/**
 * One preference compared with the other person's own feature.
 * {@code satisfactionValue} is null for unknown, contested, and not-applicable rows.
 */
public record FeatureMatchContribution(
        String direction,
        String preferenceCode,
        String targetFeatureCode,
        String expectedValue,
        String actualValue,
        String importance,
        String operator,
        String evidenceStatus,
        Satisfaction satisfaction,
        int weight,
        Double satisfactionValue,
        String reasonCode) {

    public FeatureMatchContribution {
        if (direction == null || preferenceCode == null || satisfaction == null || reasonCode == null) {
            throw new IllegalArgumentException("contribution");
        }
        if (weight < 1) throw new IllegalArgumentException("weight");
        if (satisfaction.known() && satisfactionValue == null) throw new IllegalArgumentException("known value");
        if (!satisfaction.known() && satisfactionValue != null) throw new IllegalArgumentException("unknown is not a number");
    }
}
