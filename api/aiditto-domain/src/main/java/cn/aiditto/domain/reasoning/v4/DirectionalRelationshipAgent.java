package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry;

/** One direction. It cannot read the other direction's conclusion or self-reported confidence. */
public final class DirectionalRelationshipAgent {
    private final FeatureAlignmentRegistry alignments;

    public DirectionalRelationshipAgent(FeatureAlignmentRegistry alignments) {
        this.alignments = alignments;
    }

    public DirectionalReasoning analyze(RelationshipReasoningContext context, String direction, ReasoningModel model) {
        RelationshipReasoningContext.View view = context.view(direction);
        if (context.sealed()) {
            return DirectionalReasoning.refused(view.chooserId(), view.targetId(), direction, "NOT_ELIGIBLE_FOR_REASONING");
        }
        ReasoningPrompt.Rendered prompt = ReasoningPrompt.render(context, direction, alignments.features());
        ReasoningModel.ReasoningCompletion completion;
        try {
            completion = model.complete(prompt);
        } catch (RuntimeException ex) {
            return DirectionalReasoning.failed(view.chooserId(), view.targetId(), direction, "MODEL_EXCEPTION");
        }
        if (completion == null || completion.failed() || completion.content() == null || completion.content().isBlank()) {
            return DirectionalReasoning.failed(view.chooserId(), view.targetId(), direction,
                    completion == null || completion.failure() == null ? "MODEL_FAILED" : completion.failure());
        }
        return ReasoningValidator.validate(context, direction, completion.content(), alignments);
    }
}
