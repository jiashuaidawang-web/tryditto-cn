package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import org.junit.jupiter.api.Test;

import java.util.List;

import cn.aiditto.domain.matchlab.WorkspaceBoard;

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
            assertThat(entry.datasetIdentityHash()).isNotBlank();
            assertThat(entry.datasetContentHash()).isNotEqualTo(LabWorkbench.CONTENT_UNAVAILABLE);
            assertThat(entry.commitSha()).isEqualTo("094e161c20198aef5a23a8dce76cf0b58e77d858");
            assertThat(entry.catalogIntegrationCommit()).isEqualTo(LabWorkbench.INTEGRATION_COMMIT);
            assertThat(entry.commitSha()).isNotEqualTo(entry.catalogIntegrationCommit());
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
        assertThat(lab.catalog()).extracting(LabWorkbench.CatalogEntry::datasetIdentityHash).doesNotHaveDuplicates();
        assertThat(lab.catalog()).anySatisfy(entry -> {
            assertThat(entry.experimentId()).isEqualTo("m01-rank-dev");
            assertThat(entry.datasetContentHash()).isEqualTo(LabWorkbench.CONTENT_UNAVAILABLE);
        });
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
        assertThat(reasoning.runId()).isNotBlank();
        LabWorkbench.LabRun replay = lab.runs().get(lab.runs().size() - 1);
        assertThat(replay.inputSnapshot()).contains("dev-0-MUTUAL");
        assertThat(replay.inputHash()).hasSize(64);
        assertThat(replay.resultHash()).hasSize(64);
    }

    @Test
    void sandboxRecallDoesNotInventRouteSignalsAndVersionsDoNotGetLost() throws Exception {
        LabWorkbench lab = new LabWorkbench();
        var first = lab.score(71001, 71002, "test");
        var second = lab.score(71001, 71002, "test");
        assertThat(second.aggregate()).isEqualTo(first.aggregate());
        assertThat(lab.runs()).filteredOn(run -> run.experimentType().equals("BIDIRECTIONAL_SCORE"))
                .extracting(LabWorkbench.LabRun::inputHash).containsOnly(lab.runs().get(0).inputHash());
        var sandbox = lab.recallSandbox(71001, "test");
        assertThat(sandbox.population()).isEqualTo("SYNTHETIC_SANDBOX");
        assertThat(sandbox.routeContract()).isEqualTo("NOT_APPLICABLE");
        assertThat(sandbox.executionMode()).isEqualTo("ENGINE_EXECUTED");
        assertThat(sandbox.schemes()).extracting(LabWorkbench.SchemeResult::scheme).containsExactly("FULL_POOL_M02");
        assertThat(sandbox.pool()).anyMatch(person -> person.userId() == 71002 && "LEGAL".equals(person.legality()));
        assertThat(sandbox.schemes().get(0).rows()).noneMatch(row -> "NO_ROUTE_SIGNAL".equals(row.miss()));
        assertThat(sandbox.schemes().get(0).rows()).allSatisfy(row -> assertThat(row.profileVersion()).isPositive());
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        Runnable edit = () -> {
            ready.countDown();
            try {
                go.await();
                lab.update(71002, "乙·并发", List.of(new FeatureWrite("self.pace", "slow", "CONFIRMED", null, "EQ")),
                        List.of(new FeatureWrite("partner.pace", "fast", "CONFIRMED", "SUPER", "EQ")), List.of(), 1);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        };
        Thread left = new Thread(edit);
        Thread right = new Thread(edit);
        left.start();
        right.start();
        assertThat(ready.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        go.countDown();
        left.join();
        right.join();
        assertThat(lab.profile(71002).version()).isEqualTo(2);
        assertThat(failure.get()).isInstanceOf(LabWorkbench.LabRejection.class);
    }

    @Test
    void workspacesStayWithTheirOwnerAndResetStartsClean() {
        WorkspaceBoard board = new WorkspaceBoard();
        WorkspaceBoard.Workspace first = board.resolve(11, null);
        WorkspaceBoard.Workspace again = board.resolve(11, first.workspaceId());
        assertThat(again.workspaceId()).isEqualTo(first.workspaceId());
        assertThat(again.workbench()).isSameAs(first.workbench());
        WorkspaceBoard.Workspace other = board.resolve(22, null);
        assertThat(other.workspaceId()).isNotEqualTo(first.workspaceId());
        assertThatThrownBy(() -> board.resolve(11, other.workspaceId()))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .extracting(error -> ((LabWorkbench.LabRejection) error).code())
                .isEqualTo("WORKSPACE");
        WorkspaceBoard.Workspace reset = board.reset(11, first.workspaceId());
        assertThat(reset.workspaceId()).isNotEqualTo(first.workspaceId());
        assertThat(reset.workbench().profile(71001).version()).isEqualTo(1);
        assertThat(reset.workbench().workspaceId()).isEqualTo(reset.workspaceId());
    }
}
