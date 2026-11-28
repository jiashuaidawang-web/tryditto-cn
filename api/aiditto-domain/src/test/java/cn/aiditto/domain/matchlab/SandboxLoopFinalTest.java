package cn.aiditto.domain.matchlab;

import cn.aiditto.domain.matching.v4.Objective;
import cn.aiditto.domain.matchlab.LabWorkbench.FeatureWrite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxLoopFinalTest {
    @Test
    void confirmationKeepsTheRestOfTheProfileAndALaterEditWins() {
        LabWorkbench lab = new LabWorkbench();
        long userId = 71001L;
        List<FeatureWrite> self = new ArrayList<>();
        List<FeatureWrite> preferences = new ArrayList<>();
        for (FeatureWrite write : lab.writes(userId)) {
            if (write.code().startsWith("partner.")) preferences.add(write);
            else self.add(write);
        }
        preferences.replaceAll(write -> "partner.schedule".equals(write.code())
                ? new FeatureWrite(write.code(), write.value(), "PROVISIONAL", "KINDA", "EQ") : write);
        lab.update(userId, "甲·双向", self, preferences, List.of(), 1);

        SandboxLoop.LearningView proposal = lab.loop().propose(userId, "partner.pace", "fast", "");
        SandboxLoop.LearningView confirmed = lab.loop().decide(proposal.proposal().proposalId(), "confirm", userId);
        assertThat(confirmed.profileVersion()).isEqualTo(3);
        assertThat(value(lab, userId, "partner.pace")).isEqualTo("fast");
        assertThat(value(lab, userId, "self.pace")).isEqualTo("slow");
        assertThat(value(lab, userId, "self.communication")).isEqualTo("quiet");
        assertThat(write(lab, userId, "partner.communication").importance()).isEqualTo("SUPER");
        assertThat(write(lab, userId, "partner.schedule").status()).isEqualTo("PROVISIONAL");
        assertThat(write(lab, userId, "partner.schedule").importance()).isEqualTo("KINDA");
        assertThat(lab.writes(71007L)).anyMatch(item -> "behavior.scenario.signal".equals(item.code()));

        self = selfOf(lab, userId).stream()
                .map(write -> "self.communication".equals(write.code())
                        ? new FeatureWrite(write.code(), "direct", write.status(), write.importance(), write.operator()) : write)
                .toList();
        lab.update(userId, "甲·双向", self, preferencesOf(lab, userId), List.of(), confirmed.profileVersion());
        SandboxLoop.LearningView next = lab.loop().propose(userId, "partner.communication", "direct", "");
        lab.loop().decide(next.proposal().proposalId(), "confirm", userId);
        assertThat(value(lab, userId, "self.communication")).isEqualTo("direct");
        assertThat(value(lab, userId, "partner.pace")).isEqualTo("fast");

        SandboxLoop.LearningView stale = lab.loop().propose(userId, "partner.social.initiative", "low", "");
        lab.update(userId, "甲·改名", self, currentPreferences(lab, userId), List.of(), lab.profile(userId).version());
        long version = lab.profile(userId).version();
        assertThatThrownBy(() -> lab.loop().decide(stale.proposal().proposalId(), "confirm", userId))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("STALE");
        assertThat(lab.profile(userId).version()).isEqualTo(version);
    }

    @Test
    void feedbackSourceIsBoundAndARetractedSourceCannotConfirm() {
        LabWorkbench lab = new LabWorkbench();
        SandboxLoop.GlobalView live = lab.loop().solve("G2", List.of(), "test");
        SandboxLoop.PairView pair = live.pairs().get(0);
        SandboxLoop.FeedbackView opened = lab.loop().open(live.runId(), pair.lowId(), pair.highId());
        SandboxLoop.FeedbackView accepted = lab.loop().act(opened.ledgerKey(), "accept", pair.lowId(), null);
        String acceptId = eventId(accepted, "MATCH_ACCEPTED");
        assertThatThrownBy(() -> lab.loop().propose(pair.lowId(), "partner.pace", "fast", acceptId, opened.ledgerKey(), "FEEDBACK"))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("INSUFFICIENT_SEMANTICS");
        assertThatThrownBy(() -> lab.loop().propose(pair.lowId(), "partner.pace", "fast", "forged-event", opened.ledgerKey(), "FEEDBACK"))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("SOURCE");
        assertThatThrownBy(() -> lab.loop().propose(pair.highId(), "partner.pace", "fast", acceptId, opened.ledgerKey(), "FEEDBACK"))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("CROSS_USER");

        SandboxLoop.FeedbackView continued = lab.loop().act(opened.ledgerKey(), "continue", pair.lowId(), null);
        String continueId = eventId(continued, "CONTINUE_INTEREST_EXPRESSED");
        SandboxLoop.FeedbackView corrected = lab.loop().act(opened.ledgerKey(), "preference", pair.lowId(), continueId);
        String preferenceId = eventId(corrected, "PREFERENCE_CORRECTION_SUBMITTED");
        SandboxLoop.LearningView proposal = lab.loop().propose(pair.lowId(), "partner.pace", "fast", preferenceId, opened.ledgerKey(), "FEEDBACK");
        lab.loop().act(opened.ledgerKey(), "retract", pair.lowId(), null);
        long version = lab.profile(pair.lowId()).version();
        assertThatThrownBy(() -> lab.loop().decide(proposal.proposal().proposalId(), "confirm", pair.lowId()))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("RETRACTED_SOURCE");
        assertThat(lab.profile(pair.lowId()).version()).isEqualTo(version);

        SandboxLoop.GlobalView fresh = lab.loop().solve("G2", List.of(), "test");
        SandboxLoop.PairView nextPair = fresh.pairs().get(0);
        SandboxLoop.FeedbackView other = lab.loop().open(fresh.runId(), nextPair.lowId(), nextPair.highId());
        SandboxLoop.FeedbackView again = lab.loop().act(other.ledgerKey(), "continue", nextPair.highId(), null);
        String otherContinue = eventId(again, "CONTINUE_INTEREST_EXPRESSED");
        SandboxLoop.FeedbackView otherPreference = lab.loop().act(other.ledgerKey(), "preference", nextPair.highId(), otherContinue);
        String otherId = eventId(otherPreference, "PREFERENCE_CORRECTION_SUBMITTED");
        SandboxLoop.LearningView pending = lab.loop().propose(nextPair.highId(), "partner.pace", "steady", otherId, other.ledgerKey(), "FEEDBACK");
        lab.loop().act(other.ledgerKey(), "revoke", nextPair.highId(), null);
        assertThatThrownBy(() -> lab.loop().decide(pending.proposal().proposalId(), "confirm", nextPair.highId()))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("RETRACTED_SOURCE");

        SandboxLoop.LearningView direct = lab.loop().propose(pair.lowId(), "partner.pace", "steady", "");
        SandboxLoop.LearningView once = lab.loop().decide(direct.proposal().proposalId(), "confirm", pair.lowId());
        SandboxLoop.LearningView twice = lab.loop().decide(direct.proposal().proposalId(), "confirm", pair.lowId());
        assertThat(twice.profileVersion()).isEqualTo(once.profileVersion());
    }

    @Test
    void historicalInputStaysPutAndFeedbackKeepsTheRunObjective() {
        LabWorkbench lab = new LabWorkbench();
        SandboxLoop.GlobalView g1 = lab.loop().solve("G1", List.of(), "test");
        SandboxLoop.PairView pair = g1.pairs().get(0);
        SandboxLoop.FeedbackView first = lab.loop().open(g1.runId(), pair.lowId(), pair.highId());
        assertThat(first.globalObjectiveVersion()).isEqualTo("global-objective-g1-1");
        assertThat(first.labRunId()).isEqualTo(g1.runId());
        assertThat(first.candidateStrategyVersion()).isEqualTo("recall-rank-1");
        SandboxLoop.GlobalView g2 = lab.loop().solve("G2", List.of(), "test");
        SandboxLoop.FeedbackView second = lab.loop().open(g2.runId(), g2.pairs().get(0).lowId(), g2.pairs().get(0).highId());
        assertThat(second.globalObjectiveVersion()).isEqualTo(Objective.SELECTED_VERSION);
        assertThat(g2.semanticResultHash()).isEqualTo(g2.resultHash());
        LabWorkbench.LabRun stored = lab.runs().stream().filter(run -> run.runId().equals(g2.runId())).findFirst().orElseThrow();
        assertThat(stored.inputSnapshot()).contains("self self.pace=slow");
        assertThat(stored.result()).contains("fit=");
        assertThat(stored.result()).doesNotContain(g2.runId());
        assertThat(SandboxLoop.digest(stored.result().replace("fit=", "fit=0"))).isNotEqualTo(stored.resultHash());
        SandboxLoop.GlobalView repeat = lab.loop().solve("G2", List.of(), "test");
        assertThat(repeat.semanticResultHash()).isEqualTo(g2.semanticResultHash());
        String frozen = stored.inputSnapshot();
        lab.update(71006L, "己·硬约束", selfOf(lab, 71006L), preferencesOf(lab, 71006L), List.of(), lab.profile(71006L).version());
        LabWorkbench.LabRun after = lab.runs().stream().filter(run -> run.runId().equals(g2.runId())).findFirst().orElseThrow();
        assertThat(after.inputSnapshot()).isEqualTo(frozen);
        assertThat(lab.subjects().stream().filter(user -> user.userId() == 71006L).findFirst().orElseThrow().candidate().ageMin()).isEqualTo(40);

        SandboxLoop.FeedbackView duplicate = lab.loop().act(first.ledgerKey(), "accept", pair.lowId(), null);
        SandboxLoop.FeedbackView same = lab.loop().act(first.ledgerKey(), "accept", pair.lowId(), null);
        assertThat(same.events()).hasSize(duplicate.events().size());

        WorkspaceBoard board = new WorkspaceBoard();
        WorkspaceBoard.Workspace workspace = board.resolve(9L, null);
        String runId = workspace.workbench().loop().solve("G2", List.of(), "test").runId();
        board.reset(9L, workspace.workspaceId());
        assertThatThrownBy(() -> board.resolve(9L, null).workbench().loop().open(runId, 71001L, 71002L))
                .isInstanceOf(LabWorkbench.LabRejection.class)
                .hasMessage("RUN");
    }

    private static String eventId(SandboxLoop.FeedbackView view, String type) {
        return view.events().stream().filter(event -> type.equals(event.type())).reduce((left, right) -> right).orElseThrow().eventId();
    }

    private static String value(LabWorkbench lab, long userId, String code) {
        return write(lab, userId, code).value();
    }

    private static FeatureWrite write(LabWorkbench lab, long userId, String code) {
        return lab.writes(userId).stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    private static List<FeatureWrite> selfOf(LabWorkbench lab, long userId) {
        return lab.writes(userId).stream().filter(item -> item.code().startsWith("self.")).toList();
    }

    private static List<FeatureWrite> preferencesOf(LabWorkbench lab, long userId) {
        return lab.writes(userId).stream().filter(item -> item.code().startsWith("partner.")).toList();
    }

    private static List<FeatureWrite> currentPreferences(LabWorkbench lab, long userId) {
        return preferencesOf(lab, userId);
    }
}
