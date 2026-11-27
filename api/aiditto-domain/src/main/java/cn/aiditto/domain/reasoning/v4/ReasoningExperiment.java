package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilityDecision;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * M02 stays the deterministic baseline. Scheme C may move a score by at most ten points,
 * and only after validation, and only when the M02 recommendation gate already passed.
 */
public final class ReasoningExperiment {
    public static final String POLICY = "reasoning-experiment-1";
    public static final int MAX_ADJUSTMENT = 10;
    public static final int STEP = 2;
    public static final double EVIDENCE_PRECISION_GATE = 0.90;
    public static final double UNSUPPORTED_USABLE_GATE = 0.02;

    private final FeatureAlignmentRegistry alignments;
    private final BidirectionalCompatibilityEngine engine;
    private final DirectionalRelationshipAgent agent;

    public ReasoningExperiment(FeatureAlignmentRegistry alignments) {
        this.alignments = alignments;
        this.engine = new BidirectionalCompatibilityEngine(alignments);
        this.agent = new DirectionalRelationshipAgent(alignments);
    }

    public static ReasoningExperiment v1() {
        return new ReasoningExperiment(FeatureAlignmentRegistry.v1());
    }

    public PairResult reason(CompatibilitySubject left, CompatibilitySubject right, CompatibilityContext context,
                             ReasoningModel model, ReasoningBudget budget) {
        CompatibilityDecision decision = engine.decide(left, right, context);
        RelationshipReasoningContext snapshot = RelationshipReasoningContext.from(left, right, decision, alignments);
        DirectionalReasoning ab = one(snapshot, "AB", left, right, model, budget);
        DirectionalReasoning ba = one(snapshot, "BA", right, left, model, budget);
        Double m02 = decision.pair().harmonicWithCoverage();
        int delta = adjustment(ab, ba);
        return new PairResult(decision, snapshot, ab, ba,
                rank("A", m02, 0, false, "M02_ONLY"),
                rank("B", m02, 0, false, explanationStatus(ab, ba)),
                schemeC(decision, m02, delta, ab, ba));
    }

    public List<PairResult> runFair(List<CompatibilitySubject> left, List<CompatibilitySubject> right,
                                    List<CompatibilityContext> contexts, ReasoningModel model, ReasoningBudget budget) {
        List<CompatibilityDecision> decisions = new ArrayList<>();
        List<RelationshipReasoningContext> snapshots = new ArrayList<>();
        List<DirectionalReasoning> agentA = new ArrayList<>();
        List<DirectionalReasoning> agentB = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            CompatibilityDecision decision = engine.decide(left.get(i), right.get(i), contexts.get(i));
            RelationshipReasoningContext snapshot = RelationshipReasoningContext.from(left.get(i), right.get(i), decision, alignments);
            decisions.add(decision);
            snapshots.add(snapshot);
            agentA.add(null);
            agentB.add(null);
        }
        List<Integer> pending = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            pending.add(i);
            pending.add(i);
        }
        boolean[] abTurn = new boolean[left.size()];
        while (!pending.isEmpty()) {
            pending.sort(Comparator
                    .comparingInt((Integer i) -> budget.userUsed(left.get(i).userId()))
                    .thenComparingLong(i -> left.get(i).userId())
                    .thenComparing(i -> abTurn[i]));
            int index = pending.remove(0);
            boolean second = abTurn[index];
            abTurn[index] = true;
            if (!second) agentA.set(index, one(snapshots.get(index), "AB", left.get(index), right.get(index), model, budget));
            else agentB.set(index, one(snapshots.get(index), "BA", right.get(index), left.get(index), model, budget));
        }
        List<PairResult> out = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            Double m02 = decisions.get(i).pair().harmonicWithCoverage();
            int delta = adjustment(agentA.get(i), agentB.get(i));
            out.add(new PairResult(decisions.get(i), snapshots.get(i), agentA.get(i), agentB.get(i),
                    rank("A", m02, 0, false, "M02_ONLY"),
                    rank("B", m02, 0, false, explanationStatus(agentA.get(i), agentB.get(i))),
                    schemeC(decisions.get(i), m02, delta, agentA.get(i), agentB.get(i))));
        }
        return out;
    }

    private DirectionalReasoning one(RelationshipReasoningContext snapshot, String direction,
                                     CompatibilitySubject chooser, CompatibilitySubject target,
                                     ReasoningModel model, ReasoningBudget budget) {
        if (snapshot.sealed()) return agent.analyze(snapshot, direction, model);
        ReasoningBudget.Admission admission = budget.admit(chooser.userId(), target.userId(), direction,
                chooser.profileVersion(), target.profileVersion(), snapshot.promptVersion());
        if (!admission.allowed()) {
            if ("REPEAT".equals(admission.reason()) && budget.cached(admission.key()) != null) {
                return budget.cached(admission.key());
            }
            RelationshipReasoningContext.View view = snapshot.view(direction);
            return DirectionalReasoning.refused(view.chooserId(), view.targetId(), direction, admission.reason());
        }
        DirectionalReasoning reasoning = agent.analyze(snapshot, direction, model);
        budget.remember(admission.key(), reasoning);
        return reasoning;
    }

    private static Rank schemeC(CompatibilityDecision decision, Double m02, int delta,
                                DirectionalReasoning ab, DirectionalReasoning ba) {
        if (failed(ab) || failed(ba)) return rank("C", m02, 0, false, "MODEL_FAILED");
        if (!decision.reasoningEligible() || m02 == null || !decision.recommendationEligible()) {
            return rank("C", m02, 0, false, "GATE_HELD");
        }
        return rank("C", m02, delta, true, "ADJUSTED");
    }

    private static int adjustment(DirectionalReasoning ab, DirectionalReasoning ba) {
        int raw = STEP * (count(ab, ReasoningCategory.SUPPORTED_COMPATIBILITY) + count(ba, ReasoningCategory.SUPPORTED_COMPATIBILITY))
                - STEP * (count(ab, ReasoningCategory.POTENTIAL_CONFLICT) + count(ba, ReasoningCategory.POTENTIAL_CONFLICT));
        return Math.max(-MAX_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, raw));
    }

    private static int count(DirectionalReasoning reasoning, ReasoningCategory category) {
        return (int) reasoning.usable().stream().filter(claim -> claim.category() == category).count();
    }

    private static boolean failed(DirectionalReasoning reasoning) {
        return reasoning.status() == ReasoningStatus.MODEL_FAILED;
    }

    private static String explanationStatus(DirectionalReasoning ab, DirectionalReasoning ba) {
        if (failed(ab) || failed(ba)) return "MODEL_FAILED";
        if (ab.status() == ReasoningStatus.REFUSED || ba.status() == ReasoningStatus.REFUSED) return "M02_ONLY";
        return "EXPLAINED";
    }

    private static Rank rank(String scheme, Double m02, int delta, boolean applied, String status) {
        Double experimental = m02 == null ? null : applied ? m02 + delta : m02;
        return new Rank(scheme, m02, experimental, applied ? delta : 0, applied, status);
    }

    public record Rank(String scheme, Double m02Score, Double experimentalScore, int appliedDelta, boolean applied, String status) {
        public boolean rankChanged() {
            return applied && appliedDelta != 0;
        }
    }

    public record PairResult(CompatibilityDecision decision, RelationshipReasoningContext context,
                             DirectionalReasoning agentA, DirectionalReasoning agentB,
                             Rank schemeA, Rank schemeB, Rank schemeC) {}
}
