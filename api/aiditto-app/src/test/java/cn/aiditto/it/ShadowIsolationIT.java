package cn.aiditto.it;

import cn.aiditto.shadow.ShadowView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowIsolationIT extends ShadowCase {
    @Test
    void overlapIsRejectedAndMatchTablesStayUntouched() throws Exception {
        enroll("man", List.of("women"), true, true);
        enroll("woman", List.of("men"), true, true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runner.overlapForTest(started, release);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ShadowView> first = pool.submit(() -> runner.run(request("shadow.overlap.one")));
            assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
            Future<ShadowView> second = pool.submit(() -> runner.run(request("shadow.overlap.two")));
            ShadowView busy = second.get(15, TimeUnit.SECONDS);
            release.countDown();
            ShadowView done = first.get(20, TimeUnit.SECONDS);
            assertThat(busy.status()).isEqualTo("CONCURRENCY");
            assertThat(busy.executable()).isFalse();
            assertThat(done.status()).isEqualTo("COMPLETE");
            assertThat(table("\"match\"")).isZero();
            assertThat(table("week")).isZero();
            assertThat(table("match_copy")).isZero();
            shadowRuns.deleteRun(done.runId());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shadow_run WHERE run_id = ?", Integer.class, done.runId())).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
