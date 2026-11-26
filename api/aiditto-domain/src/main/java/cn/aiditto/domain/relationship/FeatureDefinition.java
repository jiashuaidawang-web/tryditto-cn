package cn.aiditto.domain.relationship;

import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.RelationshipTypes.Section;
import cn.aiditto.domain.relationship.RelationshipTypes.Sensitivity;
import cn.aiditto.domain.relationship.RelationshipTypes.ValueType;

import java.util.Set;

/** One versioned feature. The registry is the only place a code becomes legal. */
public record FeatureDefinition(
        String featureCode,
        String displayName,
        ValueType valueType,
        Set<String> allowedValues,
        Double min,
        Double max,
        Set<Section> sections,
        Set<EvidenceSource> allowedSources,
        Sensitivity sensitivity,
        int schemaVersion) {

    public FeatureDefinition {
        if (featureCode == null || featureCode.isBlank()) throw new IllegalArgumentException("featureCode");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName");
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
        sections = Set.copyOf(sections);
        allowedSources = Set.copyOf(allowedSources);
        if (sections.isEmpty() || allowedSources.isEmpty()) throw new IllegalArgumentException("feature has no section or source");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion");
        if (min != null && (!Double.isFinite(min) || (max != null && min > max))) throw new IllegalArgumentException("range");
        if (max != null && !Double.isFinite(max)) throw new IllegalArgumentException("range");
    }

    public boolean allows(Section section, EvidenceSource source) {
        return sections.contains(section) && allowedSources.contains(source);
    }
}
