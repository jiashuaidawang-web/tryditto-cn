package cn.aiditto.domain.reasoning.v4;

import cn.aiditto.domain.compatibility.v4.BidirectionalCompatibilityEngine;
import cn.aiditto.domain.compatibility.v4.CompatibilityContext;
import cn.aiditto.domain.compatibility.v4.CompatibilitySubject;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.relationship.FeatureValue;
import cn.aiditto.domain.relationship.RelationshipTypes.FeatureStatus;
import cn.aiditto.domain.relationship.RelationshipTypes.Importance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RelationshipReasoningTest {
    private final ReasoningExperiment experiment = ReasoningExperiment.v1();

    @Test
    void sealedPairsDoNotExposeProfileRowsOrCallTheModel() {
        ReasoningDataset.Relation veto = relation(ReasoningDataset.Kind.HARD_VETO);
        AtomicInteger calls = new AtomicInteger();
        ReasoningExperiment.PairResult blocked = experiment.reason(veto.seeker(), veto.candidate(), context(veto),
                prompt -> { calls.incrementAndGet(); return ReasoningModel.ReasoningCompletion.failed("spy", "NO"); },
                ReasoningBudget.experiment());
        assertThat(blocked.context().sealed()).isTrue();
        assertThat(blocked.context().evidence()).isEmpty();
        assertThat(blocked.agentA().status()).isEqualTo(ReasoningStatus.REFUSED);
        assertThat(blocked.agentB().usable()).isEmpty();
        assertThat(blocked.schemeC().applied()).isFalse();
        assertThat(calls).hasValue(0);

        CompatibilitySubject left = veto.seeker();
        CompatibilitySubject right = relation(ReasoningDataset.Kind.MUTUAL).candidate();
        CompatibilityContext open = CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK,
                BidirectionalCompatibilityEngine.NOW, List.of(left, right));
        CompatibilityContext revoked = new CompatibilityContext(open.input(), open.now(), open.applicants(),
                Map.of(left.userId(), CompatibilityContext.granted(left.userId(), open.now()),
                        right.userId(), new MatchingConsent(right.userId(), ConsentStatus.REVOKED, 1, open.now(), open.now(),
                                cn.aiditto.domain.consent.ConsentPolicy.POLICY_VERSION, open.now())));
        ReasoningExperiment.PairResult unauthorized = experiment.reason(left, right, revoked,
                prompt -> { calls.incrementAndGet(); return ReasoningModel.ReasoningCompletion.failed("spy", "NO"); },
                ReasoningBudget.experiment());
        assertThat(unauthorized.context().sealed()).isTrue();
        assertThat(unauthorized.context().evidence()).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void withheldValuesAndTheOtherAgentsConclusionStayOutOfThePrompt() {
        CompatibilitySubject chooser = CompatibilitySubject.synthetic(1, List.of(),
                List.of(CompatibilitySubject.preference("partner.communication", "quiet", Importance.SUPER)),
                Candidate.builder(1).hobbies("reading").build());
        CompatibilitySubject hidden = CompatibilitySubject.synthetic(2, List.of(
                CompatibilitySubject.feature("self.communication", new FeatureValue.Withheld(), FeatureStatus.CONFIRMED)),
                List.of(), Candidate.builder(2).hobbies(ReasoningDataset.MALICIOUS_TEXT).build());
        ReasoningExperiment.PairResult result = experiment.reason(chooser, hidden, CompatibilityContext.experiment(
                BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW, List.of(chooser, hidden)),
                new RuleEchoModel(), ReasoningBudget.experiment());
        String prompt = ReasoningPrompt.render(result.context(), "AB",
                cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry.v1().features()).user();
        assertThat(prompt).doesNotContain("WITHHELD").doesNotContain(ReasoningDataset.MALICIOUS_TEXT);
        assertThat(prompt).doesNotContain("PREFERENCE owner=2");

        List<String> seen = new ArrayList<>();
        ReasoningModel spy = promptView -> {
            seen.add(promptView.user());
            String body = promptView.user().contains("DIRECTION AB")
                    ? "{\"direction\":\"AB\",\"chooserId\":" + promptView.view().chooserId()
                    + ",\"targetId\":" + promptView.view().targetId()
                    + ",\"supportedMatches\":[],\"potentialComplements\":[],\"possibleConflicts\":[],\"missingInformation\":[],\"contradictions\":[],\"unsupportedClaims\":[],\"confidence\":0.2,\"reasoningStatus\":\"COMPLETED\",\"marker\":\"AGENT-A-ONLY\"}"
                    : "{\"direction\":\"BA\",\"chooserId\":" + promptView.view().chooserId()
                    + ",\"targetId\":" + promptView.view().targetId()
                    + ",\"supportedMatches\":[],\"potentialComplements\":[],\"possibleConflicts\":[],\"missingInformation\":[],\"contradictions\":[],\"unsupportedClaims\":[],\"confidence\":0.2,\"reasoningStatus\":\"COMPLETED\"}";
            return ReasoningModel.ReasoningCompletion.ok(body, "spy", 1);
        };
        ReasoningDataset.Relation mutual = relation(ReasoningDataset.Kind.MUTUAL);
        experiment.reason(mutual.seeker(), mutual.candidate(), context(mutual), spy, ReasoningBudget.experiment());
        assertThat(seen).hasSize(2);
        assertThat(seen.get(1)).doesNotContain("AGENT-A-ONLY");
        assertThat(seen.get(0)).doesNotContain("PREFERENCE owner=" + mutual.candidate().userId() + " ");
        assertThat(seen.get(1)).doesNotContain("PREFERENCE owner=" + mutual.seeker().userId() + " ");
    }

    @Test
    void theValidatorRejectsTheAdversarialClaims() {
        ReasoningDataset.Relation mutual = relation(ReasoningDataset.Kind.MUTUAL);
        ReasoningExperiment.PairResult result = experiment.reason(mutual.seeker(), mutual.candidate(), context(mutual),
                new RuleEchoModel(), ReasoningBudget.experiment());
        var alignments = cn.aiditto.domain.compatibility.v4.FeatureAlignmentRegistry.v1();
        String realPref = result.context().view("AB").evidence().stream().filter(item -> "preference".equals(item.role())).findFirst().orElseThrow().ref();
        String realSelf = result.context().view("AB").evidence().stream().filter(item -> "self".equals(item.role())).findFirst().orElseThrow().ref();
        long chooser = mutual.seeker().userId();
        long target = mutual.candidate().userId();
        List<String> payloads = List.of(
                claim(chooser, target, "AB", "self.mbti", "self.pace", List.of(realPref), "slow"),
                claim(chooser, target, "AB", "partner.pace", "self.pace", List.of("u999:self.pace:v1:self"), "slow"),
                claim(target, chooser, "BA", "partner.pace", "self.pace", List.of(realPref, realSelf), "slow"),
                claim(chooser, target, "AB", "partner.pace", "self.pace", List.of(realPref), "因为是男生所以主动"),
                claim(chooser, target, "AB", "partner.communication", "self.communication", List.of(realPref), "缺失所以内向"),
                claim(chooser, target, "AB", "partner.pace", "self.pace", List.of(realSelf), "新增硬约束"),
                claim(chooser, target, "AB", "partner.pace", "self.communication", List.of(realPref, realSelf), "slow"));
        int accepted = 0;
        for (String payload : payloads) {
            DirectionalReasoning validated = ReasoningValidator.validate(result.context(), "AB", payload, alignments);
            accepted += validated.supportedMatches().size() + validated.potentialComplements().size()
                    + validated.possibleConflicts().size();
        }
        assertThat(payloads).hasSize(7);
        assertThat(accepted).isZero();
    }

    @Test
    void schemeCIsBoundedAndAFailureDoesNotInventAScore() {
        ReasoningDataset.Relation mutual = relation(ReasoningDataset.Kind.MUTUAL);
        ReasoningExperiment.PairResult echoed = experiment.reason(mutual.seeker(), mutual.candidate(), context(mutual),
                new RuleEchoModel(), ReasoningBudget.experiment());
        assertThat(echoed.schemeA().experimentalScore()).isEqualTo(echoed.schemeB().experimentalScore());
        assertThat(echoed.schemeB().rankChanged()).isFalse();
        assertThat(echoed.schemeC().appliedDelta()).isBetween(-ReasoningExperiment.MAX_ADJUSTMENT, ReasoningExperiment.MAX_ADJUSTMENT);
        assertThat(echoed.schemeC().experimentalScore()).isNotNull();
        assertThat(Math.abs(echoed.schemeC().experimentalScore() - echoed.schemeA().m02Score())).isLessThanOrEqualTo(10);

        AtomicInteger calls = new AtomicInteger();
        ReasoningBudget budget = ReasoningBudget.experiment();
        ReasoningModel failing = prompt -> { calls.incrementAndGet(); throw new IllegalStateException("timeout"); };
        ReasoningExperiment.PairResult failed = experiment.reason(mutual.seeker(), mutual.candidate(), context(mutual), failing, budget);
        assertThat(failed.schemeC().status()).isEqualTo("MODEL_FAILED");
        assertThat(failed.schemeC().experimentalScore()).isEqualTo(failed.schemeA().m02Score());
        assertThat(failed.agentA().confidence()).isNull();
        experiment.reason(mutual.seeker(), mutual.candidate(), context(mutual), failing, budget);
        assertThat(calls).hasValue(2);
    }

    @Test
    void budgetDoesNotLetTheFirstUserTakeEveryCall() {
        List<CompatibilitySubject> left = new ArrayList<>();
        List<CompatibilitySubject> right = new ArrayList<>();
        List<CompatibilityContext> contexts = new ArrayList<>();
        for (int copy = 0; copy < 3; copy++) {
            int current = copy;
            ReasoningDataset.Relation relation = ReasoningDataset.all().stream()
                    .filter(item -> item.kind() == ReasoningDataset.Kind.MUTUAL && item.copy() == current).findFirst().orElseThrow();
            left.add(relation.seeker());
            right.add(relation.candidate());
            contexts.add(context(relation));
        }
        ReasoningBudget budget = new ReasoningBudget(2, 2, 2);
        experiment.runFair(left, right, contexts, new RuleEchoModel(), budget);
        assertThat(budget.userUsed(left.get(0).userId())).isEqualTo(1);
        assertThat(budget.userUsed(left.get(1).userId())).isEqualTo(1);
        assertThat(budget.userUsed(left.get(2).userId())).isZero();
        assertThat(budget.used()).isEqualTo(2);
        assertThat(budget.skips()).anyMatch(skip -> skip.contains("GLOBAL_CAP"));
    }

    @Test
    void theDatasetIsHandLabeledAndTheFixtureIsNotAModelResult() {
        List<ReasoningDataset.Relation> all = ReasoningDataset.all();
        assertThat(all).hasSize(120);
        assertThat(all.stream().filter(item -> "dev".equals(item.split())).count()).isEqualTo(90);
        assertThat(all.stream().filter(item -> "holdout".equals(item.split())).count()).isEqualTo(30);
        assertThat(ReasoningDataset.hash()).isEqualTo(ReasoningDataset.hash());
        assertThat(ReasoningLivePlan.status(Map.of())).isEqualTo("NOT_RUN");
        assertThat(ReasoningExperiment.EVIDENCE_PRECISION_GATE).isEqualTo(0.90);
        assertThat(ReasoningExperiment.UNSUPPORTED_USABLE_GATE).isEqualTo(0.02);
        ReasoningMetrics.Report report = ReasoningMetrics.fixture();
        assertThat(report.realModel()).isEqualTo("NOT_RUN");
        assertThat(report.fixtureModel()).isEqualTo(RuleEchoModel.NAME);
        assertThat(report.illegalHardAccepted()).isZero();
        assertThat(report.unauthorizedReads()).isZero();
        assertThat(report.unsupportedUsable()).isZero();
        assertThat(report.directionAttempted()).isPositive();
        String lab = ReasoningLab.json();
        assertThat(lab).doesNotContain(ReasoningDataset.MALICIOUS_TEXT);
        assertThat(lab).contains("MUTUAL").contains("HARD_VETO").contains("NOT_RUN");
        assertThat(countCases(lab)).isEqualTo(8);
    }

    private static int countCases(String json) {
        int count = 0;
        int from = 0;
        while (true) {
            int next = json.indexOf("\"kind\":", from);
            if (next < 0) return count;
            count++;
            from = next + 6;
        }
    }

    private static ReasoningDataset.Relation relation(ReasoningDataset.Kind kind) {
        return ReasoningDataset.all().stream().filter(item -> item.kind() == kind && item.copy() == 0).findFirst().orElseThrow();
    }

    private static CompatibilityContext context(ReasoningDataset.Relation relation) {
        return CompatibilityContext.experiment(BidirectionalCompatibilityEngine.WEEK, BidirectionalCompatibilityEngine.NOW,
                List.of(relation.seeker(), relation.candidate()));
    }

    private static String claim(long chooser, long target, String direction, String preference, String feature,
                                List<String> refs, String statement) {
        return "{\"direction\":\"" + direction + "\",\"chooserId\":" + chooser + ",\"targetId\":" + target
                + ",\"supportedMatches\":[{\"preferenceCode\":\"" + preference + "\",\"targetFeatureCode\":\"" + feature
                + "\",\"evidenceRefs\":[" + refs.stream().map(ref -> "\"" + ref + "\"").reduce((a, b) -> a + "," + b).orElse("")
                + "],\"statement\":\"" + statement + "\"}],\"potentialComplements\":[],\"possibleConflicts\":[],\"missingInformation\":[],\"contradictions\":[],\"unsupportedClaims\":[],\"confidence\":0.9,\"reasoningStatus\":\"COMPLETED\"}";
    }
}
