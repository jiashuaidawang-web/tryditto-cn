package cn.aiditto.domain.relationship;

import java.util.List;

/**
 * The slice of a registry feature that a model is allowed to propose.
 * Examples describe the feature. They are not added to the closed phrase table.
 */
public record FeatureContract(
        String featureCode,
        String displayName,
        String semanticDefinition,
        List<String> allowedSections,
        String valueType,
        List<String> allowedValues,
        List<String> allowedEvidenceKinds,
        List<String> permittedSourceTypes,
        List<String> explicitExamples,
        List<String> counterExamples) {

    public FeatureContract {
        allowedSections = List.copyOf(allowedSections);
        allowedValues = List.copyOf(allowedValues);
        allowedEvidenceKinds = List.copyOf(allowedEvidenceKinds);
        permittedSourceTypes = List.copyOf(permittedSourceTypes);
        explicitExamples = List.copyOf(explicitExamples);
        counterExamples = List.copyOf(counterExamples);
    }
}
