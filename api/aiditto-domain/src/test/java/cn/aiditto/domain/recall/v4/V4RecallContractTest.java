package cn.aiditto.domain.recall.v4;

import cn.aiditto.domain.relationship.SemanticSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V4RecallContractTest {
    @Test
    void unconfirmedInferenceAndWithheldValuesStayOutOfRecall() {
        RecallFeatureAdmission.View view = RecallFeatureAdmission.admit(List.of(
                new RecallFeatureAdmission.Claim("self.pace", "slow", SemanticSupport.USER_CONFIRMED, false, false),
                new RecallFeatureAdmission.Claim("self.communication", "quiet", SemanticSupport.SEMANTIC_SUPPORT_PENDING, false, false),
                new RecallFeatureAdmission.Claim("self.schedule", "early", SemanticSupport.USER_CONFIRMED, true, false),
                new RecallFeatureAdmission.Claim("partner.pace", "UNKNOWN", SemanticSupport.USER_CONFIRMED, false, false),
                new RecallFeatureAdmission.Claim("partner.support", "close", SemanticSupport.USER_CONFIRMED, false, true),
                new RecallFeatureAdmission.Claim("occupation.text", "老师", SemanticSupport.USER_CONFIRMED, false, false)),
                Set.of("reading"), SemanticSupport.REFERENCE_VALID);
        assertThat(view.enums()).containsOnlyKeys("self.pace");
        assertThat(view.hobbies()).isEmpty();
        assertThat(view.dropped()).isGreaterThan(0);
    }

    @Test
    void unknownDoesNotCountAsTheSameSelfValue() {
        RecallBench bench = RecallBench.load();
        RecallSubject sparse = bench.subjects().stream().filter(subject -> subject.userId() == 42).findFirst().orElseThrow();
        RecallSubject quiet = bench.subjects().stream().filter(subject -> subject.userId() == 9).findFirst().orElseThrow();
        assertThat(V4RecallEngine.selfOverlap(sparse, quiet)).isZero();
    }

    @Test
    void forwardAndReverseAreNotTheSameScore() {
        RecallBench bench = RecallBench.load();
        RecallSubject seeker = find(bench, 15);
        RecallSubject target = find(bench, 19);
        assertThat(V4RecallEngine.preferenceHits(seeker.preferenceEnums(), target.selfEnums())).isEqualTo(1);
        assertThat(V4RecallEngine.preferenceHits(target.preferenceEnums(), seeker.selfEnums())).isZero();
        assertThat(V4RecallEngine.preferenceHits(find(bench, 27).preferenceEnums(), find(bench, 23).selfEnums())).isEqualTo(1);
    }

    @Test
    void productionCollectionIsRefused() {
        assertThatThrownBy(() -> V4IndexNames.refuseUnlessIsolated("match_user_v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> V4IndexNames.refuseUnlessIsolated("v4_private"))
                .isInstanceOf(IllegalArgumentException.class);
        V4IndexNames.refuseUnlessIsolated("v4_it_demo");
        V4IndexNames.refuseUnlessIsolated("v4_synth_bench");
    }

    @Test
    void vectorVersionsAreIdempotentAndStaleWritesLose() throws Exception {
        VersionedVectorIndex index = new VersionedVectorIndex();
        float[] vector = {1f, 0f};
        VersionedVectorIndex.Snapshot newer = snapshot(7, 1, 2, "aa", "cc", vector);
        VersionedVectorIndex.Snapshot older = snapshot(7, 1, 1, "bb", "dd", vector);
        assertThat(index.commit(newer)).isEqualTo(VersionedVectorIndex.Put.STORED);
        assertThat(index.commit(newer)).isEqualTo(VersionedVectorIndex.Put.IDEMPOTENT);
        assertThat(index.commit(older)).isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<VersionedVectorIndex.Put> left = new AtomicReference<>();
        AtomicReference<VersionedVectorIndex.Put> right = new AtomicReference<>();
        Thread first = new Thread(() -> {
            await(start);
            left.set(index.commit(older));
        });
        Thread second = new Thread(() -> {
            await(start);
            right.set(index.commit(newer));
        });
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();
        assertThat(List.of(left.get(), right.get())).contains(VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, vector, 5, "hash-test").get(0).profileVersion()).isEqualTo(2);
        assertThat(index.search(VersionedVectorIndex.Kind.PREFERENCE, vector, 5, "hash-test").get(0).profileVersion()).isEqualTo(2);
        index.delete(7);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, vector, 5, "hash-test")).isEmpty();
        assertThat(index.commit(newer)).isEqualTo(VersionedVectorIndex.Put.REJECT_WITHDRAWN);
    }

    private static VersionedVectorIndex.Snapshot snapshot(long userId, long generation, long version, String self, String preference, float[] vector) {
        return new VersionedVectorIndex.Snapshot(userId, generation, version, "hash-test", self, vector, preference, vector);
    }

    @Test
    void selfAndPreferenceTextsStayApart() {
        RecallSubject subject = RecallBench.load().subjects().get(0);
        String self = RecallVectorText.self(subject);
        String preference = RecallVectorText.preference(subject);
        assertThat(self).contains("hobbies=");
        assertThat(preference).doesNotContain("hobbies=");
        assertThat(self).doesNotContain("occupation");
    }

    private static RecallSubject find(RecallBench bench, long id) {
        return bench.subjects().stream().filter(subject -> subject.userId() == id).findFirst().orElseThrow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
