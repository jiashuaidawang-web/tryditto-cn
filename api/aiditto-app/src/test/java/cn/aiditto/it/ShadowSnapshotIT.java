package cn.aiditto.it;

import cn.aiditto.shadow.ShadowView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowSnapshotIT extends ShadowCase {
    @Test
    void changedInputDoesNotReplaceTheStoredSnapshot() {
        Ready man = enroll("man", List.of("women"), true, true);
        enroll("woman", List.of("men"), true, true);
        ShadowView first = runner.run(request("shadow.snapshot.one"));
        answer(man.token, "hobbies", List.of("music"));
        int source = jdbc.queryForObject("SELECT version FROM profile WHERE user_id = ?", Integer.class, man.userId);
        profiles.save(man.userId, source, profiles.storedGeneration(man.userId), profiles.projectAuthoritative(man.userId));
        ShadowView second = runner.run(request("shadow.snapshot.one"));
        assertThat(second.failureReason()).isEqualTo("INPUT_CHANGED");
        assertThat(second.executable()).isFalse();
        String stored = jdbc.queryForObject("SELECT input_hash FROM shadow_run WHERE run_id = ?", String.class, first.runId());
        assertThat(stored).isEqualTo(first.inputHash());
        assertThat(table("shadow_run")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT input_document::text FROM shadow_run WHERE run_id = ?", String.class, first.runId()))
                .doesNotContain(man.phone, "工程师", "小明");
    }

    @Test
    void failedRunKeepsNoExecutablePair() {
        Ready man = enroll("man", List.of("women"), true, true);
        enroll("woman", List.of("men"), true, true);
        runner.beforeCommitForTest(() -> assertThat(call("DELETE", "/api/me/matching-consent", null, man.token).status()).isEqualTo(200));
        ShadowView view = runner.run(request("shadow.expire.one"));
        assertThat(view.status()).isEqualTo("EXPIRED");
        assertThat(view.executable()).isFalse();
        assertThat(view.matchedPairs()).isZero();
        assertThat(table("shadow_pair")).isZero();
        assertThat(jdbc.queryForObject("SELECT executable FROM shadow_run WHERE run_id = ?", Boolean.class, view.runId())).isFalse();
    }
}
