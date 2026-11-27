package cn.aiditto.recall;

import cn.aiditto.domain.recall.v4.RecallBench;
import cn.aiditto.domain.recall.v4.RecallSubject;
import cn.aiditto.domain.recall.v4.RecallVectorText;
import cn.aiditto.domain.recall.v4.V4IndexNames;
import cn.aiditto.domain.recall.v4.VersionedVectorIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V4VectorSyncTest {
    private static final String POPULATION_SHA = "90f8fdc483e7f6ff0380ab7052c38caae8de82abf17038bd65e18c9662156cc5";

    @Test
    void syncIsIdempotentAndAStaleVersionCannotOverwrite() {
        VersionedVectorIndex index = new VersionedVectorIndex();
        EmbeddingService embedding = new HashingEmbeddingService(32);
        RecallSubject current = RecallBench.load().subjects().stream().filter(subject -> subject.userId() == 1).findFirst().orElseThrow();
        assertThat(V4VectorSync.sync(index, current, embedding)).isEqualTo(VersionedVectorIndex.Put.STORED);
        assertThat(V4VectorSync.sync(index, current, embedding)).isEqualTo(VersionedVectorIndex.Put.IDEMPOTENT);
        RecallSubject older = new RecallSubject(current.userId(), current.split(), 1, true, false, current.fixtureSource(),
                current.candidate(), current.applicant(), current.consent(), current.selfEnums(), current.hobbies(),
                current.preferenceEnums());
        assertThat(V4VectorSync.sync(index, older, embedding)).isEqualTo(VersionedVectorIndex.Put.REJECT_STALE);
        assertThat(index.search(VersionedVectorIndex.Kind.SELF, embedding.embed(RecallVectorText.self(current)), 3, embedding.version()))
                .isNotEmpty();
    }

    @Test
    void withdrawnSubjectLeavesTheIndex() {
        VersionedVectorIndex index = new VersionedVectorIndex();
        EmbeddingService embedding = new HashingEmbeddingService(32);
        RecallSubject current = RecallBench.load().subjects().stream().filter(subject -> subject.userId() == 3).findFirst().orElseThrow();
        V4VectorSync.sync(index, current, embedding);
        RecallSubject withdrawn = new RecallSubject(current.userId(), current.split(), current.profileVersion(), true, true,
                current.fixtureSource(), current.candidate(), current.applicant(), current.consent(),
                current.selfEnums(), current.hobbies(), current.preferenceEnums());
        assertThat(V4VectorSync.sync(index, withdrawn, embedding)).isEqualTo(VersionedVectorIndex.Put.DELETED);
        assertThat(index.size()).isZero();
    }

    @Test
    void hashingSyncIsAnEngineeringStandIn() {
        EmbeddingService embedding = new HashingEmbeddingService(32);
        assertThat(embedding.version()).startsWith("hash-");
        assertThat(embedding.version()).doesNotContain("semantic");
    }

    @Test
    void productionCollectionIsRefused() {
        assertThatThrownBy(() -> V4IndexNames.refuseUnlessIsolated("match_user_v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syntheticPopulationBaselineStaysTwoHundred() throws Exception {
        Path path = Path.of("src/main/resources/synthetic/users-v1.json");
        byte[] bytes = Files.readAllBytes(path);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(hash).isEqualTo(POPULATION_SHA);
        assertThat(new String(bytes)).contains("\"users\"");
    }
}
