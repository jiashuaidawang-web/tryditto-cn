package cn.aiditto.it;

import cn.aiditto.domain.matching.v4.PoolExclusion;
import cn.aiditto.shadow.ProductMatchingPoolReader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMatchingPoolIT extends ShadowCase {
    @Test
    void grantedUserEntersAndMissingConsentStaysOut() {
        Ready granted = enroll("man", List.of("women"), true, true);
        Ready missing = enroll("woman", List.of("men"), true, false);
        ProductMatchingPoolReader.PoolRead read = pools.read("bj", PERIOD, CUTOFF, "real");
        assertThat(read.eligible()).extracting(row -> row.member().userId()).contains(granted.userId).doesNotContain(missing.userId);
        assertThat(read.excluded()).anyMatch(row -> row.userId() == missing.userId
                && row.reason() == PoolExclusion.MATCH_CONSENT_MISSING);
        assertThat(read.eligible()).allMatch(row -> row.snapshot().inputHash().equals(row.version().inputHash()));
    }

    @Test
    void revokedPausedAndClosedProfilesStayOut() {
        Ready revoked = enroll("man", List.of("women"), true, true);
        assertThat(call("DELETE", "/api/me/matching-consent", null, revoked.token).status()).isEqualTo(200);
        Ready paused = enroll("woman", List.of("men"), true, true);
        jdbc.update("UPDATE \"user\" SET status = 'paused' WHERE id = ?", paused.userId);
        Ready ungranted = enroll("woman", List.of("men"), false, true);
        Ready stale = enroll("man", List.of("women"), true, true);
        answer(stale.token, "matchingStyle", "faster");
        jdbc.update("UPDATE profile SET version = version + 1 WHERE user_id = ?", stale.userId);
        ProductMatchingPoolReader.PoolRead read = pools.read("bj", PERIOD, CUTOFF, "real");
        assertThat(reason(read, revoked.userId)).isEqualTo(PoolExclusion.MATCH_CONSENT_REVOKED);
        assertThat(reason(read, paused.userId)).isEqualTo(PoolExclusion.ACCOUNT_INACTIVE);
        assertThat(reason(read, ungranted.userId)).isEqualTo(PoolExclusion.PROFILE_PROCESSING_NOT_GRANTED);
        assertThat(reason(read, stale.userId)).isEqualTo(PoolExclusion.STALE_PROFILE);
        assertThat(read.eligible()).extracting(row -> row.member().userId())
                .doesNotContain(revoked.userId, paused.userId, ungranted.userId, stale.userId);
    }

    private static PoolExclusion reason(ProductMatchingPoolReader.PoolRead read, long userId) {
        return read.excluded().stream().filter(row -> row.userId() == userId).findFirst().orElseThrow().reason();
    }
}
