package cn.aiditto.it;

import cn.aiditto.shadow.ShadowView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowMatchingIT extends ShadowCase {
    @Test
    void isolatedBusinessUsersRunWithoutPublishing() {
        Ready man = enroll("man", List.of("women"), true, true);
        Ready woman = enroll("woman", List.of("men"), true, true);
        int matches = table("\"match\"");
        int weeks = table("week");
        int copies = table("match_copy");
        ShadowView view = runner.run(request("shadow.business.one"));
        assertThat(view.status()).isEqualTo("COMPLETE");
        assertThat(view.executable()).isTrue();
        assertThat(view.safetyViolations()).isZero();
        assertThat(view.eligibleUsers()).isEqualTo(2);
        assertThat(view.recommendedEdges()).isZero();
        assertThat(view.matchedPairs()).isZero();
        assertThat(view.comparisonJson()).contains("frameA", "frameB").doesNotContain("工程师", man.phone, woman.email, "小明");
        assertThat(table("\"match\"")).isEqualTo(matches);
        assertThat(table("week")).isEqualTo(weeks);
        assertThat(table("match_copy")).isEqualTo(copies);
        assertThat(table("shadow_pair")).isZero();
    }

    @Test
    void oneWayIntentProducesNoLegalEdge() {
        jdbc.update("""
                INSERT INTO place (id, kind, city, name_zh, name_en, lat, lng)
                VALUES ('shadowlab', 'area', 'lab', '影子实验室', 'Shadow Lab', 31.230000, 121.470000)
                ON CONFLICT (id) DO NOTHING
                """);
        enroll("shadowlab", "man", List.of("men"), true, true);
        enroll("shadowlab", "woman", List.of("men"), true, true);
        ShadowView view = runner.run(request("shadow.intent.one", "lab"));
        assertThat(view.status()).isEqualTo("COMPLETE");
        assertThat(view.legalEdges()).isZero();
        assertThat(view.matchedPairs()).isZero();
        assertThat(view.safetyViolations()).isZero();
        assertThat(table("\"match\"")).isZero();
    }

    @Test
    void repeatedRunKeepsTheSameSnapshot() {
        enroll("man", List.of("women"), true, true);
        enroll("woman", List.of("men"), true, true);
        ShadowView first = runner.run(request("shadow.repeat.one"));
        ShadowView second = runner.run(request("shadow.repeat.one"));
        assertThat(second.inputHash()).isEqualTo(first.inputHash());
        assertThat(second.resultHash()).isEqualTo(first.resultHash());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shadow_run WHERE run_id = ?", Integer.class, first.runId())).isEqualTo(1);
    }

    @Test
    void deleteDuringTheRunDoesNotDeadlock() throws Exception {
        Ready man = enroll("man", List.of("women"), true, true);
        Ready woman = enroll("woman", List.of("men"), true, true);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> deletion = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                assertThat(call("DELETE", "/api/account", null, man.token).status()).isEqualTo(204);
                return null;
            });
            Future<ShadowView> shadow = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return runner.run(request("shadow.delete.race"));
            });
            deletion.get(20, TimeUnit.SECONDS);
            ShadowView view = shadow.get(20, TimeUnit.SECONDS);
            assertThat(view.status()).isIn("EXPIRED", "REJECTED", "COMPLETE", "ABORTED");
            assertThat(view.matchedPairs()).isZero();
            if (!"COMPLETE".equals(view.status())) assertThat(view.executable()).isFalse();
            assertThat(table("shadow_pair")).isZero();
            assertThat(table("\"match\"")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, man.userId)).isEqualTo("deleting");
            assertThat(jdbc.queryForObject("SELECT status FROM \"user\" WHERE id = ?", String.class, woman.userId)).isEqualTo("active");
        } finally {
            pool.shutdownNow();
        }
    }
}
