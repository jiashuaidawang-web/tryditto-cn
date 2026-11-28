package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabWorkbenchTest {
    @Test
    void frozenCatalogIsNotLabeledAsALiveRun() {
        LabWorkbench lab = new LabWorkbench();
        assertThat(lab.catalog()).anySatisfy(entry -> {
            assertThat(entry.experimentId()).isEqualTo("f03-synthetic-1");
            assertThat(entry.sourceType()).isEqualTo("FROZEN_RESULT");
            assertThat(entry.executionStatus()).isEqualTo("RECORDED");
            assertThat(entry.datasetHash()).isNotBlank();
            assertThat(entry.commitSha()).isEqualTo(LabWorkbench.FROZEN_COMMIT);
            assertThat(entry.caseIds()).contains("f03-synthetic-1");
        });
        assertThat(lab.catalog()).anySatisfy(entry -> {
            assertThat(entry.experimentId()).isEqualTo("m03-rule-echo");
            assertThat(entry.sourceType()).isEqualTo("MODEL_REPLAY");
            assertThat(entry.executionStatus()).isEqualTo("REPLAY_READY");
            assertThat(entry.limitations()).contains("C1");
            assertThat(entry.caseIds()).contains("dev-0-MUTUAL");
        });
        assertThat(lab.catalog()).noneMatch(entry -> entry.qualityStatus().equals("PRODUCTION_GO"));
        assertThat(lab.catalog()).extracting(LabWorkbench.CatalogEntry::datasetHash).doesNotHaveDuplicates();
        assertThat(lab.policies()).extracting(LabWorkbench.PolicyNote::policy).containsExactly("E", "B5", "G2");
    }

    @Test
    void twoSandboxProfilesUseTheEngineAndAnIllegalCodeIsRejected() {
        LabWorkbench lab = new LabWorkbench();
        var mutual = lab.score(71001, 71002, "test");
        assertThat(mutual.executionMode()).isEqualTo("ENGINE_EXECUTED");
        assertThat(mutual.recommendationEligible()).isTrue();
        assertThat(mutual.formulaVersion()).isNotBlank();
        assertThat(mutual.items()).isNotEmpty();
        var oneWay = lab.score(71003, 71004, "test");
        assertThat(oneWay.recommendationEligible()).isFalse();
        assertThat(oneWay.reasons()).contains("RECOMMENDATION_REFUSED");
        var sparse = lab.score(71001, 71005, "test");
        assertThat(sparse.recommendationEligible()).isFalse();
        var veto = lab.score(71001, 71006, "test");
        assertThat(veto.recommendationEligible()).isFalse();
        assertThat(veto.hardFilter()).anyMatch(code -> code.startsWith("HARD_"));
        assertThatThrownBy(() -> lab.create("外来", List.of(new FeatureWrite("not.a.feature", "slow", "CONFIRMED", null, "EQ")), List.of()))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .extracting(error -> ((LabWorkbench.LabRejection) error).code())
                .isEqualTo("FEATURE_CODE");
        assertThatThrownBy(() -> lab.create("未知状态", List.of(new FeatureWrite("self.pace", "slow", "UNKNOWN", null, "EQ")), List.of()))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .extracting(error -> ((LabWorkbench.LabRejection) error).code())
                .isEqualTo("FEATURE_STATUS");
        assertThat(lab.profile(71007).features()).anySatisfy(feature -> {
            assertThat(feature.section()).isEqualTo("BEHAVIOR");
            assertThat(feature.featureCode()).isEqualTo("behavior.scenario.signal");
            assertThat(feature.evidenceSource()).isEqualTo("SELF_REPORTED");
            assertThat(feature.status()).isEqualTo("CONFIRMED");
        });
        assertThat(lab.profile(71007).features()).noneMatch(feature -> feature.section().equals("MEMORY"));
    }

    @Test
    void editingASoftPreferenceChangesTheScoreWithoutClaimingRelationshipLift() {
        LabWorkbench lab = new LabWorkbench();
        var before = lab.score(71001, 71002, "test");
        lab.update(71002, "乙·改偏好", List.of(
                new FeatureWrite("self.pace", "slow", "CONFIRMED", null, "EQ"),
                new FeatureWrite("self.communication", "quiet", "CONFIRMED", null, "EQ"),
                new FeatureWrite("self.social.initiative", "low", "CONFIRMED", null, "EQ"),
                new FeatureWrite("self.schedule", "early", "CONFIRMED", null, "EQ")), List.of(
                new FeatureWrite("partner.pace", "fast", "CONFIRMED", "SUPER", "EQ"),
                new FeatureWrite("partner.communication", "frequent", "CONFIRMED", "SUPER", "EQ"),
                new FeatureWrite("partner.social.initiative", "high", "CONFIRMED", "SUPER", "EQ"),
                new FeatureWrite("partner.schedule", "late", "CONFIRMED", "SUPER", "EQ")));
        var after = lab.score(71001, 71002, "test");
        assertThat(after.versionB()).isEqualTo(before.versionB() + 1);
        assertThat(after.aggregate()).isNotEqualTo(before.aggregate());
        assertThat(after.recommendationEligible()).isFalse();
        assertThat(lab.runs()).filteredOn(run -> run.experimentType().equals("BIDIRECTIONAL_SCORE"))
                .allSatisfy(run -> assertThat(run.limitations()).contains("不是真实关系效果"));
        assertThat(lab.diff(71001, 71002)).isNotEmpty();
    }

    @Test
    void recallAndReasoningStayInsideTheSandboxContract() {
        LabWorkbench lab = new LabWorkbench();
        var recall = lab.recall("test");
        assertThat(recall.executionMode()).isEqualTo("ENGINE_EXECUTED");
        assertThat(recall.dataset()).isEqualTo("dev");
        assertThat(recall.people()).isLessThan(80);
        assertThat(recall.pool()).hasSize(recall.people());
        assertThat(recall.pool()).anySatisfy(person -> assertThat(person.legality()).isEqualTo("SEEKER"));
        assertThat(recall.pool()).anyMatch(person -> person.legality().startsWith("HARD") || "PROFILE_WITHDRAWN".equals(person.legality()) || "PROFILE_STALE".equals(person.legality()));
        assertThat(recall.schemes()).extracting(LabWorkbench.SchemeResult::scheme)
                .containsExactly("RRF", "RRF_M02", "FULL_POOL_M02", "SHORTLIST_M02");
        assertThat(recall.schemes()).allSatisfy(scheme -> assertThat(scheme.safetyViolations()).isZero());
        assertThat(recall.schemes().get(0).rows()).anyMatch(row -> row.miss().equals("NO_ROUTE_SIGNAL"));
        var holdout = lab.recall("holdout", "test");
        assertThat(holdout.dataset()).isEqualTo("holdout");
        assertThat(holdout.seekerId()).isNotEqualTo(recall.seekerId());
        assertThat(holdout.people()).isLessThan(80);
        assertThatThrownBy(() -> lab.recall("production", "test"))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .extracting(error -> ((LabWorkbench.LabRejection) error).code())
                .isEqualTo("DATASET");
        var reasoning = lab.reasoning("test");
        assertThat(reasoning.executionMode()).isEqualTo("MODEL_REPLAY");
        assertThat(reasoning.modelName()).isEqualTo("rule-echo-fixture");
        assertThat(reasoning.realModelStatus()).isEqualTo("NOT_RUN");
        assertThat(reasoning.aiAdjustment()).isFalse();
        assertThat(reasoning.m02Score()).isNotNull();
        assertThat(reasoning.agentA().unsupported()).isNotNull();
        assertThat(reasoning.agentA().rejected()).isNotNull();
    }
}
