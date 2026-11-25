package cn.aiditto.it;

import java.util.Locale;
import java.util.Set;

/**
 * Refuses {@code flyway.clean()} unless the JDBC URL is an explicit disposable test database.
 * There is no default URL. A missing setting must not fall through to a database that might
 * hold business data.
 */
public final class TestDatabaseGuard {
    public static final String DATABASE = "aiditto_s01_test";
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1", "::1", "postgres");

    private TestDatabaseGuard() {}

    public static String requireUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "TEST_DB_URL is required. Refusing to choose a database that might hold business data.");
        }
        Parsed parsed = parse(url.trim());
        if (!HOSTS.contains(parsed.host())) {
            throw new IllegalStateException("TEST_DB_URL host is not an allowed test host");
        }
        if (!DATABASE.equals(parsed.database())) {
            throw new IllegalStateException("Refusing to clean a database named differently from " + DATABASE);
        }
        return url.trim();
    }

    static Parsed parse(String url) {
        String prefix = "jdbc:postgresql://";
        if (!url.startsWith(prefix)) {
            throw new IllegalStateException("TEST_DB_URL must be a PostgreSQL JDBC URL");
        }
        String rest = url.substring(prefix.length());
        if (rest.indexOf('@') >= 0) {
            throw new IllegalStateException("TEST_DB_URL must not embed credentials");
        }
        String host;
        String path;
        if (rest.startsWith("[")) {
            int end = rest.indexOf(']');
            if (end < 0) throw new IllegalStateException("TEST_DB_URL host is not an allowed test host");
            host = rest.substring(1, end);
            path = rest.substring(end + 1);
        } else {
            int slash = rest.indexOf('/');
            if (slash < 0) throw new IllegalStateException("TEST_DB_URL has no database name");
            String hostPort = rest.substring(0, slash);
            int colon = hostPort.indexOf(':');
            host = colon < 0 ? hostPort : hostPort.substring(0, colon);
            path = rest.substring(slash);
        }
        int slash = path.indexOf('/');
        if (slash < 0) throw new IllegalStateException("TEST_DB_URL has no database name");
        String database = path.substring(slash + 1);
        int query = database.indexOf('?');
        if (query >= 0) database = database.substring(0, query);
        if (database.isBlank() || database.indexOf('/') >= 0) {
            throw new IllegalStateException("Refusing to clean a database named differently from " + DATABASE);
        }
        return new Parsed(host.toLowerCase(Locale.ROOT), database);
    }

    record Parsed(String host, String database) {}
}
