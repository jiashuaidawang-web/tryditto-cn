package cn.aiditto.relationship;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.relationship.FeatureRegistry;
import cn.aiditto.domain.relationship.RelationshipProfileProjector;
import cn.aiditto.domain.relationship.RelationshipProfileSnapshot;
import cn.aiditto.domain.relationship.SnapshotAdmission;
import org.springframework.stereotype.Component;

/**
 * Reads stored onboarding rows for one user and admits them through the registry.
 * Callers cannot supply a feature code, an evidence id, another user, a source version, or a consent flag.
 */
@Component
public class OnboardingEvidenceAdapter {
    private final AnswerEvidenceDirectory answers;
    private final FeatureRegistry registry = FeatureRegistry.v1();

    public OnboardingEvidenceAdapter(AnswerEvidenceDirectory answers) {
        this.answers = answers;
    }

    public RelationshipProfileSnapshot project(long userId) {
        try {
            return SnapshotAdmission.admit(
                    RelationshipProfileProjector.project(userId, answers.loadAnswers(userId), registry),
                    registry, answers);
        } catch (IllegalArgumentException ex) {
            throw DomainException.invalid("snapshot", ex.getMessage());
        }
    }
}
