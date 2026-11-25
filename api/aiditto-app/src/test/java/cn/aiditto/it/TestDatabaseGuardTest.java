package cn.aiditto.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Clean is allowed only for the disposable CI database on a local or runner host. */
class TestDatabaseGuardTest {

    @Test
    void missingUrlIsRefused() {
        assertThatThrownBy(() -> TestDatabaseGuard.requireUrl(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_URL is required");
        assertThatThrownBy(() -> TestDatabaseGuard.requireUrl("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_DB_URL is required");
    }

    @Test
    void oldDefaultsAndBusinessDatabasesAreRefused() {
        assertRefused("jdbc:postgresql://127.0.0.1:5432/aiditto_test?stringtype=unspecified");
        assertRefused("jdbc:postgresql://127.0.0.1:5432/aiditto");
        assertRefused("jdbc:postgresql://127.0.0.1:5432/aiditto_pg");
        assertRefused("jdbc:postgresql://127.0.0.1:5432/aiditto_s01_test_backup");
        assertRefused("jdbc:postgresql://db.internal:5432/aiditto_s01_test");
        assertRefused("jdbc:postgresql://aiditto_ci:secret@127.0.0.1:5432/aiditto_s01_test");
        assertRefused("jdbc:mysql://127.0.0.1:3306/aiditto_s01_test");
    }

    @Test
    void disposableDatabaseOnAnAllowedHostIsAccepted() {
        String local = "jdbc:postgresql://127.0.0.1:5432/aiditto_s01_test?stringtype=unspecified";
        assertThat(TestDatabaseGuard.requireUrl(local)).isEqualTo(local);
        assertThat(TestDatabaseGuard.requireUrl("jdbc:postgresql://localhost/aiditto_s01_test"))
                .contains("aiditto_s01_test");
        assertThat(TestDatabaseGuard.requireUrl("jdbc:postgresql://[::1]:5432/aiditto_s01_test"))
                .contains("aiditto_s01_test");
        assertThat(TestDatabaseGuard.requireUrl("jdbc:postgresql://postgres:5432/aiditto_s01_test"))
                .contains("aiditto_s01_test");
    }

    private static void assertRefused(String url) {
        assertThatThrownBy(() -> TestDatabaseGuard.requireUrl(url))
                .as(url)
                .isInstanceOf(IllegalStateException.class);
    }
}
