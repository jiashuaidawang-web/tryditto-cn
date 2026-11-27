package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.consent.ConsentPolicy;
import cn.aiditto.domain.consent.ConsentStatus;
import cn.aiditto.domain.consent.MatchingConsent;
import cn.aiditto.domain.matching.Candidate;
import cn.aiditto.domain.matching.PoolGate;
import cn.aiditto.domain.relationship.RelationshipTypes.EvidenceSource;
import cn.aiditto.domain.relationship.SemanticSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecallAuditTest {
    private static final float[] VECTOR = {1f, 0f};

    @Test
    void withdrawnGenerationRejectsADelayedWriteAndAnOldWithdrawCannotDeleteTheNewOne() throws Exception {
        VersionedVectorIndex index = new VersionedVectorIndex();
        VersionedVectorIndex.Snapshot second = snapshot(9, 2, 2, "self-2", "pref-2");
        assertThat(index.commit(second)).isEqualTo(VersionedVectorIndex.Put.STORED);
        assertThat(index.withdraw(9, 2, 2)).isEqualTo(VersionedVectorIndex.Put.DELETED);
        assertThat(index.commit(snapshot(9, 2, 1, "self-1", "pref-1"))).isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.commit(second)).isEqualTo(VersionedVectorIndex.Put.REJECT_WITHDRAWN);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, VECTOR, 5, "hash-test")).isEmpty();
        assertThat(index.authorize(9, 3)).isEqualTo(VersionedVectorIndex.Put.STORED);
        VersionedVectorIndex.Snapshot third = snapshot(9, 3, 3, "self-3", "pref-3");
        assertThat(index.commit(third)).isEqualTo(VersionedVectorIndex.Put.STORED);
        assertThat(index.withdraw(9, 2, 2)).isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.commit(snapshot(9, 3, 2, "old-self", "old-pref"))).isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, VECTOR, 5, "hash-test").get(0).profileVersion()).isEqualTo(3);
        assertThat(index.search(VersionedVectorIndex.Kind.PREFERENCE, VECTOR, 5, "hash-test").get(0).profileVersion()).isEqualTo(3);
        float[] exposed = {1f, 0f};
        VersionedVectorIndex.Point point = new VersionedVectorIndex.Point(9, VersionedVectorIndex.Kind.SELF, 3, "hash-test", "self-3", exposed);
        exposed[0] = 9f;
        point.vector()[0] = 4f;
        assertThat(point.vector()[0]).isEqualTo(1f);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<VersionedVectorIndex.Put> write = new AtomicReference<>();
        AtomicReference<VersionedVectorIndex.Put> drop = new AtomicReference<>();
        Thread publishing = new Thread(() -> {
            await(start);
            write.set(index.commit(third));
        });
        Thread withdrawing = new Thread(() -> {
            await(start);
            drop.set(index.withdraw(9, 3, 3));
        });
        publishing.start();
        withdrawing.start();
        start.countDown();
        publishing.join();
        withdrawing.join();
        assertThat(index.size()).isIn(0, 2);
        if (index.size() == 2) {
            assertThat(index.withdraw(9, 3, 3)).isEqualTo(VersionedVectorIndex.Put.DELETED);
        }
        assertThat(index.commit(third)).isIn(VersionedVectorIndex.Put.REJECT_WITHDRAWN, VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, VECTOR, 5, "hash-test")).isEmpty();
        assertThat(index.search(VersionedVectorIndex.Kind.PREFERENCE, VECTOR, 5, "hash-test")).isEmpty();
    }

    @Test
    void aPartialModelOrADifferentModelCannotPublishOneSide() {
        VersionedVectorIndex index = new VersionedVectorIndex();
        VersionedVectorIndex.Snapshot first = snapshot(4, 1, 2, "self", "pref");
        assertThat(index.commit(first)).isEqualTo(VersionedVectorIndex.Put.STORED);
        VersionedVectorIndex.Snapshot otherModel = new VersionedVectorIndex.Snapshot(
                4, 1, 2, "other-model", "self", VECTOR, "pref", VECTOR);
        assertThat(index.commit(otherModel)).isEqualTo(VersionedVectorIndex.Put.REJECT_CONFLICT);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, VECTOR, 5, "other-model")).isEmpty();
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, VECTOR, 5, "hash-test")).hasSize(1);
        assertThat(index.size()).isEqualTo(2);
    }

    @Test
    void contradictoryAndUngatedClaimsDoNotEnterTheRecallView() {
        RecallProfileContract.Admitted admitted = RecallProfileContract.admit(List.of(
                new RecallProfileContract.Claim("self.pace", "slow", SemanticSupport.USER_CONFIRMED, false, false, EvidenceSource.SELF_REPORTED),
                new RecallProfileContract.Claim("self.pace", "fast", SemanticSupport.USER_CONFIRMED, false, false, EvidenceSource.SELF_REPORTED),
                new RecallProfileContract.Claim("self.communication", "quiet", SemanticSupport.SEMANTIC_SUPPORT_PENDING, false, false, EvidenceSource.MODEL_INFERENCE),
                new RecallProfileContract.Claim("self.schedule", "early", SemanticSupport.USER_CONFIRMED, true, false, EvidenceSource.SELF_REPORTED),
                new RecallProfileContract.Claim("partner.pace", "UNKNOWN", SemanticSupport.USER_CONFIRMED, false, false, EvidenceSource.DIRECT_ANSWER),
                new RecallProfileContract.Claim("made.up", "slow", SemanticSupport.USER_CONFIRMED, false, false, EvidenceSource.SELF_REPORTED)),
                Set.of("reading"), SemanticSupport.USER_CONFIRMED);
        assertThat(admitted.selfEnums()).isEmpty();
        assertThat(admitted.preferenceEnums()).isEmpty();
        assertThat(admitted.hobbies()).containsExactly("reading");
        assertThat(admitted.reasons()).anyMatch(reason -> reason.contains("CONTRADICTION"));
        assertThat(admitted.reasons()).anyMatch(reason -> reason.contains("WITHHELD"));
        assertThat(admitted.reasons()).anyMatch(reason -> reason.contains("INFERENCE") || reason.contains("SUPPORT"));
        RecallSubject subject = RecallBench.load().subjects().get(0);
        assertThat(RecallVectorText.self(subject)).doesNotContain("WITHHELD");
        assertThatThrownBy(() -> new RecallSubject(1, "dev", 2, true, false, subject.fixtureSource(),
                subject.candidate(), subject.applicant(), new MatchingConsent(2, ConsentStatus.GRANTED, 1,
                RecallBench.NOW, null, ConsentPolicy.POLICY_VERSION, RecallBench.NOW),
                Map.of(), Set.of(), Map.of(), 1, RecallIngress.SYNTHETIC_FIXTURE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingResultsStayInTheDenominatorAndLongIdsDoNotCollapse() {
        RecallBench bench = RecallBench.load();
        RecallSubject seeker = bench.subjects().get(0);
        RecallMetrics.Summary missing = RecallMetrics.summarize(List.of(seeker), Map.of(),
                List.of(new RecallMetrics.Label(seeker.userId(), 3, "dev", "RELEVANT", "SIMILAR", 2)),
                bench.subjects(), bench.input());
        assertThat(missing.eligiblePositives()).isEqualTo(1);
        assertThat(missing.recallAt20()).isZero();
        assertThat(missing.missingResultPositives()).isEqualTo(1);
        long wide = 4294967297L;
        RecallSubject big = person(wide);
        RecallSubject small = person(1);
        RecallSubject other = person(8);
        V4RecallEngine.Hit toOther = hit(wide, 8);
        V4RecallEngine.Hit fromSmall = hit(1, 8);
        V4RecallEngine.Hit back = hit(8, wide);
        Map<Long, V4RecallEngine.Result> results = Map.of(
                wide, result(toOther),
                1L, result(fromSmall),
                8L, result(back));
        RecallMetrics.Summary pairs = RecallMetrics.summarize(List.of(big, small, other), results, List.of(),
                List.of(big, small, other), null);
        assertThat(pairs.poolDirectedEdges()).isEqualTo(3);
        assertThat(pairs.poolMutualEdges()).isEqualTo(2);
    }

    @Test
    void challengeLabelsAreNotACliqueAndTheFrozenVerdictIsApplied() {
        RecallChallenge.Dataset data = RecallChallenge.load();
        assertThat(data.subjects()).hasSize(220);
        assertThat(data.labels().stream().filter(label -> "RELEVANT".equals(label.kind())).count()).isGreaterThanOrEqualTo(100);
        assertThat(kind(data, 1, 9)).isEqualTo("IRRELEVANT");
        assertThat(kind(data, 1, 30)).isEqualTo("RELEVANT");
        assertThat(kind(data, 1, 29)).isEqualTo("HARD_VETO");
        assertThat(kind(data, 37, 41)).isEqualTo("RELEVANT");
        assertThat(kind(data, 41, 37)).isEqualTo("RELEVANT");
        assertThat(kind(data, 85, 89)).isEqualTo("RELEVANT");
        assertThat(kind(data, 89, 85)).isEqualTo("IRRELEVANT");
        assertThat(kind(data, 122, 101)).isEqualTo("UNKNOWN");
        Map<String, RecallMetrics.Summary> dev = RecallControls.compare(
                data.subjects(), data.split("dev"), data.input(), data.labels("dev"));
        String verdict = RecallChallenge.verdict(dev.get("B0-random"), dev.get("B1-scorer"), dev.get("B3-multi"));
        assertThat(verdict).isEqualTo(RecallChallenge.verdict(dev.get("B0-random"), dev.get("B1-scorer"), dev.get("B3-multi")));
        assertThat(dev.get("B3-multi").safetyAuditViolations()).isZero();
        assertThat(dev.get("B3-multi").expiredReturned()).isZero();
        assertThat(dev.get("B3-multi").withdrawnReturned()).isZero();
        assertThat(dev.get("B0-random").eligiblePositives()).isEqualTo(dev.get("B3-multi").eligiblePositives());
        assertThat(dev.get("B1-scorer").eligiblePositives()).isEqualTo(dev.get("B2-structured").eligiblePositives());
        RecallBench bench = RecallBench.load();
        Map<String, RecallMetrics.Summary> original = RecallControls.compare(
                bench.subjects(), bench.split("dev"), bench.input(), bench.labels("dev"));
        assertThat(original.get("B0-random").eligiblePositives()).isEqualTo(original.get("B3-multi").eligiblePositives());
        assertThat(original.get("B3-multi").safetyAuditViolations()).isZero();
        System.out.println("ORIGINAL b0=" + original.get("B0-random").recallHitsAt20()
                + " b1=" + original.get("B1-scorer").recallHitsAt20()
                + " b2=" + original.get("B2-structured").recallHitsAt20()
                + " b3=" + original.get("B3-multi").recallHitsAt20()
                + " denom=" + original.get("B3-multi").eligiblePositives());
        RecallControls.Ties ties = RecallControls.scorerTies(
                data.subjects().stream().filter(subject -> subject.userId() == 101).findFirst().orElseThrow(),
                data.subjects(), data.input());
        assertThat(ties.equalPairs()).isPositive();
        assertThat(ties.smallerIdFirst()).isEqualTo(ties.equalPairs());
        RecallMetrics.Summary multi = dev.get("B3-multi");
        System.out.println("CHALLENGE verdict=" + verdict
                + " positives=" + multi.eligiblePositives()
                + " b0=" + dev.get("B0-random").recallHitsAt10() + "/" + dev.get("B0-random").recallHitsAt20() + "/" + dev.get("B0-random").recallHitsAt50()
                + " b1=" + dev.get("B1-scorer").recallHitsAt10() + "/" + dev.get("B1-scorer").recallHitsAt20() + "/" + dev.get("B1-scorer").recallHitsAt50()
                + " b2=" + dev.get("B2-structured").recallHitsAt10() + "/" + dev.get("B2-structured").recallHitsAt20() + "/" + dev.get("B2-structured").recallHitsAt50()
                + " b3=" + multi.recallHitsAt10() + "/" + multi.recallHitsAt20() + "/" + multi.recallHitsAt50()
                + " denom=" + multi.eligiblePositives()
                + " labeledPrecision=" + multi.labeledRelevantAt20() + "/" + (multi.labeledRelevantAt20() + multi.labeledIrrelevantAt20())
                + " slotPrecision=" + multi.labeledRelevantAt20() + "/" + multi.inspectedSlotsAt20()
                + " unlabeled=" + multi.unlabeledAt20()
                + " coverageLabels=" + multi.labelCoverage()
                + " safety=" + multi.safetyAuditViolations()
                + " hard=" + multi.hardViolations()
                + " expired=" + multi.expiredReturned()
                + " withdrawn=" + multi.withdrawnReturned()
                + " mrrSeekers=" + multi.mrrSeekers()
                + " hitSeekers=" + multi.hitRateAt20()
                + " ndcg=" + multi.ndcgAt20()
                + " mrr=" + multi.mrr()
                + " queryBoth=" + multi.queryMutualEdges() + "/" + multi.queryDirectedEdges()
                + " poolBoth=" + multi.poolMutualEdges() + "/" + multi.poolDirectedEdges()
                + " ties=" + ties.equalPairs() + "/" + ties.smallerIdFirst()
                + " zeros=" + multi.zeroIneligible() + "/" + multi.zeroNoLegal()
                + "/" + multi.zeroSparse() + "/" + multi.zeroRecallFailure()
                + " routes=" + multi.routeFound());
        Map<String, RecallMetrics.Summary> holdout = RecallControls.compare(
                data.subjects(), data.split("holdout"), data.input(), data.labels("holdout"));
        RecallMetrics.Summary held = holdout.get("B3-multi");
        System.out.println("CHALLENGE-HOLDOUT positives=" + held.eligiblePositives()
                + " b0=" + holdout.get("B0-random").recallHitsAt20()
                + " b1=" + holdout.get("B1-scorer").recallHitsAt20()
                + " b2=" + holdout.get("B2-structured").recallHitsAt20()
                + " b3=" + held.recallHitsAt10() + "/" + held.recallHitsAt20() + "/" + held.recallHitsAt50()
                + " safety=" + held.safetyAuditViolations()
                + " labeled=" + held.labeledRelevantAt20() + "/" + (held.labeledRelevantAt20() + held.labeledIrrelevantAt20())
                + " slots=" + held.labeledRelevantAt20() + "/" + held.inspectedSlotsAt20()
                + " unlabeled=" + held.unlabeledAt20());
    }

    private static String kind(RecallChallenge.Dataset data, long seeker, long candidate) {
        return data.labels().stream()
                .filter(label -> label.seeker() == seeker && label.candidate() == candidate)
                .map(RecallMetrics.Label::kind)
                .findFirst()
                .orElse("");
    }

    private static VersionedVectorIndex.Snapshot snapshot(long userId, long generation, long version, String self, String preference) {
        return new VersionedVectorIndex.Snapshot(userId, generation, version, "hash-test", self, VECTOR, preference, VECTOR);
    }

    private static V4RecallEngine.Hit hit(long seeker, long candidate) {
        return new V4RecallEngine.Hit(seeker, candidate, "R1", 1, 1, 1, 1, 1, "PASS", "R1", List.of());
    }

    private static V4RecallEngine.Result result(V4RecallEngine.Hit hit) {
        return new V4RecallEngine.Result(List.of(hit), List.of(), 1, 0, 0, 0, V4RecallEngine.Gap.NONE);
    }

    private static RecallSubject person(long id) {
        Candidate candidate = Candidate.builder(id).build();
        PoolGate.Applicant applicant = new PoolGate.Applicant(candidate.birthday(), true, true, false, 2, false, false, null, false);
        MatchingConsent consent = new MatchingConsent(id, ConsentStatus.GRANTED, 1, RecallBench.NOW, null,
                ConsentPolicy.POLICY_VERSION, Instant.parse("2026-11-27T06:00:00Z"));
        return new RecallSubject(id, "dev", 2, true, false, RecallBench.SOURCE, candidate, applicant, consent,
                Map.of(), Set.of(), Map.of(), 1, RecallIngress.SYNTHETIC_FIXTURE);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
