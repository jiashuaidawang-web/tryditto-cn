package cn.aiditto.domain.feedback.v4.learning;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.compatibility.v4.PairCompatibilityResult;
import cn.aiditto.domain.matching.v4.GlobalCandidateGraphBuilder;
import cn.aiditto.domain.matching.v4.GlobalMatchingInput;
import cn.aiditto.domain.matching.v4.GlobalMatchingResult;
import cn.aiditto.domain.matching.v4.GlobalMatchPeople;
import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.matching.v4.V4GlobalMatchingEngine;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ten fictional replays. A score change is a change in system behavior.
 * It is not evidence that a real relationship got better.
 */
public final class ProfileLearningLab {
    private ProfileLearningLab() {}

    public record LabCase(String id, String story, String signal, String proposal, boolean profileChanged,
                          String scoreBefore, String scoreAfter, String poolBefore, String poolAfter,
                          String pairsBefore, String pairsAfter, int safetyViolations) {}

    public static List<LabCase> cases() {
        CompatibilitySubject frequent = user(91001, 1, "frequent", "frequent");
        CompatibilitySubject quiet = user(91002, 1, "quiet", "quiet");
        CompatibilitySubject direct = user(91003, 1, "direct", "direct");
        CompatibilitySubject steady = user(91004, 1, "direct", "frequent");
        return List.of(
                changed("L01", "用户把自述沟通从高频改成安静", "EXPLICIT_SELF_CORRECTION", "CONFIRMED",
                        frequent, user(91001, 2, "quiet", "frequent"), quiet, List.of(quiet, direct, steady)),
                changed("L02", "用户把希望的对方沟通从高频改成安静", "EXPLICIT_PARTNER_PREFERENCE", "CONFIRMED",
                        frequent, user(91001, 2, "frequent", "quiet"), quiet, List.of(quiet, direct, steady)),
                unchanged("L03", "拒绝没有原因", "NO_LEARNING_SIGNAL", "NONE", frequent, quiet, List.of(quiet, direct, steady)),
                unchanged("L04", "超时", "NO_LEARNING_SIGNAL", "NONE", frequent, quiet, List.of(quiet, direct, steady)),
                unchanged("L05", "没有安排见面", "RELATIONSHIP_OUTCOME", "NONE", frequent, quiet, List.of(quiet, direct, steady)),
                unchanged("L06", "对方的负面评价", "COUNTERPART_OPINION", "NONE", frequent, quiet, List.of(quiet, direct, steady)),
                unchanged("L07", "一次沟通不满", "BEHAVIORAL_OBSERVATION", "NONE", frequent, quiet, List.of(quiet, direct, steady)),
                changed("L08", "三次同方向自述之后，用户确认改成安静", "PREFERENCE_HYPOTHESIS", "CONFIRMED",
                        frequent, user(91001, 2, "quiet", "quiet"), quiet, List.of(quiet, direct, steady)),
                unchanged("L09", "用户拒绝提案", "EXPLICIT_SELF_CORRECTION", "REJECTED", frequent, quiet, List.of(quiet, direct, steady)),
                changed("L10", "确认之后撤回，只去掉这条派生特征", "EXPLICIT_SELF_CORRECTION", "RETRACTED",
                        user(91001, 2, "quiet", "frequent"), frequent, quiet, List.of(quiet, direct, steady))
        );
    }

    public static void main(String[] args) {
        System.out.print(json());
    }

    public static String json() {
        StringBuilder out = new StringBuilder("[\n");
        List<LabCase> cases = cases();
        for (int i = 0; i < cases.size(); i++) {
            LabCase item = cases.get(i);
            out.append("  {\"id\":\"").append(item.id())
                    .append("\",\"story\":\"").append(item.story())
                    .append("\",\"signal\":\"").append(item.signal())
                    .append("\",\"proposal\":\"").append(item.proposal())
                    .append("\",\"profileChanged\":").append(item.profileChanged())
                    .append(",\"scoreBefore\":\"").append(item.scoreBefore())
                    .append("\",\"scoreAfter\":\"").append(item.scoreAfter())
                    .append("\",\"poolBefore\":\"").append(item.poolBefore())
                    .append("\",\"poolAfter\":\"").append(item.poolAfter())
                    .append("\",\"pairsBefore\":\"").append(item.pairsBefore())
                    .append("\",\"pairsAfter\":\"").append(item.pairsAfter())
                    .append("\",\"safetyViolations\":").append(item.safetyViolations())
                    .append("}");
            out.append(i + 1 == cases.size() ? "\n" : ",\n");
        }
        return out.append("]\n").toString();
    }

    private static LabCase unchanged(String id, String story, String signal, String proposal,
                                     CompatibilitySubject actor, CompatibilitySubject partner, List<CompatibilitySubject> pool) {
        return changed(id, story, signal, proposal, actor, actor, partner, pool);
    }

    private static LabCase changed(String id, String story, String signal, String proposal,
                                   CompatibilitySubject before, CompatibilitySubject after,
                                   CompatibilitySubject partner, List<CompatibilitySubject> pool) {
        BidirectionalCompatibilityEngine scorer = BidirectionalCompatibilityEngine.v1();
        String scoreBefore = score(scorer, before, partner);
        String scoreAfter = score(scorer, after, partner);
        String poolBefore = order(scorer, before, pool);
        String poolAfter = order(scorer, after, pool);
        String pairsBefore = pairs(before, pool);
        String pairsAfter = pairs(after, pool);
        return new LabCase(id, story, signal, proposal, before.profileVersion() != after.profileVersion()
                || !scoreBefore.equals(scoreAfter), scoreBefore, scoreAfter, poolBefore, poolAfter,
                pairsBefore, pairsAfter, 0);
    }

    private static String score(BidirectionalCompatibilityEngine scorer, CompatibilitySubject left, CompatibilitySubject right) {
        PairCompatibilityResult result = scorer.score(left, right, CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, List.of(left, right)));
        return text(result.scoreAB()) + "/" + text(result.scoreBA());
    }

    private static String order(BidirectionalCompatibilityEngine scorer, CompatibilitySubject actor, List<CompatibilitySubject> pool) {
        List<String> rows = new ArrayList<>();
        for (CompatibilitySubject other : pool) {
            rows.add(other.userId() + ":" + score(scorer, actor, other));
        }
        return String.join(",", rows);
    }

    private static String pairs(CompatibilitySubject actor, List<CompatibilitySubject> pool) {
        List<CompatibilitySubject> users = new ArrayList<>();
        users.add(actor);
        users.addAll(pool);
        GlobalMatchingResult result = V4GlobalMatchingEngine.solve(GlobalCandidateGraphBuilder.build(
                GlobalMatchingInput.of("lab-" + actor.userId() + "-" + actor.profileVersion(), "lab", users,
                        Set.of(), Set.of(), Set.of(), java.util.Map.of(), 7L),
                GlobalCandidateGraphBuilder.Strategy.E_FULL_POOL), Objective.SELECTED);
        if (result.safetyViolations() != 0) {
            throw new IllegalStateException("safety " + result.safetyViolations());
        }
        List<String> pairs = new ArrayList<>();
        for (GlobalMatchingResult.MatchedPair pair : result.pairs()) {
            pairs.add(pair.lowId() + "-" + pair.highId() + ":" + pair.fitCenti());
        }
        return pairs.isEmpty() ? "none" : String.join(",", pairs);
    }

    private static CompatibilitySubject user(long id, long version, String selfCommunication, String partnerCommunication) {
        CompatibilitySubject person = GlobalMatchPeople.person(id,
                new String[]{"steady", selfCommunication, "steady", "flexible"},
                new String[]{"steady", partnerCommunication, "steady", "flexible"},
                Importance.KINDA, true, false);
        if (version == 1) return person;
        return CompatibilitySubject.synthetic(id, version, 1, true, false, GlobalMatchPeople.SOURCE,
                person.self().features(), person.preference().targets(), person.candidate());
    }

    private static String text(Double value) {
        return value == null ? "none" : String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
